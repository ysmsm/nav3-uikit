<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/hero-dark.png">
    <img src="docs/images/hero-light.png" alt="Nav3 UIKit: the same Compose screens in native UIKit containers on iPhone, iPad, and a closed iPhone Duo, where the back button, bar buttons, and tab bar move into the vertical bar" width="880">
  </picture>
</p>

<p align="center"><b>Native UIKit navigation for Compose Multiplatform apps built on Navigation 3.</b></p>

<p align="center">
  <img src="https://img.shields.io/badge/status-experimental-orange" alt="Status: experimental">
  <a href="https://central.sonatype.com/artifact/dev.seungmin.nav3/nav3-uikit"><img src="https://img.shields.io/maven-central/v/dev.seungmin.nav3/nav3-uikit" alt="Maven Central"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4.20">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-1.12.1-4285F4" alt="Compose Multiplatform 1.12.1">
  <img src="https://img.shields.io/badge/platforms-iOS_|_Android_|_JVM-lightgrey" alt="Platforms: iOS, Android, JVM">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License: Apache 2.0"></a>
</p>

Rename `NavDisplay` to `NativeNavDisplay` and your iOS screens run in a real `UINavigationController`, with system swipe-back, large titles, and sheets. `NativeTabDisplay` adds a native `UITabBarController`. On Android and desktop, both fall back to `NavDisplay`.

```diff
- NavDisplay(
+ NativeNavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider = entryProvider { /* unchanged */ },
  )
```

[Install](#install) · [Quick start](#quick-start) · [Tabs](#tabs) · [Compatibility](#compatibility) · [Limitations](#limitations) · [How it works](docs/how-it-works.md)

## Features

- **Drop-in for `NavDisplay`.** `NativeNavDisplay` has both overloads (`backStack` and `entries`) and every parameter of `NavDisplay`. Decorators, `sceneStrategies`, `DialogSceneStrategy`, and back handlers keep working.
- **Native stack on iOS.** Each entry is its own `ComposeUIViewController` in a `UINavigationController`, so the back button, edge swipe-back, and the back button's history menu are UIKit's own.
- **Native tabs on iOS.** `NativeTabDisplay` shows a `UITabBarController` with a navigation stack per tab and tab badges. It accepts either the single back stack of Navigation 3's `commonui` recipe or one back stack per tab.
- **Sheets, titles, and bar buttons from metadata.** Entries opt into a large title, a sheet with medium or large detents, or a hidden navigation bar or tab bar. Screens can set the title and trailing buttons from their content.
- **Back stack stays the source of truth.** Native pops call `onBack`. If the app refuses a pop, the screen is put back.
- **iPhone Duo ready.** The tab bar and the back button move into the iPhone Duo vertical bar, laid out by UIKit itself.

<p align="center">
  <img src="docs/images/demo.gif" alt="A push, a sheet, a pop, and tab switches, each animated by UIKit" width="300">
</p>

## Install

The library is on Maven Central. Depend on it from `commonMain`:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.seungmin.nav3:nav3-uikit:0.1.0-dev01")
        }
    }
}
```

One coordinate serves Android, JVM, `iosArm64`, and `iosSimulatorArm64`. The library exposes Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui`) as an `api` dependency, so the app does not have to declare it. The tab display uses `UITab` and requires iOS 18 or later.

## Quick start

1. On iOS, create the root view controller with `NativeNavigationViewController`. Apps that use `NativeTabDisplay` need it for the tab bar to appear on an open iPhone Duo.

```kotlin
// iosMain
fun MainViewController(): UIViewController = NativeNavigationViewController { App() }
```

2. Replace `NavDisplay` with `NativeNavDisplay`, and add `NativeNavigation` metadata to the entries that should use the native navigation bar or a sheet.

```kotlin
NativeNavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider {
        entry<Home>(metadata = NativeNavigation.title("Home", large = true)) { HomeScreen() }
        entry<Detail>(metadata = { NativeNavigation.title("Detail ${it.id}") }) { DetailScreen(it.id) }
        entry<Settings>(metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large))) { SettingsSheet() }
        entry<Immersive>(metadata = NativeNavigation.navigationBarHidden()) { ImmersiveScreen() }
        entry<ConfirmDelete>(metadata = DialogSceneStrategy.dialog()) { ConfirmDeleteDialog() }
    },
    sceneStrategies = listOf(DialogSceneStrategy()),
)
```

Entries without `NativeNavigation` metadata draw their own top bar, exactly as under `NavDisplay`.

| Metadata | Effect on iOS |
|---|---|
| `NativeNavigation.title(text, large)` | Shows the native navigation bar with this title, optionally as a large title |
| `NativeNavigation.sheet(detents)` | Presents the entry as a sheet with `SheetDetent.Medium` and/or `SheetDetent.Large` |
| `NativeNavigation.navigationBarHidden()` | Hides the navigation bar for this entry |
| `NativeNavigation.tabBarHidden()` | Hides the tab bar while this entry is pushed above its tab's root |

### Set bar items from a screen

`NativeNavigationBarItems` sets the native bar's title and trailing buttons from inside the screen. `LocalNativeNavigationBar` is `true` where the native bar is available, so one screen can serve every platform:

```kotlin
if (LocalNativeNavigationBar.current) {
    NativeNavigationBarItems(
        title = note.title,
        trailing = listOf(NativeBarButton("Edit", systemImage = "pencil") { onEdit() }),
    )
} else {
    TopAppBar(title = { Text(note.title) }, actions = { /* ... */ })
}
```

## Tabs

