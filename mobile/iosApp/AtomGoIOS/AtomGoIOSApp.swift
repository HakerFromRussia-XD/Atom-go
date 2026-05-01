import SwiftUI

@main
struct AtomGoIOSApp: App {
    @StateObject private var appViewModel = AppViewModel()

    var body: some Scene {
        WindowGroup {
            AppRootView(viewModel: appViewModel)
                .preferredColorScheme(.light)
        }
    }
}
