import XCTest

/// Drives the tab sample (`NAV3_SAMPLE=tabs`): one Navigation 3 back stack per tab inside a
/// native UITabBarController. Every screen prints "backStack: <tab>: <stack>" at the top.
///
/// The tests find tab buttons and the back button by label and identifier rather than through
/// `app.tabBars` and `app.navigationBars`, because on iPhone Duo the system moves both into the
/// vertical bar.
final class TabNavigationUITests: SampleUITestCase {
    override var sample: String? { "tabs" }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    func testEachTabKeepsItsOwnStack() {
        expectBackStack("Home: Home")
        tap("Open detail 1")
        expectBackStack("Home: Home > Detail(id=1)")
        snapshot("home-detail")

        selectTab("Search")
        expectBackStack("Search: SearchHome")
        tap("Open detail 1")
        expectBackStack("Search: SearchHome > Detail(id=1)")
        tap("ViewModel taps: 0")
        expectVisible("ViewModel taps: 1")

        // Detail(1) is on both stacks, but each tab has its own ViewModel for it.
        selectTab("Home")
        expectBackStack("Home: Home > Detail(id=1)")
        expectVisible("ViewModel taps: 0")

        selectTab("Search")
        expectBackStack("Search: SearchHome > Detail(id=1)")
        expectVisible("ViewModel taps: 1")
        XCTAssertTrue(isTabSelected("Search"))
    }

    func testStateSurvivesTabSwitch() {
        tap("remember: 0")
        tap("rememberSaveable: 0")
        selectTab("Profile")
        expectBackStack("Profile: Profile")
        selectTab("Home")
        expectBackStack("Home: Home")
        expectVisible("remember: 1")
        expectVisible("rememberSaveable: 1")
    }

    func testBackInsideTab() {
        tap("Open detail 1")
        tap("Push detail 2")
        expectBackStack("Home: Home > Detail(id=1) > Detail(id=2)")
        snapshot("detail-2")
        edgeSwipeBack()
        expectBackStack("Home: Home > Detail(id=1)")
        tapBack()
        expectBackStack("Home: Home")
    }

    /// Tapping the selected tab again pops its navigation stack to the root, which UIKit does on
    /// its own; the back stack has to follow.
    func testReselectingTabPopsToRoot() {
        tap("Open detail 1")
        tap("Push detail 2")
        expectBackStack("Home: Home > Detail(id=1) > Detail(id=2)")
        selectTab("Home")
        expectBackStack("Home: Home")
    }

    func testProgrammaticTabSelection() {
        tap("Go to Profile tab")
        expectBackStack("Profile: Profile")
        XCTAssertTrue(isTabSelected("Profile"))
        tap("Go to Home tab")
        expectBackStack("Home: Home")
        XCTAssertTrue(isTabSelected("Home"))
    }

    func testPushIntoHiddenTab() {
        tap("Push detail 7 in Search tab")
        expectBackStack("Home: Home")
        selectTab("Search")
        expectBackStack("Search: SearchHome > Detail(id=7)")
        XCTAssertTrue(app.navigationBars["Detail 7"].waitForExistence(timeout: 5))
        tapBack()
        expectBackStack("Search: SearchHome")
    }

    /// A sheet added to a tab that is not on screen is presented once that tab appears.
    func testSheetInHiddenTab() {
        tap("Open sheet in Search tab")
        expectBackStack("Home: Home")
        selectTab("Search")
        expectBackStack("Search: SearchHome > Settings")
        tap("Close sheet")
        expectBackStack("Search: SearchHome")
    }

    func testSheetInsideTab() {
        tap("Open settings sheet")
        expectBackStack("Home: Home > Settings")
        snapshot("settings-sheet")
        tap("Close sheet")
        expectBackStack("Home: Home")
    }

    func testNavigationBarHiddenInsideTab() {
        selectTab("Profile")
        tap("Open immersive")
        expectBackStack("Profile: Profile > Immersive")
        snapshot("immersive")
        tap("Back")
        expectBackStack("Profile: Profile")
    }

