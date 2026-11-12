package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Hides the tab bar of [NativeTabDisplay] while this call stays in composition and [hidden] is
 * `true`, for a screen whose state decides it:
 * ```
 * NativeTabBarHidden(hidden = expanded)
 * ```
 * The tab bar comes back when [hidden] becomes `false` or the screen is popped. For a screen that
 * always hides the tab bar, [NativeNavigation.tabBarHidden] metadata also hides it during the push.
 *
 * On iOS the call applies while its entry is the top pushed entry of the selected tab, at a tab's
 * root too; calls from screens below it, sheets, and overlays have no effect. On other platforms
 * [NativeTabDisplay] hides its `tabBar` while any screen it shows makes the call. Outside
 * [NativeTabDisplay] it does nothing.
 */
@Composable
public fun NativeTabBarHidden(hidden: Boolean = true) {
    val requests = LocalTabBarHiddenRequests.current ?: return
    if (hidden) {
        DisposableEffect(requests) {
            requests.add()
            onDispose { requests.remove() }
        }
    }
}

/**
 * Counts the [NativeTabBarHidden] calls that hide the tab bar. [onChanged] is called when
 * [hidden] changes; on iOS it must not hold a coordinator strongly, because content reaches it.
 */
internal class TabBarHiddenRequests(private val onChanged: () -> Unit = {}) {
    private var count by mutableIntStateOf(0)

    val hidden: Boolean get() = count > 0

    fun add() {
        count++
        if (count == 1) onChanged()
    }

    fun remove() {
        count--
        if (count == 0) onChanged()
    }
}

/** Where [NativeTabBarHidden] reports to: the hosting entry on iOS, the tab display elsewhere. */
internal val LocalTabBarHiddenRequests = compositionLocalOf<TabBarHiddenRequests?> { null }
