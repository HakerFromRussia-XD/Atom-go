import Foundation

enum AppRoute {
    case login
    case clientHome(AuthSession)
    case adminHome(AuthSession)
}

@MainActor
final class AppViewModel: ObservableObject {
    @Published private(set) var route: AppRoute = .login

    let apiService: BackendServicing
    let loginViewModel: LoginViewModel

    init(apiService: BackendServicing = LazyBackendService()) {
        self.apiService = apiService
        self.loginViewModel = LoginViewModel(apiService: apiService)

        loginViewModel.onAuthenticated = { [weak self] session in
            self?.routeAfterLogin(session)
        }
    }

    func logout() {
        route = .login
        loginViewModel.resetForNextLogin()
    }

    private func routeAfterLogin(_ session: AuthSession) {
        switch session.role {
        case .client:
            route = .clientHome(session)
        case .admin:
            route = .adminHome(session)
        }
    }
}