    /// Screens popped inside any tab must be released; only the three tab roots stay alive.
    func testPoppedControllersAreReleased() {
        tap("Open detail 1")
        tap("Push detail 2")
        tap("Pop to root")
        expectBackStack("Home: Home")
        selectTab("Search")
        tap("Open detail 1")
        tap("Push detail 2")
        tapBack()
        tapBack()
        expectBackStack("Search: SearchHome")
        selectTab("Home")
        tap("Open settings sheet")
        tap("Close sheet")
        expectBackStack("Home: Home")
        assertLiveControllers(3)
    }

    func testContainerPlacement() {
        XCTAssertTrue(element(expectedPlacement).waitForExistence(timeout: 5))
    }

    func testTabBarHiddenWhenPushed() {
        tap("Open focus")
        expectVisible("Tab bar hidden here")
        settle()
        let search = tabButton("Search")
        XCTAssertFalse(search.exists && search.isHittable, "tab bar still shown")
        snapshot("focus")
        tapBack()
        expectBackStack("Home: Home")
        XCTAssertTrue(tabButton("Search").waitForExistence(timeout: 5))
        XCTAssertTrue(tabButton("Search").isHittable)
    }

    func testTabBadge() {
        selectTab("Search")
        expectBackStack("Search: SearchHome")
        tap("Add Home badge")
        tap("Add Home badge")
        let home = tabButton("Home")
        XCTAssertTrue(waitForValue(of: home) { $0.contains("2") }, "no badge: \(home.debugDescription)")
        snapshot("badge")
        tap("Clear Home badge")
        XCTAssertTrue(waitForValue(of: home) { !$0.contains("2") }, "badge left: \(home.debugDescription)")
    }

    /// A tab's root screen hides the tab bar from its own state.
    func testTabBarHiddenFromStateAtRoot() {
        selectTab("Profile")
        expectBackStack("Profile: Profile")
        tap("Expand")
        expectTabBar(shown: false, "tab bar still shown while expanded")
        snapshot("expanded")
        tap("Collapse")
        expectTabBar(shown: true, "tab bar not back after collapsing")
    }

    func testTabBarComesBackWhenHidingScreenIsPopped() {
        selectTab("Profile")
        tap("Open panel")
        expectBackStack("Profile: Profile > Panel")
        tap("Expand")
        expectTabBar(shown: false, "tab bar still shown while expanded")
        tapBack()
        expectBackStack("Profile: Profile")
        expectTabBar(shown: true, "tab bar not back after the pop")
    }

    /// A screen with `tabBarHidden()` metadata pushed over a screen that hides the tab bar from its
    /// state, then popped, leaves the tab bar hidden for that screen.
    func testTabBarHiddenFromStateUnderMetadataScreen() {
        selectTab("Profile")
        tap("Open panel")
        tap("Expand")
        expectTabBar(shown: false, "tab bar still shown while expanded")
        tap("Open focus")
        expectVisible("Tab bar hidden here")
        expectTabBar(shown: false, "tab bar shown on the focus screen")
        tapBack()
        expectBackStack("Profile: Profile > Panel")
        expectTabBar(shown: false, "tab bar shown over the expanded screen")
        tap("Collapse")
        expectTabBar(shown: true, "tab bar not back after collapsing")
    }

    /// Only the selected tab's screen decides: an expanded screen in a hidden tab leaves the tab
    /// bar to the tab on screen.
    func testTabBarHiddenFromStateFollowsSelectedTab() {
        selectTab("Profile")
        tap("Expand")
        expectTabBar(shown: false, "tab bar still shown while expanded")
        tap("Go to Home tab")
        expectBackStack("Home: Home")
        expectTabBar(shown: true, "Home tab has no tab bar")
        tap("Go to Profile tab")
        expectBackStack("Profile: Profile")
        expectTabBar(shown: false, "tab bar shown while the Profile tab is expanded")
    }

    /// Waits for UIKit's transitions, then for the tab bar to be usable or not, judged by the
    /// Search tab button.
    private func expectTabBar(shown: Bool, _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        settle()
        let search = tabButton("Search")
        let deadline = Date().addingTimeInterval(3)
        repeat {
            if (search.exists && search.isHittable) == shown { return }
            usleep(200_000)
        } while Date() < deadline
        snapshot("failure")
        XCTFail(message, file: file, line: line)
    }

    private func waitForValue(of element: XCUIElement, _ matches: (String) -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(5)
        repeat {
            if element.exists, matches(element.value as? String ?? "") { return true }
            usleep(200_000)
        } while Date() < deadline
        return false
    }
}
