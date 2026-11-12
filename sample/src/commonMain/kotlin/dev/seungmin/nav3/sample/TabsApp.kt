package dev.seungmin.nav3.sample

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.seungmin.nav3.uikit.NativeNavigation
import dev.seungmin.nav3.uikit.NativeTab
import dev.seungmin.nav3.uikit.NativeTabBarHidden
import dev.seungmin.nav3.uikit.NativeTabDisplay
import dev.seungmin.nav3.uikit.SheetDetent
import kotlinx.serialization.Serializable

/** Where the system put the vertical bar on iPhone Duo ("leading", "trailing"), or null. */
@Composable
expect fun verticalBarEdge(): String?

/** A screen pushed with the tab bar hidden. */
@Serializable data object Focus : Route

/** A pushed screen that hides the tab bar from its state. */
@Serializable data object Panel : Route

/** [onSignOut], when set, adds a Sign out button to the Home tab. */
@Composable
fun TabsApp(onSignOut: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalAccent provides Color(0xFF8A3FD1)) {
        val home = rememberNavBackStack(config, Home)
        val search = rememberNavBackStack(config, SearchHome)
        val profile = rememberNavBackStack(config, Profile)
        val backStacks = listOf(home, search, profile)
        var selected by rememberSaveable { mutableIntStateOf(0) }
        var homeBadge by rememberSaveable { mutableIntStateOf(0) }
        // Only the selected tab can be interacted with, so screens act on its back stack.
        val current: NavBackStack<NavKey> = backStacks[selected]
        val describe = { stack: List<NavKey> -> stack.joinToString(" > ") }
        val names = listOf("Home", "Search", "Profile")

        NativeTabDisplay(
            tabs = listOf(
                NativeTab(home, "Home", icon = "house", badge = homeBadge.takeIf { it > 0 }?.toString()),
                NativeTab(search, "Search", icon = "magnifyingglass"),
                NativeTab(profile, "Profile", icon = "person.crop.circle"),
            ),
            selectedTab = selected,
            modifier = Modifier.fillMaxSize(),
            onTabSelected = {
                selected = it
                EventLog.log("tab -> ${names[it]}")
            },
            onBack = { tab ->
                backStacks[tab].removeLastOrNull()
                EventLog.log("onBack(${names[tab]}) -> ${describe(backStacks[tab])}")
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Home>(metadata = NativeNavigation.title("Home", large = true)) {
                    TabScreen(names[selected], describe(current)) {
                        onSignOut?.let { Button("Sign out", it) }
                        StateCounters()
                        Button("Open detail 1") { current += Detail(1) }
                        Button("Open settings sheet") { current += Settings }
                        Button("Go to Profile tab") { selected = 2 }
                        Button("Push detail 7 in Search tab") { search += Detail(7) }
                        Button("Open sheet in Search tab") { search += Settings }
                        Button("Open focus") { current += Focus }
                        LiveControllersButton()
                        EventList()
                    }
                }
                entry<SearchHome>(metadata = NativeNavigation.title("Search", large = true)) {
                    TabScreen(names[selected], describe(current)) {
                        Button("Open detail 1") { current += Detail(1) }
                        Button("Open detail 2") { current += Detail(2) }
                        Button("Add Home badge") { homeBadge++ }
                        Button("Clear Home badge") { homeBadge = 0 }
                    }
                }
                entry<Profile>(metadata = NativeNavigation.title("Profile", large = true)) {
                    TabScreen(names[selected], describe(current)) {
                        Button("Go to Home tab") { selected = 0 }
                        Button("Open immersive") { current += Immersive }
                        Button("Open panel") { current += Panel }
                        ExpandControls()
                    }
                }
                entry<Detail>(metadata = { NativeNavigation.title("Detail ${it.id}") }) { key ->
                    val vm = viewModel { DetailViewModel(key.id) }
                    TabScreen(names[selected], describe(current)) {
                        Button("ViewModel taps: ${vm.taps}") { vm.taps++ }
                        Button("Push detail ${key.id + 1}") { current += Detail(key.id + 1) }
                        Button("Pop to root") { while (current.size > 1) current.removeLastOrNull() }
                    }
                }
                entry<Settings>(
                    metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large)),
                ) {
                    TabScreen(names[selected], describe(current)) {
                        BasicText("Settings sheet", style = TextStyle(fontSize = 22.sp))
                        Button("Close sheet") { current.removeLastOrNull() }
                    }
                }
                entry<Focus>(metadata = NativeNavigation.title("Focus") + NativeNavigation.tabBarHidden()) {
                    TabScreen(names[selected], describe(current)) {
                        BasicText("Tab bar hidden here", style = TextStyle(fontSize = 22.sp))
                    }
                }
                entry<Panel>(metadata = NativeNavigation.title("Panel")) {
                    TabScreen(names[selected], describe(current)) {
                        ExpandControls()
                        Button("Open focus") { current += Focus }
                    }
                }
                entry<Immersive>(metadata = NativeNavigation.navigationBarHidden()) {
                    TabScreen(names[selected], describe(current)) {
                        BasicText("No navigation bar here", style = TextStyle(fontSize = 22.sp))
                        Button("Back") { current.removeLastOrNull() }
                    }
                }
            },
        )
    }
}

/** A toggle that hides the tab bar while it is expanded. */
@Composable
private fun ExpandControls() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    NativeTabBarHidden(hidden = expanded)
    Button(if (expanded) "Collapse" else "Expand") { expanded = !expanded }
}

/** [Screen] plus the insets and vertical bar edge the screen sees, for checking iPhone Duo. */
@Composable
private fun TabScreen(tab: String, backStack: String, content: @Composable () -> Unit) {
    Screen("$tab: $backStack") {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing
        val edge = verticalBarEdge()
        with(density) {
            val l = insets.getLeft(this, direction).toDp().value.toInt()
            val t = insets.getTop(this).toDp().value.toInt()
            val r = insets.getRight(this, direction).toDp().value.toInt()
            val b = insets.getBottom(this).toDp().value.toInt()
            BasicText(
                "insets l=$l t=$t r=$r b=$b · host=${launchFlag("NAV3_HOST") ?: "root"} · bar=${edge ?: "none"}",
                style = TextStyle(fontSize = 12.sp, color = Color.Gray),
            )
        }
        content()
        // Last, so that it does not move the screen's buttons.
        BasicText(
            "container=${containerPlacement() ?: "?"}",
            style = TextStyle(fontSize = 12.sp, color = Color.Gray),
        )
    }
}
