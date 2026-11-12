package dev.seungmin.nav3.uikit

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent

/**
 * One top-level destination of [NativeTabDisplay], with its own back stack.
 *
 * The tab's icon is [systemImage], an SF Symbol name, on iOS when it is set, and [icon] otherwise.
 * iOS draws [icon] as a template image, so the tab bar tints it for the selected and unselected
 * states. Other platforms draw [icon] in the default tab bar.
 *
 * [badge] is shown on the tab, such as an unread count; null shows no badge. On iOS it is the
 * tab's `badgeValue`, so UIKit draws the badge and only its text is up to the app. Other platforms
 * draw it in the default tab bar; an app that passes its own `tabBar` draws it there.
 */
public class NativeTab<out T : Any>(
    public val backStack: List<T>,
    public val title: String,
    public val icon: Painter? = null,
    public val systemImage: String? = null,
    public val badge: String? = null,
) {
    /** A tab whose icon is the SF Symbol [icon] on iOS. */
    public constructor(backStack: List<T>, title: String, icon: String, badge: String? = null) :
        this(backStack, title, icon = null, systemImage = icon, badge = badge)

    internal val label: TabLabel get() = TabLabel(title, icon, systemImage, badge)
}

/**
 * One top-level destination of the single back stack [NativeTabDisplay]: the tab whose back stack
 * starts at [key]. The icon and [badge] follow the same rules as [NativeTab].
 */
public class NativeTabRoot<out T : Any>(
    public val key: T,
    public val title: String,
    public val icon: Painter? = null,
    public val systemImage: String? = null,
    public val badge: String? = null,
) {
    /** A tab whose icon is the SF Symbol [icon] on iOS. */
    public constructor(key: T, title: String, icon: String, badge: String? = null) :
        this(key, title, icon = null, systemImage = icon, badge = badge)

    internal val label: TabLabel get() = TabLabel(title, icon, systemImage, badge)
}

/** What the tab bar shows for one tab. */
internal data class TabLabel(
    val title: String,
    val icon: Painter?,
    val systemImage: String?,
    val badge: String? = null,
) {
    /** What the tab's image is made from. */
    val iconSource: Any? get() = systemImage ?: icon
}

/**
 * Displays several Navigation 3 back stacks behind a bottom tab bar.
 *
 * On iOS this is a `UITabBarController` whose tabs each hold a `UINavigationController`, so the
 * system decides where the tab bar goes: at the bottom on most iPhones, in the side bar on
 * iPhone Duo, or as a sidebar where one is available. Each tab behaves like [NativeNavDisplay]:
 * native pushes, swipe-back, sheets, overlays from [sceneStrategies], and per-entry metadata from
 * [NativeNavigation].
 *
 * The caller owns the selection and the back stacks, as `NavDisplay`'s caller owns its back stack.
 * When the user picks another tab in the tab bar, [onTabSelected] is called with its index, and the
 * display shows that tab once [selectedTab] changes. When UIKit pops a tab's screens on its own
 * (swipe-back, the back button, or tapping the selected tab again), [onBack] is called once per
 * removed entry with that tab's index. If the caller does not follow a pop, the display puts the
 * screens back.
 *
 * Every tab keeps its entries decorated while another tab is shown, so saveable state and
 * ViewModels survive tab switches. Entries of different tabs never share decorator state, even
 * when the same key is on two back stacks.
 *
 * A screen hides the tab bar from its own state with [NativeTabBarHidden], and from its metadata
 * with [NativeNavigation.tabBarHidden].
 *
 * Other platforms show the selected tab's back stack in a `NavDisplay` that receives the scene and
 * transition parameters, with [tabBar] below it; pass the app's own tab bar there. [tabBar] is
 * hidden while a screen calls [NativeTabBarHidden]. System back at the root of any tab but the
 * first selects the first tab, as in Navigation 3's multiple back stack recipe. iOS ignores
 * [tabBar] and the transition parameters.
 */
