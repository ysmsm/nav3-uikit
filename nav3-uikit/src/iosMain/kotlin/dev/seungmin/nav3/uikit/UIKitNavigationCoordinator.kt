package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIAction
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.darwin.NSObject

/**
 * Keeps a [UINavigationController] (plus a chain of presented sheets and overlays) in sync with
 * the decorated Navigation 3 entries.
 *
 * Direction 1, back stack to UIKit: [setEntries] diffs the entries against the hosted controllers
 * by `contentKey` and position, keeps the common prefix, and replaces the rest.
 *
 * Direction 2, UIKit to back stack: when UIKit removes controllers on its own (swipe-back, the
 * back button, dragging a sheet down), the hosts are dropped and [onBack] is called once per
 * removed entry. The resulting back stack change then diffs to a no-op. The app may also leave its
 * back stack as it is; [onNativeChange] then makes the display apply its entries again, which puts
 * the removed screens back.
 *
 * When the top entry's content has an enabled back handler, UIKit is not allowed to pop it; the
 * back action goes to that handler instead, as it would under `NavDisplay`.
 */
internal class UIKitNavigationCoordinator<T : Any>(
    private val parentLocals: ParentLocals,
    configure: ComposeUIViewControllerConfiguration.() -> Unit,
    private val onBack: () -> Unit,
    private val onNativeChange: () -> Unit,
    /** Called after the top pushed entry or what it asks of the bars may have changed. */
    private val onTopChromeChanged: () -> Unit = {},
) {
    val navigationController: UINavigationController =
        ContainerNavigationController(onAppear = weakly(this) { it.onContainerAppeared() })

    private val environment = HostEnvironment(
        parentLocals = parentLocals,
        configure = configure,
        onEntryChromeChanged = weakly(this) { it.updateTopChrome() },
    )

    private val stack = mutableListOf<EntryHost<T>>()
    private val modals = mutableListOf<EntryHost<T>>()

    /** Hosts being animated out; their content stays composed until the transition ends. */
    private val retiring = mutableListOf<EntryHost<T>>()

    /**
     * Hosts UIKit removed on its own. They stay composed until the next [setEntries], so that when
     * the app keeps their entries, the same hosts go back instead of new ones composing the same
     * entries while the old content is still in composition.
     */
    private val detached = mutableListOf<EntryHost<T>>()

    private var latestEntries: List<NavEntry<T>> = emptyList()
    private var latestOverlays: Map<Any, @Composable () -> Unit> = emptyMap()
    private var modalTransitionInFlight = false

    /** A modal is waiting for the container to be in a window, which it needs to be presented. */
    private var waitingForWindow = false
    private var hasApplied = false
    private var disposed = false

    /** Whether the top pushed entry hides the tab bar with [NativeTabBarHidden]. */
    val topRequestsTabBarHidden: Boolean
        get() = stack.lastOrNull()?.requestsTabBarHidden == true

    /** Whether UIKit hides the tab bar for the top pushed entry, from its metadata. */
    val topHidesTabBarWhenPushed: Boolean
        get() = stack.size > 1 && stack.last().hidesTabBarWhenPushed

    // The full-width content swipe exists from iOS 26.
    @OptIn(ExperimentalForeignApi::class)
    private val hasContentPopGesture =
        navigationController.respondsToSelector(NSSelectorFromString("interactiveContentPopGestureRecognizer"))

    // UIKit holds delegates weakly, so the coordinator keeps them alive.
    private val navigationDelegate = NavigationDelegate(this)

    init {
        navigationController.delegate = navigationDelegate
        navigationController.navigationBar.prefersLargeTitles = true
        navigationController.setNavigationBarHidden(true, animated = false)
    }

    /**
     * Shows [entries]. [overlays] holds, by content key, the content of each entry that the scene
     * strategies show in an overlay scene. With [animate] false, the change is applied without a
     * transition even while the container is on screen.
     */
    fun setEntries(
        entries: List<NavEntry<T>>,
        overlays: Map<Any, @Composable () -> Unit>,
        animate: Boolean = true,
    ) {
        if (disposed) return
        latestEntries = entries
        latestOverlays = overlays
        if (modalTransitionInFlight) return // re-applied when the transition completes

        val firstModal = entries.indexOfFirst { it.isModal() }.let { if (it == -1) entries.size else it }
        // The root entry is always pushed; there is nothing to present a modal from otherwise.
        val pushCount = if (entries.isEmpty()) 0 else maxOf(firstModal, 1)
        val animated = animate && hasApplied && navigationController.view.window != null

        syncStack(entries.subList(0, pushCount), animated)
        syncModals(entries.subList(pushCount, entries.size), animated)
        detached.forEach { it.retire() }
        detached.clear()
        updateTopChrome()
        hasApplied = true
    }

    fun dispose() {
        disposed = true
        navigationController.delegate = null
        (stack + modals + retiring + detached).forEach { it.retire() }
        stack.clear()
        modals.clear()
        retiring.clear()
        detached.clear()
    }

    private fun syncStack(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = updateCommonPrefix(stack, entries)
        if (common == stack.size && common == entries.size) return

        val oldTop = stack.lastOrNull()
        val removed = stack.removeFrom(common)
        entries.drop(common).forEach { stack += hostFor(it, overlay = null) }
        // UIKit shows a back button for any item with a back action, even the root.
        stack.forEachIndexed { index, host ->
            val item = host.viewController.navigationItem
            if (index == 0) {
                item.backAction = null
            } else if (item.backAction == null) {
                item.backAction = backAction(this, host)
            }
        }

        // Only a change of the top controller produces a transition (and a didShow callback).
        val transition = animated && stack.lastOrNull() !== oldTop
        navigationController.setViewControllers(stack.map { it.viewController }, transition)
        if (transition) retiring += removed else removed.forEach { it.retire() }
    }

    private fun syncModals(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = updateCommonPrefix(modals, entries)

        if (common < modals.size) {
            // Dismissing the presenter's child dismisses everything presented above it too.
            val presenter = presenterFor(common)
            val removed = modals.removeFrom(common)
            modalTransitionInFlight = true
            presenter.dismissViewControllerAnimated(animated) {
                removed.forEach { it.retire() }
                modalTransitionInFlight = false
                setEntries(latestEntries, latestOverlays)
            }
            return
        }

        if (common < entries.size) {
            val presenter = presenterFor(common)
            if (presenter.view.window == null) {
                // Not on screen yet: the initial back stack ends with a sheet, or the tab is not
                // selected. Presented once the container appears.
                waitingForWindow = true
                return
            }
            val entry = entries[common]
            val host = hostFor(entry, latestOverlays[entry.contentKey])
            host.configureAsModal(this)
            modals += host
            modalTransitionInFlight = true
            presenter.presentViewController(host.viewController, animated) {
                modalTransitionInFlight = false
                setEntries(latestEntries, latestOverlays) // presents the next modal, if any
            }
        }
    }

    /**
     * A host for [entry]: one UIKit removed, if it can show the entry the same way again, or else a
     * new one.
     */
    private fun hostFor(entry: NavEntry<T>, overlay: (@Composable () -> Unit)?): EntryHost<T> {
        val index = detached.indexOfFirst { it.contentKey == entry.contentKey && it.isOverlay == (overlay != null) }
        if (index == -1) return EntryHost(entry, overlay, environment)
        return detached.removeAt(index).also { it.update(entry, overlay) }
    }

    private fun presenterFor(modalIndex: Int): UIViewController =
        if (modalIndex == 0) navigationController else modals[modalIndex - 1].viewController

    private fun onContainerAppeared() {
        if (!waitingForWindow) return
        waitingForWindow = false
        setEntries(latestEntries, latestOverlays)
    }

    fun onWillShow(viewController: UIViewController) {
        val host = stack.firstOrNull { it.viewController == viewController } ?: return
        navigationController.setNavigationBarHidden(!host.showsNavigationBar, animated = true)
    }

    fun onDidShow() {
        retiring.forEach { it.retire() }
        retiring.clear()
        val nativeCount = navigationController.viewControllers.size
        if (nativeCount < stack.size) {
            val top = stack.last()
            if (top.interceptsBack) {
                // A pop that bypassed the back action, such as one from the back button's history
                // menu. Put the screens back and let the entry's back handler decide.
                navigationController.setViewControllers(stack.map { it.viewController }, animated = true)
                top.dispatchBack()
                return
            }
            // UIKit popped on its own: swipe-back, the back button, or its long-press menu.
            reportNativeRemoval(stack.removeFrom(nativeCount))
        }
        updateTopChrome()
    }

    fun onSheetDismissedByUser(host: EntryHost<T>) {
        val index = modals.indexOf(host)
        if (index == -1) return
        reportNativeRemoval(modals.removeFrom(index))
    }

    private fun reportNativeRemoval(removed: List<EntryHost<T>>) {
        detached += removed
        repeat(removed.size) { onBack() }
        onNativeChange()
    }

    /**
     * Matches the navigation bar and the swipe-back gestures to the top entry. The gestures stay
     * off while that entry handles back itself; Compose's own edge gesture then delivers the swipe
     * to its handler.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun updateTopChrome() {
        if (disposed) return
        val top = stack.lastOrNull() ?: return
        val enabled = !top.interceptsBack
        navigationController.interactivePopGestureRecognizer?.enabled = enabled
        if (hasContentPopGesture) {
            navigationController.interactiveContentPopGestureRecognizer?.enabled = enabled
        }
        // During a push or pop the top view controller is already the incoming one.
        if (navigationController.topViewController == top.viewController) {
            val hidden = !top.showsNavigationBar
            if (navigationController.isNavigationBarHidden() != hidden) {
                navigationController.setNavigationBarHidden(hidden, animated = hasApplied)
            }
        }
        onTopChromeChanged()
    }

    fun onBackButton(host: EntryHost<T>) {
        if (host.interceptsBack) {
            host.dispatchBack()
        } else {
            navigationController.popViewControllerAnimated(true)
        }
    }

    /** Updates the hosts that already show [entries]' leading keys and returns how many there are. */
    private fun updateCommonPrefix(hosts: List<EntryHost<T>>, entries: List<NavEntry<T>>): Int {
        var i = 0
        while (i < hosts.size && i < entries.size && hosts[i].contentKey == entries[i].contentKey) {
            hosts[i].update(entries[i], latestOverlays[entries[i].contentKey])
            i++
        }
        return i
    }

    private fun NavEntry<T>.isModal(): Boolean =
        metadata[NativeNavigation.Presentation] is NativePresentation.Sheet || contentKey in latestOverlays
}

