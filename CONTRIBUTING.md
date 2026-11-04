# Contributing

## Repository layout

- `nav3-uikit/` is the library. It targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`. Android and JVM share the `nonIos` source set, which delegates to `NavDisplay`.
- `sample/` is the sample app. The `NAV3_SAMPLE` launch environment variable picks the sample; without it, the single-stack sample starts.

  | `NAV3_SAMPLE` | Sample |
  |---|---|
  | `tabs` | The `NativeTab` tab sample, one back stack per tab |
  | `swap` | A sign-in display that the tab sample replaces |
  | `restart` | Two tabs that get new roots when a flow finishes |
  | `stack-tabs` | The `commonui` recipe's `TopLevelBackStack` with three tabs |
  | `showcase` | A travel app with realistic content, used for the README images. `NAV3_SHOWCASE` opens it on a pushed screen (`trip`), a sheet (`sheet`), or another tab (`saved`, `profile`); `NAV3_SHOWCASE=tour` plays the push, sheet, pop, and tab sequence of `docs/images/demo.gif` |
  | `swiftui-tabs`, `uikit-tabs` | A SwiftUI `TabView` and a plain `UITabBarController` without Compose, for comparing the system layout |

  In the single-stack sample, `NAV3_LAYOUT=plain` calls the display without a modifier and `NAV3_LAYOUT=bar` places it above a Compose bar of the app's own. `NAV3_HOST=interop` uses a plain `ComposeUIViewController` as the root, which embeds the native containers in Compose.
- `iosApp/` is the iOS app shell and the XCUITest target. The Xcode project is generated from `project.yml` with XcodeGen.

## Build

Building needs JDK 21, the Android SDK, Xcode, and XcodeGen. The Kotlin, Compose Multiplatform, and Navigation 3 versions are in `gradle/libs.versions.toml`. The iPhone Duo tests need the iOS 27.1 simulator runtime.

```sh
./gradlew :nav3-uikit:compileKotlinIosSimulatorArm64 :nav3-uikit:compileKotlinJvm :nav3-uikit:compileAndroidMain
./gradlew :nav3-uikit:jvmTest          # unit tests of how a single back stack is split into tabs
./gradlew :nav3-uikit:checkKotlinAbi   # fails when the public API differs from nav3-uikit/api
./gradlew :nav3-uikit:updateKotlinAbi  # rewrites nav3-uikit/api after an intended API change
./gradlew :nav3-uikit:publishToMavenLocal
```

`publishToMavenLocal` publishes `dev.seungmin.nav3:nav3-uikit` with Android, JVM, and iOS variants under one coordinate, so Gradle picks the variant for each consumer. The version is the `-SNAPSHOT` default in `nav3-uikit/build.gradle.kts` unless the `nav3.version` Gradle property sets another. Without a signing key the local publication is unsigned.

## UI tests

Generate the Xcode project, then run the tests on a regular iPhone simulator and on an iPhone Duo simulator. Replace the simulator names with ones from `xcrun simctl list devices`.

```sh
(cd iosApp && xcodegen generate)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=<iPhone simulator>' test
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=<iPhone Duo simulator>' test
```

The test classes and what each covers:

- `NativeNavigationUITests` drives the single-stack sample and checks the `backStack: ...` label at the top of each screen.
- `TabNavigationUITests` drives the tab sample: tab switching, per-tab state, reselecting a tab, programmatic selection, pushing into a hidden tab, sheets, a screen that hides the tab bar, where the tab container sits, and controller release.
- `StandardAppUITests` drives the single-stack sample's Lab screen: a back the app refuses, a `NavigationBackHandler` that receives native back, a `DialogSceneStrategy` dialog, a screen without native bar metadata, bar items set from content, and controller release.
- `RootSwapUITests` replaces the sign-in display with the tab display, checks where each container ends up, and signs out and in again to check that the root holds no container of a display that left.
- `DefaultSizeUITests` and `ComposeBarUITests` run the single-stack sample without a modifier and above a Compose bar.
- `TabRestartUITests` gives every tab a new root, with and without selecting another tab in the same frame, and checks that each tab's `UINavigationController` holds as many screens as its back stack.
- `StackTabsUITests` drives the single back stack tab sample: each tab's part of the back stack, selecting a tab that is not in the back stack, swipe-back, reselecting a tab, programmatic selection, a sheet, a screen that hides the tab bar, and controller release.
- `VerticalBarUITests` reads the vertical bar edge each screen reports and checks that the tab buttons and the back button sit on that edge and that the Compose content stays clear of them. Where the device reports no vertical bar, it checks that the tab bar is at the bottom.

Two environment variables change what the tests run. `xcodebuild` passes a variable prefixed with `TEST_RUNNER_` to the test runner without the prefix.

- `TEST_RUNNER_NAV3_DECORATORS` set to `none`, `saveable`, or `viewmodel` changes which decorators the single-stack sample uses.
- `TEST_RUNNER_NAV3_HOST=interop` runs the tests with the containers embedded in Compose.

The tests find the back button by its `BackButton` identifier and the tabs by label, not through `app.navigationBars` or `app.tabBars`, because on iPhone Duo both move into the vertical bar.

### Testing on iPhone Duo

A new iPhone Duo simulator starts closed and shows the outer display, so the commands above cover the closed device. Neither `simctl` nor XCUITest can open the device, so open it by hand before running the tests on the inner display. The outer display does not rotate, so `testTabBarPlacementLandscape` skips itself there.

Do not reach the inner display by toggling display power with `xcrun simctl io <udid> screenConfig`. A simulator in that state stops accepting edge swipe gestures, even in the single-stack sample, so swipe test failures there do not point to a bug in the library.

## Memory rule for iOS code

Kotlin/Native's garbage collector cannot collect reference cycles that pass through Objective-C objects. Nothing reachable from a hosted controller's content lambda, a `UITab`'s view controller provider, or a UIKit delegate may hold a strong reference to `EntryHost` or a coordinator. Breaking this rule leaks popped screens. `testPoppedControllersAreReleased` in the UI test classes checks it.

## Pull requests

Fork the repository, make the change on a branch, and open a pull request against `main`. Open an issue first for a change to the public API or to behavior that apps rely on, so the approach can be agreed before the work. A bug fix should come with a test that fails without it, where the bug can be tested.

CI compiles the library for every target, runs `jvmTest`, and runs `checkKotlinAbi`. A pull request that changes the public API commits the new dump from `updateKotlinAbi`, so the change shows up in review. CI does not run the UI tests, because they take about 30 minutes. For a change to the iOS code, run them as described in [UI tests](#ui-tests) on a regular iPhone simulator and mention the result in the pull request; the maintainer can also run the [UI tests](https://github.com/ysmsm/nav3-uikit/actions/workflows/ui-tests.yml) workflow on GitHub.

A change that users will notice adds a line under **Unreleased** in `CHANGELOG.md`. Pull requests are squash-merged, so the pull request title becomes the commit message.