@Composable
public fun <T : Any> NativeTabDisplay(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: (tab: Int) -> Unit = { tab ->
        val backStack = tabs[tab].backStack
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategies: List<SceneStrategy<T>> = remember { listOf(SinglePaneSceneStrategy()) },
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    tabBar: @Composable () -> Unit = { DefaultTabBar(tabs.map { it.label }, selectedTab, onTabSelected) },
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(tabs.isNotEmpty()) { "NativeTabDisplay needs at least one tab" }
    require(selectedTab in tabs.indices) { "selectedTab $selectedTab is not a tab index" }

    // Decorating every tab, not just the selected one, keeps the hidden tabs' entries out of
    // Navigation 3's pop handling while they are off screen.
    val entries = tabs.mapIndexed { index, tab ->
        key(index) { rememberTabEntries(index, tab.backStack, entryDecorators, entryProvider) }
    }
    PlatformTabDisplay(
        tabs = tabs.map { it.label },
        entries = entries,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        onBack = onBack,
        options = NavDisplayOptions(
            contentAlignment = contentAlignment,
            sceneStrategies = sceneStrategies,
            sceneDecoratorStrategies = sceneDecoratorStrategies,
            sharedTransitionScope = sharedTransitionScope,
            sizeTransform = sizeTransform,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            predictivePopTransitionSpec = predictivePopTransitionSpec,
        ),
        tabBar = tabBar,
        modifier = modifier,
    )
}

/**
 * Displays one Navigation 3 back stack that holds several tabs behind a bottom tab bar.
 *
 * This is the shape of an app that passes a single back stack to `NavDisplay` and draws its own
 * tab bar next to it: each tab's back stack starts at that tab's root key, and the back stack
 * holds the visited tabs one after another, as the `TopLevelBackStack` of Navigation 3's
 * `commonui` recipe does. For example, `[Home, Detail(1), Search, Detail(2)]` holds the Home tab
 * `[Home, Detail(1)]` and the Search tab `[Search, Detail(2)]`. The tab whose root key comes last is the selected one, so the
 * back stack is the only state the display reads. [backStack] must start with one of the [tabs]
 * keys, because a key before the first tab root would belong to no tab.
 *
 * When the user picks a tab, [onTabSelected] is called with its root key; pass what the app's own
 * tab bar called, such as the recipe's `addTopLevel`. [onBack] removes the last entry by default,
 * as in `NavDisplay`.
 *
 * On iOS this is the same `UITabBarController` as the [NativeTab] overload. Each tab shows its part
 * of [backStack]; a tab that occurs twice shows its last part, and a tab that is not in [backStack]
 * is empty until it is selected. When UIKit pops screens on its own, [onBack] is called once per
 * removed entry; those screens are always the last entries of [backStack], because only the
 * selected tab is on screen. UIKit has no back action at a tab's root, so the order of the tabs
 * in [backStack] only matters on other platforms.
 *
 * Other platforms show the whole [backStack] in a `NavDisplay` with [tabBar] below it, so system
 * back follows [backStack] exactly as it did under `NavDisplay`, including from one tab's root to
 * the tab before it. [tabBar] is hidden while a screen calls [NativeTabBarHidden].
 *
 * The entries are decorated once, in [backStack] order, as `NavDisplay` decorates them. An entry
 * leaves composition and loses its decorator state when it leaves [backStack], so keep a tab's
 * entries in [backStack] while it is hidden if its screens should keep their state. To keep a
 * separate back stack per tab instead, use the [NativeTab] overload.
 */
@Composable
public fun <T : Any> NativeTabDisplay(
    backStack: List<T>,
    tabs: List<NativeTabRoot<T>>,
    onTabSelected: (key: T) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategies: List<SceneStrategy<T>> = remember { listOf(SinglePaneSceneStrategy()) },
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    tabBar: @Composable () -> Unit = {
        DefaultTabBar(
            tabs = tabs.map { it.label },
            selectedTab = tabParts(backStack, tabs.map { it.key }).selected,
            onTabSelected = { onTabSelected(tabs[it].key) },
        )
    },
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(tabs.isNotEmpty()) { "NativeTabDisplay needs at least one tab" }
    require(backStack.isNotEmpty()) { "NativeTabDisplay backstack cannot be empty" }
    val roots = tabs.map { it.key }
    require(roots.toSet().size == roots.size) { "NativeTabDisplay tab keys must be distinct" }
    require(backStack.first() in roots) {
        "NativeTabDisplay backstack must start with a tab key, but starts with ${backStack.first()}"
    }

    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    PlatformStackTabDisplay(
        tabs = tabs.map { it.label },
        entries = entries,
        parts = tabParts(backStack, roots),
        onTabSelected = { onTabSelected(tabs[it].key) },
        onBack = onBack,
        options = NavDisplayOptions(
            contentAlignment = contentAlignment,
            sceneStrategies = sceneStrategies,
            sceneDecoratorStrategies = sceneDecoratorStrategies,
            sharedTransitionScope = sharedTransitionScope,
            sizeTransform = sizeTransform,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            predictivePopTransitionSpec = predictivePopTransitionSpec,
        ),
        tabBar = tabBar,
        modifier = modifier,
    )
}

/**
 * Where each tab's part of a single back stack is: [ranges] holds, per tab, the indices of its last
 * part, or null for a tab that is not in the back stack. [selected] is the tab of the last part.
 */
internal class TabParts(val ranges: List<IntRange?>, val selected: Int)

internal fun <T : Any> tabParts(backStack: List<T>, roots: List<T>): TabParts {
    val ranges = MutableList<IntRange?>(roots.size) { null }
    var selected = 0
    var start = 0
    while (start < backStack.size) {
        var end = start + 1
        while (end < backStack.size && backStack[end] !in roots) end++
        val tab = roots.indexOf(backStack[start])
        // Keys before the first tab root are not shown.
        if (tab != -1) {
            ranges[tab] = start until end
            selected = tab
        }
        start = end
    }
    return TabParts(ranges, selected)
}

@Composable
internal expect fun <T : Any> PlatformStackTabDisplay(
    tabs: List<TabLabel>,
    entries: List<NavEntry<T>>,
    parts: TabParts,
    onTabSelected: (index: Int) -> Unit,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
)

/** A plain row of tab icons and titles, for platforms where the app passes no tab bar of its own. */
@Composable
private fun DefaultTabBar(tabs: List<TabLabel>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onTabSelected(index) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tab.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        tab.title,
                        style = TextStyle(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    )
                    tab.badge?.let { DefaultBadge(it) }
                }
            }
        }
    }
}

@Composable
private fun DefaultBadge(text: String) {
    BasicText(
        text,
        modifier = Modifier
            .background(Color(0xFFFF3B30), RoundedCornerShape(percent = 50))
            .padding(horizontal = 6.dp),
        style = TextStyle(color = Color.White, fontSize = 12.sp),
    )
}

@Composable
private fun <T : Any> rememberTabEntries(
    tab: Int,
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
): List<NavEntry<T>> {
    // Decorators are shared by all tabs and keep their state by content key. Prefixing the key
    // with the tab keeps two tabs showing the same destination apart.
    val entries = remember(backStack.toList()) {
        backStack.map { key ->
            val entry = entryProvider(key)
            NavEntry(key, "tab$tab/${entry.contentKey}", entry.metadata) { entry.Content() }
        }
    }
    return rememberDecoratedNavEntries(entries, entryDecorators)
}

@Composable
internal expect fun <T : Any> PlatformTabDisplay(
    tabs: List<TabLabel>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
)
