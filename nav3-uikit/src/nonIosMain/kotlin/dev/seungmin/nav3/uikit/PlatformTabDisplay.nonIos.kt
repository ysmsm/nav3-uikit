package dev.seungmin.nav3.uikit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
internal actual fun <T : Any> PlatformTabDisplay(
    tabs: List<TabLabel>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
) {
    TabDisplayLayout(tabBar, modifier) {
        PlatformNavDisplay(entries[selectedTab], Modifier, onBack = { onBack(selectedTab) }, options)
    }

    // At the root of another tab, back returns to the first tab instead of leaving the app.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selectedTab != 0 && entries[selectedTab].size <= 1,
        onBackCompleted = { onTabSelected(0) },
    )
}

@Composable
internal actual fun <T : Any> PlatformStackTabDisplay(
    tabs: List<TabLabel>,
    entries: List<NavEntry<T>>,
    parts: TabParts,
    onTabSelected: (index: Int) -> Unit,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
) {
    // The whole back stack goes to NavDisplay, so back crosses from one tab to the one before it
    // exactly as it did before the app switched to this display.
    TabDisplayLayout(tabBar, modifier) {
        PlatformNavDisplay(entries, Modifier, onBack, options)
    }
}

/** [content] above [tabBar], which is hidden while a screen in [content] calls [NativeTabBarHidden]. */
@Composable
private fun TabDisplayLayout(tabBar: @Composable () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val requests = remember { TabBarHiddenRequests() }
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalTabBarHiddenRequests provides requests, content = content)
        }
        AnimatedVisibility(visible = !requests.hidden) { tabBar() }
    }
}
