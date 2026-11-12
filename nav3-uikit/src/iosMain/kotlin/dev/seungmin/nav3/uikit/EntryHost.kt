package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationOverFullScreen
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCrossDissolve
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObject

/** The locals at the NativeNavDisplay call site, shared by every hosted controller. */
internal class ParentLocals(initial: CompositionLocalContext) {
    var value by mutableStateOf(initial)
}

/**
 * What every host of one coordinator shares: the app's controller configuration and callbacks into
 * the coordinator. The callbacks must not hold the coordinator strongly, because hosted content
 * reaches them.
 */
internal class HostEnvironment(
    val parentLocals: ParentLocals,
    val configure: ComposeUIViewControllerConfiguration.() -> Unit,
    /** An entry's back handlers, navigation bar request, or tab bar request changed. */
    val onEntryChromeChanged: () -> Unit,
)

/**
 * One back stack entry hosted in its own Compose-backed UIViewController.
 *
 * Kotlin/Native cannot collect reference cycles that pass through Objective-C objects, so nothing
 * reachable from [viewController] or from a UIKit delegate may point back at this host or at the
 * coordinator with a strong reference. The controller's content only sees [EntryContent] and
 * [ParentLocals]; delegates and callbacks hold weak references.
 *
 * An entry that a scene strategy shows in an overlay scene is hosted with that scene's content
 * ([overlay]) over a clear, full-screen controller, so the screens below stay visible.
 */
internal class EntryHost<T : Any>(
    entry: NavEntry<T>,
    overlay: (@Composable () -> Unit)?,
    environment: HostEnvironment,
) {
    val contentKey: Any = entry.contentKey

    /** Entries keep the presentation they were created with. */
    val isOverlay: Boolean = overlay != null

    private val content = EntryContent(entry, overlay, environment)
    private var presentationDelegate: SheetPresentationDelegate<T>? = null

    val viewController: UIViewController =
        hostedController(content, environment, transparent = isOverlay)

    private val navigationItem =
        EntryNavigationItem(viewController, environment.onEntryChromeChanged).also { content.navigationItem = it }

    /**
     * Whether the native navigation bar is shown while this entry is on top: when the entry can
     * show it and asks for it with a title or [NativeNavigationBarItems].
     */
    val showsNavigationBar: Boolean
        get() = content.navigationBarAvailable &&
            (content.entry.metadata[NativeNavigation.Title] != null || navigationItem.requested)

    /**
     * Whether the entry's content has an enabled back handler (`NavigationBackHandler`,
     * `BackHandler`, an open `ModalBottomSheet`, ...). UIKit must then not pop the entry on its
     * own; [dispatchBack] hands the back event to that handler instead.
     */
    val interceptsBack: Boolean
        get() = content.backInput.hasEnabledHandlers

    /** Whether the entry's content hides the tab bar with [NativeTabBarHidden]. */
    val requestsTabBarHidden: Boolean
        get() = content.tabBarRequests.hidden

    /** Whether [NativeNavigation.tabBarHidden] metadata hides the tab bar while the entry is pushed. */
    val hidesTabBarWhenPushed: Boolean
        get() = viewController.hidesBottomBarWhenPushed

    fun dispatchBack() {
        content.backInput.back()
    }

    init {
        viewController.view.backgroundColor =
            if (isOverlay) UIColor.clearColor else UIColor.systemBackgroundColor
        NativeNavDiagnostics.track(viewController)
        applyMetadata()
    }

    fun update(newEntry: NavEntry<T>, overlay: (@Composable () -> Unit)?) {
        val metadataChanged = newEntry.metadata != content.entry.metadata
        content.entry = newEntry
        // An overlay whose scene is gone keeps its presentation and shows the entry directly.
        if (isOverlay) content.overlay = overlay
        if (metadataChanged) applyMetadata()
    }

    fun configureAsModal(coordinator: UIKitNavigationCoordinator<T>) {
        content.pushed = false
        if (isOverlay) {
            viewController.modalPresentationStyle = UIModalPresentationOverFullScreen
            viewController.modalTransitionStyle = UIModalTransitionStyleCrossDissolve
            return
        }
        val sheet = content.entry.metadata[NativeNavigation.Presentation] as? NativePresentation.Sheet
            ?: NativePresentation.Sheet()
        viewController.modalPresentationStyle = UIModalPresentationPageSheet
        viewController.sheetPresentationController?.let { controller ->
            controller.detents = sheet.detents.map {
                when (it) {
                    SheetDetent.Medium -> UISheetPresentationControllerDetent.mediumDetent()
                    SheetDetent.Large -> UISheetPresentationControllerDetent.largeDetent()
                }
            }
            controller.prefersGrabberVisible = sheet.grabberVisible
        }
        // UIKit holds presentation delegates weakly, so this host keeps it alive.
        val delegate = SheetPresentationDelegate(this, coordinator)
        presentationDelegate = delegate
        viewController.presentationController?.delegate = delegate
    }

    /** Removes the entry's content from composition. Call once the controller is off screen. */
    fun retire() {
        content.active = false
    }

    private fun applyMetadata() {
        val metadata = content.entry.metadata
        navigationItem.metadataTitle = metadata[NativeNavigation.Title]
        viewController.navigationItem.largeTitleDisplayMode =
            if (metadata[NativeNavigation.LargeTitle] == true) {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
            } else {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
            }
        viewController.hidesBottomBarWhenPushed = metadata[NativeNavigation.TabBarHidden] == true
    }
}

