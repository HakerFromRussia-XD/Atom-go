import Foundation

public enum BackendReachabilityPolicy {
    public static func isReachableWithRetries(
        maxAttempts: Int,
        retryDelayNanoseconds: UInt64,
        probe: () async -> Bool,
        sleep: (UInt64) async -> Void = { nanoseconds in
            try? await Task.sleep(nanoseconds: nanoseconds)
        }
    ) async -> Bool {
        guard maxAttempts > 0 else { return false }

        for attempt in 1 ... maxAttempts {
            if await probe() {
                // Immediate success: do not execute extra retries.
                return true
            }

            if attempt < maxAttempts {
                await sleep(retryDelayNanoseconds)
            }
        }

        return false
    }
}
