# How it works on iOS

This page explains what the displays do with UIKit. For setup and usage, see the [README](../README.md).

## Navigation stack

- An embedded display fills the space its `modifier` leaves it, because a UIKit container has no content size for Compose to measure. This matches `NavDisplay` showing screens that fill the screen, so a call site without `Modifier.fillMaxSize()` still shows its screens.
- Each entry is placed in its own `ComposeUIViewController`, and those controllers are kept in the `UINavigationController` stack. Entries with sheet metadata are presented modally on top of the stack.
- `sceneStrategies` run as in `NavDisplay`. An entry that a strategy shows in an `OverlayScene`, such as a `DialogSceneStrategy` dialog, is presented over a clear full-screen controller with the scene's own content, so the screens below stay visible. Entries of other scenes are pushed one by one.
- The back stack is the source of truth. When the back stack changes, the entries are compared against the hosted controllers by `contentKey` and position: the common prefix is kept and the rest is replaced.

## Back gestures and back handlers

- When UIKit removes a controller itself (edge swipe-back, the back button, dragging a sheet down), `onBack` is called once per removed entry. Cancelling a swipe partway changes nothing. If the app leaves its back stack unchanged, for example because the screen is busy, the display puts the removed screens back. A tab the user picks is not shown until the app selects it, as `NavDisplay` only shows the back stack it is given.
- When the top entry's content has an enabled back handler (`NavigationBackHandler`, `BackHandler`, an open `ModalBottomSheet`, and so on), UIKit does not pop it. The back button, the edge swipe, the back button's history menu, and dragging a sheet down all go to that handler, as system back does under `NavDisplay`.

## Navigation bar

The native navigation bar is shown only for entries that ask for it, with `NativeNavigation.title` metadata or a `NativeNavigationBarItems` call. Screens that draw their own top bar look as they do under `NavDisplay`. `LocalNativeNavigationBar` tells a screen whether it can use the native bar.

## Composition, decorators, and CompositionLocals

- The `configure` block of `NativeNavigationViewController` also configures every entry's controller. Entry controllers skip Compose's `Info.plist` check, which the app's root controller has already made.
- Decorators run through Navigation 3's `rememberDecoratedNavEntries` in the host composition. Each entry's content is composed inside its own controller. Once a popped entry finishes its transition, its content leaves composition, and Navigation 3 calls the decorators' `onPop`. Because of this, the saveable state and ViewModel decorators work unchanged.
- Since each controller is a separate composition, the CompositionLocals from the display's call site (theme, DI, and so on) are re-provided to each one. The controller's own platform locals (lifecycle, `LocalUIViewController`, and so on) take precedence over them.

## Tabs

- Each tab is the same navigation coordinator `NativeNavDisplay` uses, so pushes, swipe-back, sheets, and metadata work inside every tab.
- With the single back stack overload, each tab shows its part of the back stack, from its root key up to the next tab's root key. The tab whose root key comes last is selected. A tab that occurs twice shows its last part, and a tab that is not in the back stack is empty. Tapping such a tab calls `onTabSelected` without switching, and UIKit selects the tab once the back stack holds it, so the empty tab is never shown. Its entries are decorated once in back stack order, as `NavDisplay` decorates them, so an entry that leaves the back stack loses its state there too.
- With the `NativeTab` overload, the caller owns the selection. Tapping another tab calls `onTabSelected` without switching; changing `selectedTab` selects the tab in UIKit. Tapping the selected tab again makes UIKit pop that tab to its root, which reaches the back stack through `onBack(tab)` like any other native pop.
- With the `NativeTab` overload, every tab's entries stay decorated while the tab is hidden, so `remember`, `rememberSaveable`, and ViewModels survive tab switches. The library prefixes each entry's `contentKey` with its tab, so the same key on two tabs gets separate saveable state and a separate ViewModel.
- A back stack can change while its tab is hidden, for example to open a screen in another tab. The tab shows the new stack when it is selected. Every tab can also get a new root while another tab is selected in the same frame, as when an app rebuilds its main tabs after a flow finishes.
- `NativeNavigation.tabBarHidden()` metadata hides the tab bar while that entry is pushed above its tab's root, as detail screens in many apps do. It sets `hidesBottomBarWhenPushed`, so the tab bar leaves with the push transition.
- `NativeTabBarHidden` hides the tab bar with `UITabBarController.setTabBarHidden(_:animated:)` while its entry is the top pushed entry of the selected tab, at the tab's root too. Calls from screens below the top one, from sheets, and from other tabs have no effect. A change that comes during a push or pop is made once the transition has finished, because iOS 27 does not show a tab bar change made during the transition. While the top entry hides the tab bar through metadata, UIKit's push transition handles the tab bar and the display leaves it as it is.
- A tab's `badge` is its `UITab.badgeValue`. It belongs to the tab rather than to a screen: an entry's content is composed only once its controller's view is in a window, so the screens of a tab the user has not opened yet are not composed.
- A tab's icon is the SF Symbol `systemImage` when it is set, and the `icon` painter otherwise. iOS draws the painter as a template image, so the tab bar tints it.

## iPhone Duo

iPhone Duo can move the navigation bar items and the tab bar into a vertical bar on one side of the display (the trailing side in the simulator), with the tab bar at the bottom of it. Because the displays use the system containers, UIKit does this layout itself: the tab buttons and the back button move into the vertical bar, and the bar's width reaches Compose as a leading or trailing safe area inset, so content padded with `WindowInsets.safeDrawing` stays clear of it.

This only works when the native container is not embedded in Compose. A `UITabBarController` placed inside a `ComposeUIViewController` through `UIKitViewController` interop shows its vertical tab bar on the closed (outer) display, but on the open inner display the system reserves the vertical bar and draws no tabs in it. A `UITabBarController` that is a direct child of a plain `UIViewController` is laid out correctly.

`NativeNavigationViewController` provides that structure. The first `NativeTabDisplay` composed inside it hands its tab bar controller to the root view controller, which places it above the Compose view instead of inside it. When that display leaves composition, the tab display that has waited longest takes its place. The root tab display's `modifier` is ignored, because the container always fills the screen, and Compose content drawn outside that display is covered by it. `NativeNavDisplay` always stays embedded in Compose, so a sign-in display or an app's own Compose tab bar around a `NativeNavDisplay` look the same under either root controller.

`UITraitCollection.verticalBarEdge` and the other iPhone Duo APIs are iOS 27.1 SDK API. Kotlin/Native binds UIKit from the SDK of the Xcode its toolchain was built with (the `targetToolchain` entry of the Kotlin/Native distribution's `konan/konan.properties`). With an older binding, Kotlin code reads these APIs through key-value coding, as `sample/src/iosMain/kotlin/dev/seungmin/nav3/sample/VerticalBarEdge.kt` does. The vertical bar layout itself does not depend on the binding, because Xcode links the app target against its own SDK.
