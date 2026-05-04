import XCTest
import AtomGoIOS

final class BackendReachabilityPolicyTests: XCTestCase {
    func testReachabilitySucceedsOnFirstAttemptAndStopsImmediately() async {
        var probeCalls = 0
        var sleepCalls = 0

        let result = await BackendReachabilityPolicy.isReachableWithRetries(
            maxAttempts: 3,
            retryDelayNanoseconds: 1,
            probe: {
                probeCalls += 1
                return true
            },
            sleep: { _ in
                sleepCalls += 1
            }
        )

        XCTAssertTrue(result)
        XCTAssertEqual(probeCalls, 1, "Should stop after first successful probe")
        XCTAssertEqual(sleepCalls, 0, "Should not sleep when first probe succeeds")
    }

    func testReachabilitySucceedsOnSecondAttemptAndDoesSingleRetry() async {
        var probeCalls = 0
        var sleepCalls = 0

        let result = await BackendReachabilityPolicy.isReachableWithRetries(
            maxAttempts: 3,
            retryDelayNanoseconds: 1,
            probe: {
                probeCalls += 1
                return probeCalls == 2
            },
            sleep: { _ in
                sleepCalls += 1
            }
        )

        XCTAssertTrue(result)
        XCTAssertEqual(probeCalls, 2, "Should stop immediately after second successful probe")
        XCTAssertEqual(sleepCalls, 1, "Should sleep only between failed attempts")
    }

    func testReachabilityFailsAfterThreeAttempts() async {
        var probeCalls = 0
        var sleepCalls = 0

        let result = await BackendReachabilityPolicy.isReachableWithRetries(
            maxAttempts: 3,
            retryDelayNanoseconds: 1,
            probe: {
                probeCalls += 1
                return false
            },
            sleep: { _ in
                sleepCalls += 1
            }
        )

        XCTAssertFalse(result)
        XCTAssertEqual(probeCalls, 3)
        XCTAssertEqual(sleepCalls, 2)
    }
}