private class EntryContent<T : Any>(
    entry: NavEntry<T>,
    overlay: (@Composable () -> Unit)?,
    environment: HostEnvironment,
) {
    var entry by mutableStateOf(entry)
    var overlay by mutableStateOf(overlay)
    var active by mutableStateOf(true)
    var pushed by mutableStateOf(true)
    var navigationItem: EntryNavigationItem? = null
    val backInput = EntryBackInput().apply { onChanged = environment.onEntryChromeChanged }
    val tabBarRequests = TabBarHiddenRequests(environment.onEntryChromeChanged)

    val navigationBarAvailable: Boolean
        get() = pushed && entry.metadata[NativeNavigation.NavigationBarHidden] != true
}

/** Reports whether an entry's content handles back, and delivers native back actions to it. */
internal class EntryBackInput : NavigationEventInput() {
    var hasEnabledHandlers = false
        private set

    /** Called when [hasEnabledHandlers] changes. Must not hold the coordinator strongly. */
    var onChanged: (() -> Unit)? = null

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        this.hasEnabledHandlers = hasEnabledHandlers
        onChanged?.invoke()
    }

    fun back() {
        if (hasEnabledHandlers) dispatchOnBackCompleted()
    }
}

// A top-level function so the content lambda cannot capture the EntryHost.
@OptIn(ExperimentalComposeUiApi::class)
private fun <T : Any> hostedController(
    content: EntryContent<T>,
    environment: HostEnvironment,
    transparent: Boolean,
): UIViewController {
    val parentLocals = environment.parentLocals
    val configure = environment.configure
    return ComposeUIViewController(
        configure = {
            // The app's root controller already checked Info.plist. Hosted controllers skip the
            // check so that an app which turned it off there is not stopped by it here.
            enforceStrictPlistSanityCheck = false
            configure()
            if (transparent) opaque = false
        },
    ) {
        // Each UIViewController is a separate composition. Re-provide the locals from where
        // NativeNavDisplay was called (theme, DI, ...), then restore this controller's own
        // platform locals (lifecycle, density, UIViewController, ...) on top of them.
        val ownLocals = currentCompositionLocalContext
        CompositionLocalProvider(parentLocals.value) {
            CompositionLocalProvider(ownLocals) {
                CompositionLocalProvider(
                    LocalNativeNavigationBarAvailable provides content.navigationBarAvailable,
                    LocalEntryNavigationItem provides content.navigationItem,
                    LocalTabBarHiddenRequests provides content.tabBarRequests,
                ) {
                    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
                    DisposableEffect(dispatcherOwner) {
                        val dispatcher = dispatcherOwner?.navigationEventDispatcher
                        dispatcher?.addInput(content.backInput)
                        onDispose { dispatcher?.removeInput(content.backInput) }
                    }
                    // Leaving composition here is what lets Navigation 3 run the decorators' onPop.
                    if (content.active) {
                        val overlay = content.overlay
                        if (overlay != null) overlay() else content.entry.Content()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalNativeApi::class)
private class SheetPresentationDelegate<T : Any>(
    host: EntryHost<T>,
    coordinator: UIKitNavigationCoordinator<T>,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
    private val host = WeakReference(host)
    private val coordinator = WeakReference(coordinator)

    override fun presentationControllerShouldDismiss(presentationController: UIPresentationController): Boolean =
        host.value?.interceptsBack != true

    override fun presentationControllerDidAttemptToDismiss(presentationController: UIPresentationController) {
        host.value?.dispatchBack()
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        val host = host.value ?: return
        coordinator.value?.onSheetDismissedByUser(host)
    }
}
