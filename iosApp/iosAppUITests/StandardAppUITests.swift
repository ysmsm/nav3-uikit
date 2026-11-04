import XCTest

/// Shared helpers for the sample's UI tests. Every screen prints the back stack as
/// "backStack: ..." at the top.
class SampleUITestCase: XCTestCase {
    var app: XCUIApplication!

    /// The `NAV3_SAMPLE` value to launch with, or nil for the single-stack sample.
    var sample: String? { nil }

    /// Extra launch environment, such as `NAV3_LAYOUT`.
    var environment: [String: String] { [:] }

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        if let sample { app.launchEnvironment["NAV3_SAMPLE"] = sample }
        environment.forEach { app.launchEnvironment[$0.key] = $0.value }
        if let host = ProcessInfo.processInfo.environment["NAV3_HOST"] {
            app.launchEnvironment["NAV3_HOST"] = host
        }
        app.launch()
    }

    /// The container placement the sample reports for the host the tests run with.
    var interop: Bool { ProcessInfo.processInfo.environment["NAV3_HOST"] == "interop" }

    var expectedPlacement: String { interop ? "container=embedded" : "container=root" }

    func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    func tap(_ label: String) {
        let target = element(label)
        XCTAssertTrue(target.waitForExistence(timeout: 5), "missing \(label)")
        target.tap()
    }

    /// Passes when any element with `label` can be hit. Screens below the top one can show the
    /// same label: iOS 18 keeps a sheet covered by another sheet in the accessibility tree, and
    /// every screen of the sample prints the same back stack.
    func expectVisible(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let matches = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label))
        let deadline = Date().addingTimeInterval(5)
        repeat {
            if matches.allElementsBoundByIndex.contains(where: { $0.exists && $0.isHittable }) { return }
            usleep(200_000)
        } while Date() < deadline
        snapshot("failure")
        XCTFail("expected visible \(label)", file: file, line: line)
    }

    func expectBackStack(_ expected: String, file: StaticString = #filePath, line: UInt = #line) {
        expectVisible("backStack: \(expected)", file: file, line: line)
    }

    func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    var backButton: XCUIElement { app.buttons["BackButton"] }

    /// On iPhone Duo the back button moves from the navigation bar into the vertical bar.
    func tapBack() {
        if backButton.waitForExistence(timeout: 2) && backButton.isHittable {
            backButton.tap()
        } else {
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
    }

    /// A point at mid-height, `x` points from the left edge of the screen.
    func midHeight(x: CGFloat) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x, dy: app.frame.height / 2))
    }

    /// Where an edge swipe starts. A synthesized drag starts an edge swipe only when it begins
    /// within about 4 pt of the screen edge, and the limit differs by device: 3.9 pt misses on an
    /// iPhone 17e or iPhone 12 Pro. So this is 1 pt in, not a fraction of the width.
    var edgeSwipeStart: XCUICoordinate { midHeight(x: 1) }

    func edgeSwipeBack() {
        edgeSwipeStart.press(forDuration: 0.05, thenDragTo: midHeight(x: app.frame.width * 0.9))
    }

    /// Waits for UIKit transitions that the test cannot observe through labels to finish.
    func settle() {
        usleep(1_500_000)
    }

    func tabButton(_ name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", name)).firstMatch
    }

    /// Whether the tab bar marks `name` as selected. Waits, because the selection indicator
    /// animates, and checks every match, because the tab bar can expose a tab more than once.
    func isTabSelected(_ name: String) -> Bool {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", name))
        let deadline = Date().addingTimeInterval(3)
        repeat {
            if matches.allElementsBoundByIndex.contains(where: { $0.isSelected }) { return true }
            usleep(200_000)
        } while Date() < deadline
        print("tab \(name) not selected:\n" + matches.debugDescription)
        return false
    }

    func selectTab(_ name: String) {
        let button = tabButton(name)
        XCTAssertTrue(button.waitForExistence(timeout: 5), "missing tab \(name)")
        button.tap()
    }

    /// Asks the sample how many hosted controllers are alive, after the last transition and the
    /// releases that follow it have finished.
    func assertLiveControllers(_ expected: Int, file: StaticString = #filePath, line: UInt = #line) {
        sleep(2)
        tap("Live controllers: ?")
        sleep(3)
        let live = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH 'Live controllers: '")).firstMatch
        XCTAssertTrue(live.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertEqual(live.label, "Live controllers: \(expected)", "leaked controllers", file: file, line: line)
    }
}

