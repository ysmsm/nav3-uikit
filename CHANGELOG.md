# Changelog

Every user-visible change to `dev.seungmin.nav3:nav3-uikit` is recorded here, in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. A pull request with such a change adds a line under **Unreleased**; a release moves those lines under its version. The release workflow publishes a version's section as its GitHub release notes.

## [Unreleased]

## [0.1.0-dev01] - 2026-10-17

The first public build on Maven Central. The API is experimental and may change in any `0.x` release.

### Added

- `NativeNavDisplay`, a drop-in replacement for `NavDisplay` that runs iOS entries in a `UINavigationController` with system swipe-back, large titles, and sheets.
- `NativeTabDisplay`, which shows a native `UITabBarController`, with overloads for one back stack per tab (`NativeTab`) and for a single back stack split into tabs (`NativeTabRoot`).
- `NativeNavigation` entry metadata: `title`, `sheet` with `SheetDetent`, `navigationBarHidden`, and `tabBarHidden`.
- `NativeNavigationBarItems` and `NativeBarButton` for setting the native bar's title and trailing buttons from inside a screen, and `LocalNativeNavigationBar` for screens that also run where the native bar is not available.
- `NativeNavigationViewController`, the iOS root view controller, which the tab bar needs on an open iPhone Duo.
- On Android and desktop, `NativeNavDisplay` and `NativeTabDisplay` fall back to `NavDisplay`.

[Unreleased]: https://github.com/ysmsm/nav3-uikit/compare/v0.1.0-dev01...HEAD
[0.1.0-dev01]: https://github.com/ysmsm/nav3-uikit/releases/tag/v0.1.0-dev01
