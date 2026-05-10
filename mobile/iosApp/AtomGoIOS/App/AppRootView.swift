import SwiftUI

struct AppRootView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        switch viewModel.route {
        case .launching:
            ZStack {
                AppDesign.pageBackground.ignoresSafeArea()
                ProgressView("Запуск приложения...")
                    .tint(AppDesign.accent)
            }

        case .login:
            ContentView(viewModel: viewModel.loginViewModel)

        case let .clientHome(session):
            ClientHomeContainerView(
                session: session,
                apiService: viewModel.apiService,
                onLogout: viewModel.logout
            )

        case let .adminHome(session, startupRentalDeepLink):
            AdminHomeContainerView(
                session: session,
                apiService: viewModel.apiService,
                startupRentalDeepLink: startupRentalDeepLink,
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
    let startupRentalDeepLink: AdminStartupRentalDeepLink?
    let onLogout: () -> Void

    init(
        session: AuthSession,
        apiService: BackendServicing,
        startupRentalDeepLink: AdminStartupRentalDeepLink?,
        onLogout: @escaping () -> Void
    ) {
        _viewModel = StateObject(wrappedValue: AdminHomeViewModel(session: session, apiService: apiService))
        self.startupRentalDeepLink = startupRentalDeepLink
        self.onLogout = onLogout
    }

    var body: some View {
        AdminHomeView(
            viewModel: viewModel,
            startupRentalDeepLink: startupRentalDeepLink,
            onLogout: onLogout
        )
    }
}