/// Checks the single-stack sample against what a standard Navigation 3 app expects: the app may
/// refuse back, guard it with a back handler, show dialog scenes, and draw its own top bar.
final class StandardAppUITests: SampleUITestCase {
    private func openLab() {
        tap("Open lab")
        expectVisible("Open locked")
    }

    func testRefusedBackKeepsScreen() {
        openLab()
        tap("Open locked")
        expectVisible("Back is refused")

        tapBack()
        settle()
        expectVisible("Back is refused")
        XCTAssertFalse(element("Open locked").isHittable, "UIKit left the refused screen")

        edgeSwipeBack()
        settle()
        expectVisible("Back is refused")
        XCTAssertFalse(element("Open locked").isHittable, "UIKit left the refused screen")

        tap("Unlock")
        tapBack()
        expectVisible("Open locked")
        expectBackStack("Home > Lab")
    }

    func testBackHandlerReceivesNativeBack() {
        openLab()
        tap("Open editor")
        tap("Make changes")

        tapBack()
        expectVisible("Discard changes?")
        tap("Keep editing")

        edgeSwipeBack()
        expectVisible("Discard changes?")
        snapshot("discard-prompt")
        tap("Discard")
        expectVisible("Open editor")
        expectBackStack("Home > Lab")

        // Without unsaved changes the swipe pops as usual.
        tap("Open editor")
        expectVisible("No changes")
        edgeSwipeBack()
        expectVisible("Open editor")
    }

    func testDialogSceneShowsOverScreen() {
        openLab()
        tap("Open dialog")
        expectVisible("Close dialog")
        // The screen below stays in place under the dialog.
        XCTAssertTrue(element("Open locked").exists)
        snapshot("dialog")
        tap("Close dialog")
        expectVisible("Open dialog")

        // Tapping outside the dialog dismisses it through onBack, as NavDisplay's DialogScene does.
        tap("Open dialog")
        expectVisible("Close dialog")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)).tap()
        expectVisible("Open dialog")
        expectBackStack("Home > Lab")
    }

    func testScreenWithoutBarMetadataShowsNoNativeBar() {
        openLab()
        tap("Open plain")
        expectVisible("Native bar available")
        settle()
        XCTAssertFalse(backButton.exists && backButton.isHittable, "native bar shown without being asked for")
        snapshot("plain")
        tap("Back")
        expectVisible("Open plain")
    }

    func testBarItemsFromContent() {
        openLab()
        tap("Open toolbar")
        XCTAssertTrue(element("Toolbar 0").waitForExistence(timeout: 5))
        let add = app.buttons["Add"]
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        add.tap()
        expectVisible("Count: 1")
        XCTAssertTrue(element("Toolbar 1").waitForExistence(timeout: 5))
        snapshot("toolbar")
        tapBack()
        expectVisible("Open toolbar")
    }

    /// The bar buttons, back actions, and overlays must not keep popped controllers alive.
    func testLabControllersAreReleased() {
        openLab()
        for _ in 0..<2 {
            tap("Open toolbar")
            let add = app.buttons["Add"]
            XCTAssertTrue(add.waitForExistence(timeout: 5))
            add.tap()
            tapBack()
            expectVisible("Open toolbar")

            tap("Open dialog")
            tap("Close dialog")
            expectVisible("Open dialog")

            tap("Open editor")
            tap("Make changes")
            tapBack()
            tap("Discard")
            expectVisible("Open editor")
        }
        tapBack()
        expectBackStack("Home")
        assertLiveControllers(1)
    }
}