/** Removes the elements from [index] on and returns them. */
private fun <E> MutableList<E>.removeFrom(index: Int): List<E> {
    val tail = subList(index, size)
    return tail.toList().also { tail.clear() }
}

// Top-level functions so the blocks UIKit and hosted content keep capture only weak references.
@OptIn(ExperimentalNativeApi::class)
internal fun <C : Any> weakly(target: C, action: (C) -> Unit): () -> Unit {
    val weak = WeakReference(target)
    return { weak.value?.let(action) }
}

@OptIn(ExperimentalNativeApi::class)
private fun <T : Any> backAction(coordinator: UIKitNavigationCoordinator<T>, host: EntryHost<T>): UIAction {
    val weakCoordinator = WeakReference(coordinator)
    val weakHost = WeakReference(host)
    return UIAction.actionWithHandler { _ ->
        val h = weakHost.value
        if (h != null) weakCoordinator.value?.onBackButton(h)
    }
}

/** Reports when the container appears, which is when modals that waited for a window can show. */
private class ContainerNavigationController(
    private val onAppear: () -> Unit,
) : UINavigationController(nibName = null, bundle = null) {
    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        onAppear()
    }
}

@OptIn(ExperimentalNativeApi::class)
private class NavigationDelegate(
    coordinator: UIKitNavigationCoordinator<*>,
) : NSObject(), UINavigationControllerDelegateProtocol {
    private val coordinator = WeakReference(coordinator)

    @ObjCSignatureOverride
    override fun navigationController(
        navigationController: UINavigationController,
        willShowViewController: UIViewController,
        animated: Boolean,
    ) {
        coordinator.value?.onWillShow(willShowViewController)
    }

    @ObjCSignatureOverride
    override fun navigationController(
        navigationController: UINavigationController,
        didShowViewController: UIViewController,
        animated: Boolean,
    ) {
        coordinator.value?.onDidShow()
    }
}
