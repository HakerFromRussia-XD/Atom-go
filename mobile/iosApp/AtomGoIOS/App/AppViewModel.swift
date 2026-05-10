import Foundation

struct AdminStartupRentalDeepLink: Equatable {
    let clientId: String
    let rentalId: String
}

enum AppRoute {
    case launching
    case login
    case clientHome(AuthSession)
    case adminHome(AuthSession, startupRentalDeepLink: AdminStartupRentalDeepLink?)
}

@MainActor
final class AppViewModel: ObservableObject {
    private enum StartupFastEntryConfig {
        // TEST FLAG:
        // `true`  -> сразу после запуска приложения выполняем автологин админа
        //            и открываем экран выбранной аренды для быстрой UI-проверки.
        // `false` -> стандартный запуск с экраном логина.
        static let isEnabled = true

        static let adminLogin = "admin"
        static let adminPassword = "admin123"

        // Целевая аренда для быстрого входа.
        // Приоритет поиска: логин клиента -> имя + модель -> первая доступная аренда.
        static let targetClientLogin = "client1"
        static let targetClientName = "Иван Петров"
        static let targetBikeModel = "Ninebot E-Bike Pro"
    }

    @Published private(set) var route: AppRoute =
        StartupFastEntryConfig.isEnabled ? .launching : .login

    let apiService: BackendServicing
    let loginViewModel: LoginViewModel

    init(apiService: BackendServicing = LazyBackendService()) {
        self.apiService = apiService
        self.loginViewModel = LoginViewModel(apiService: apiService)

        loginViewModel.onAuthenticated = { [weak self] session in
            self?.routeAfterLogin(session)
        }

        if StartupFastEntryConfig.isEnabled {
            Task {
                await runStartupFastEntry()
            }
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
            route = .adminHome(session, startupRentalDeepLink: nil)
        }
    }

    private func runStartupFastEntry() async {
        do {
            let session = try await apiService.login(
                login: StartupFastEntryConfig.adminLogin,
                password: StartupFastEntryConfig.adminPassword
            )
            guard session.role == .admin else {
                route = .login
                return
            }

            let clients = try await apiService.fetchAdminRents(accessToken: session.accessToken)
            let startupRentalDeepLink = resolveStartupRentalDeepLink(in: clients)
            route = .adminHome(session, startupRentalDeepLink: startupRentalDeepLink)
        } catch {
            route = .login
        }
    }

    private func resolveStartupRentalDeepLink(
        in clients: [AdminClientSummaryResponse]
    ) -> AdminStartupRentalDeepLink? {
        let targetByLogin = clients.first { client in
            guard let rentalId = client.rentalId, !rentalId.isEmpty else { return false }
            guard let login = client.clientLogin else { return false }
            return login.caseInsensitiveCompare(StartupFastEntryConfig.targetClientLogin) == .orderedSame
        }

        let targetByNameAndBike = clients.first { client in
            guard let rentalId = client.rentalId, !rentalId.isEmpty else { return false }
            return client.fullName == StartupFastEntryConfig.targetClientName
                && client.bikeModel == StartupFastEntryConfig.targetBikeModel
        }

        let fallback = clients.first { client in
            guard let rentalId = client.rentalId else { return false }
            return !rentalId.isEmpty
        }

        guard
            let resolved = targetByLogin ?? targetByNameAndBike ?? fallback,
            let rentalId = resolved.rentalId
        else {
            return nil
        }

        return AdminStartupRentalDeepLink(clientId: resolved.clientId, rentalId: rentalId)
    }
}