/// `NAV3_SAMPLE=swap`: a sign-in display that the tab display replaces, as many apps start.
final class RootSwapUITests: SampleUITestCase {
    override var sample: String? { "swap" }

    func testReplacingDisplayMovesTabsToRoot() {
        // A NativeNavDisplay stays in Compose, so Compose content around it stays visible.
        XCTAssertTrue(element("container=embedded").waitForExistence(timeout: 5))
        tap("Continue")
        expectBackStack("Home: Home")
        settle()
        XCTAssertTrue(element(expectedPlacement).waitForExistence(timeout: 5), "tab container not placed as expected")
        snapshot("after-sign-in")

        // The tabs keep working after the swap.
        tap("Open detail 1")
        expectBackStack("Home: Home > Detail(id=1)")
        tapBack()
        expectBackStack("Home: Home")
    }

    /// Each time the tab display leaves, its container leaves the root too, so after signing out
    /// only the Compose view is left there.
    func testSignedOutRootHoldsOnlyCompose() {
        for _ in 0..<3 {
            expectBackStack("SignIn")
            if !interop { expectVisible("root children=1") }
            tap("Continue")
            expectBackStack("Home: Home")
            settle()
            tap("Sign out")
        }
        expectBackStack("SignIn")
        settle()
        if !interop { expectVisible("root children=1") }
    }
}

/// `NAV3_LAYOUT=plain`: the single-stack sample called without a modifier, as a call site copied
/// from `NavDisplay` may be. The display still fills the screen.
final class DefaultSizeUITests: SampleUITestCase {
    override var environment: [String: String] { ["NAV3_LAYOUT": "plain"] }

    func testDisplayWithoutModifierShowsScreens() {
        expectBackStack("Home")
        tap("Open detail 1")
        expectBackStack("Home > Detail(id=1)")
        tapBack()
        expectBackStack("Home")
    }
}

/// `NAV3_LAYOUT=bar`: the single-stack sample above a Compose bar of the app's own. The bar stays
/// visible under either root controller.
final class ComposeBarUITests: SampleUITestCase {
    override var environment: [String: String] { ["NAV3_LAYOUT": "bar"] }

    func testComposeBarOutsideDisplayStaysVisible() {
        expectBackStack("Home")
        expectVisible("App bar")
        tap("Open detail 1")
        expectBackStack("Home > Detail(id=1)")
        expectVisible("App bar")
        snapshot("app-bar")
        tapBack()
        expectBackStack("Home")
    }
}

/// `NAV3_SAMPLE=restart`: every tab gets a new root when a flow finishes, sometimes while another
/// tab is selected in the same frame. Each tab's UINavigationController must then hold exactly
/// the screens of its back stack ("native=1,1").
final class TabRestartUITests: SampleUITestCase {
    override var sample: String? { "restart" }

    func testRestartFromTabRoot() {
        expectBackStack("First: TabRoot(session=0, tab=0)")
        expectVisible("native=1,1")
        tap("Restart on Second")
        expectBackStack("Second: TabRoot(session=1, tab=1)")
        expectVisible("native=1,1")
        tap("Restart on Second")
        expectBackStack("Second: TabRoot(session=2, tab=1)")
        expectVisible("native=1,1")
    }

    func testRestartFromOpenFlow() {
        tap("Open flow")
        expectBackStack("First: TabRoot(session=0, tab=0) > Detail(id=1)")
        tap("Finish on Second")
        expectBackStack("Second: TabRoot(session=1, tab=1)")
        expectVisible("native=1,1")
        tap("Open flow")
        expectBackStack("Second: TabRoot(session=1, tab=1) > Detail(id=1)")
        tap("Finish on First")
        expectBackStack("First: TabRoot(session=2, tab=0)")
        expectVisible("native=1,1")
    }
}
