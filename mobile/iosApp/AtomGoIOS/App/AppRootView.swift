import SwiftUI

struct AppRootView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        switch viewModel.route {
        case .login:
            ContentView(viewModel: viewModel.loginViewModel)

        case let .clientHome(session):
            ClientHomeContainerView(
                session: session,
                apiService: viewModel.apiService,
                onLogout: viewModel.logout
            )

        case let .adminHome(session):
            AdminHomeContainerView(
                session: session,
                apiService: viewModel.apiService,
                onLogout: viewModel.logout
            )
        }
    }
}

private struct ClientHomeContainerView: View {
    @StateObject private var viewModel: ClientHomeViewModel
    let onLogout: () -> Void

    init(session: AuthSession, apiService: BackendServicing, onLogout: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: ClientHomeViewModel(session: session, apiService: apiService))
        self.onLogout = onLogout
    }

    var body: some View {
        ClientHomeView(viewModel: viewModel, onLogout: onLogout)
    }
}

private struct AdminHomeContainerView: View {
    @StateObject private var viewModel: AdminHomeViewModel
    let onLogout: () -> Void

    init(session: AuthSession, apiService: BackendServicing, onLogout: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AdminHomeViewModel(session: session, apiService: apiService))
        self.onLogout = onLogout
    }

    var body: some View {
        AdminHomeView(viewModel: viewModel, onLogout: onLogout)
    }
}