`NavDisplay` has no tabs; an app's tab bar is its own UI next to it. To get a native tab bar, replace the app's tab bar and `NavDisplay` with one `NativeTabDisplay` call. Choose the overload that matches how the app already stores its navigation state.

**One back stack holding every tab**, as the `TopLevelBackStack` of Navigation 3's `commonui` recipe does. The selected tab is read from the back stack.

```kotlin
// e.g. [Home, Detail(1), Search, Detail(2)]
val topLevelBackStack = remember { TopLevelBackStack<NavKey>(Home) }

NativeTabDisplay(
    backStack = topLevelBackStack.backStack,
    tabs = listOf(
        NativeTabRoot(Home, "Home", icon = "house"),  // SF Symbol name
        NativeTabRoot(Search, "Search", icon = painterResource(Res.drawable.search)),  // tinted as a template image
    ),
    onTabSelected = { topLevelBackStack.addTopLevel(it) },
    onBack = { topLevelBackStack.removeLast() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider = entryProvider { /* entries for every tab */ },
)
```

**One back stack per tab**, as Navigation 3's `multiplestacks` recipe does. The app owns the selection.

```kotlin
val home = rememberNavBackStack(config, Home)
val search = rememberNavBackStack(config, SearchHome)
var selected by rememberSaveable { mutableIntStateOf(0) }

NativeTabDisplay(
    tabs = listOf(
        NativeTab(home, "Home", icon = "house"),
        NativeTab(search, "Search", icon = painterResource(Res.drawable.search)),
    ),
    selectedTab = selected,
    onTabSelected = { selected = it },
    onBack = { tab -> listOf(home, search)[tab].removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider = entryProvider { /* entries for every tab */ },
)
```

With one back stack per tab, every tab keeps its `rememberSaveable` state and ViewModels while it is hidden. An app that wants to keep its Compose tab bar can use `NativeNavDisplay` alone; the tab bar then stays a Compose view and does not move into the iPhone Duo vertical bar.

### Badges and a hidden tab bar

`badge` puts a badge on a tab, such as an unread count. On iOS it is the tab's `badgeValue`, so UIKit draws the badge and only its text is up to the app.

```kotlin
NativeTab(messages, "Messages", icon = "message", badge = unread.takeIf { it > 0 }?.toString())
```

A screen hides the tab bar from its own state with `NativeTabBarHidden`. The tab bar comes back when the state changes or the screen is popped. A screen that always hides the tab bar uses `NativeNavigation.tabBarHidden()` metadata instead, which also hides it during the push.

```kotlin
@Composable
fun DetailScreen() {
    var expanded by remember { mutableStateOf(false) }
    NativeTabBarHidden(hidden = expanded)
    // ...
}
```

## On Android and desktop

`NativeNavDisplay` passes every parameter to `NavDisplay`. `NativeTabDisplay` shows a `NavDisplay` above its `tabBar` slot, which defaults to a plain row of tab icons, titles, and badges; pass the app's own tab bar there. The slot is hidden while a screen calls `NativeTabBarHidden`. Back behaves as before: the single back stack overload follows the back stack exactly, and the per-tab overload returns to the first tab from the root of any other tab.

## Compatibility

| Dependency | Tested with |
|---|---|
| Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui`) | 1.1.2 |
| Compose Multiplatform | 1.12.1 |
| Kotlin | 2.4.20 |

The tested versions are the ones in `gradle/libs.versions.toml` (last checked 2026-10-16); update this table when that file changes. If the app declares Navigation 3 itself, Gradle uses the higher of the two versions.

- Navigation 3 1.1.0 is the lowest version that can work. It introduced `NavMetadataKey`, the `metadata { }` builder, and `SceneDecoratorStrategy`, which the public API uses. Navigation 3 1.2.0 and 1.3.0-alpha02 keep the signatures of every function the library calls.
- On iOS the library computes scenes with `rememberSceneState` itself instead of calling `NavDisplay`. A Navigation 3 release that changes how scenes or overlays are computed can therefore change iOS behavior without a compile error, and the iOS code also relies on Compose Multiplatform's partly experimental UIKit interop. After moving to a version outside the table, run the UI tests described in [CONTRIBUTING.md](CONTRIBUTING.md#ui-tests).

## Limitations

- Transitions are UIKit's. Shared element transitions, the transition parameters, and scenes other than overlays do not apply on iOS. An overlay scene that shows more than one entry has its entries pushed instead.
- Sheet and overlay entries must come after all pushed entries in the back stack; a pushed entry that follows them is presented modally too. A sheet on a hidden tab appears when that tab is selected.
- Tabs must be keys in the back stack. Tabs that one screen draws inside its own content are invisible to the display, so make each tab's root a key, or keep the Compose tab bar.
- The single back stack tab overload has no `entries` form, because `NavEntry` does not expose its key. Apps that build `entries` from several back stacks use the `NativeTab` overload.
- The single back stack must start with a tab's root key. For a sign-in flow before the tabs, pick the display by the root: `if (backStack.first() in tabKeys) NativeTabDisplay(...) else NativeNavDisplay(...)`.
- On iOS, back does not go from one tab's root to another tab, because UIKit has no back action at a tab's root. Android and desktop keep that `NavDisplay` behavior.

## Learn more

- [How it works on iOS](docs/how-it-works.md): how entries map to controllers, how native pops reach the back stack, tabs, and the iPhone Duo layout.
- [CONTRIBUTING.md](CONTRIBUTING.md): repository layout, the sample app, building, and the UI test suite.
- `sample/`: runnable single-stack and tab samples for iOS and desktop.

## License

```
Copyright 2026 Seungmin Yang

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
