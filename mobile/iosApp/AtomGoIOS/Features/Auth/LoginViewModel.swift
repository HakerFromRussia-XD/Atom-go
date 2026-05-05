import Foundation

enum LoginStatusKind {
    case idle
    case info
    case success
    case error
}

@MainActor
final class LoginViewModel: ObservableObject {
    static let waitingStatusText = "Статус: ожидание"
    private static let startupHealthCheckAttempts = 3
    private static let startupHealthCheckRetryDelayNs: UInt64 = 300_000_000

    @Published var login: String
    @Published var password: String
    @Published var statusText = waitingStatusText
    @Published var statusKind: LoginStatusKind = .idle
    @Published var isLoading = false

    var onAuthenticated: ((AuthSession) -> Void)?

    private let apiService: BackendServicing
    private var reachabilityTask: Task<Void, Never>?

    init(apiService: BackendServicing) {
        self.apiService = apiService
        self.login = ProcessInfo.processInfo.environment["ATOMGO_TEST_LOGIN"] ?? "client1"
        self.password = ProcessInfo.processInfo.environment["ATOMGO_TEST_PASSWORD"] ?? "client123"
        checkServerOnStart()
    }

    func signIn() {
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedLogin.isEmpty || password.isEmpty {
            statusText = "Статус: введите логин и пароль"
            statusKind = .info
            return
        }

        statusText = "Статус: выполняю вход..."
        statusKind = .info
        isLoading = true

        Task {
            do {
                let session = try await apiService.login(login: normalizedLogin, password: password)
                statusText = "Статус: вход выполнен, роль: \(session.role.rawValue)\nToken: \(String(session.accessToken.prefix(12)))..."
                statusKind = .success
                onAuthenticated?(session)
            } catch {
                statusText = "Статус: ошибка входа: \(error.localizedDescription)"
                statusKind = .error
            }
            isLoading = false
        }
    }

    func fillClientCredentials() {
        login = "client1"
        password = "client123"
    }

    func fillAdminCredentials() {
        login = "admin"
        password = "admin123"
    }

    func resetForNextLogin() {
        reachabilityTask?.cancel()
        login = "client1"
        password = "client123"
        statusText = Self.waitingStatusText
        statusKind = .idle
        isLoading = false
        checkServerOnStart()
    }

    private func checkServerOnStart() {
        reachabilityTask?.cancel()
        reachabilityTask = Task.detached(priority: .utility) { [weak self] in
            try? await Task.sleep(nanoseconds: 120_000_000)
            guard !Task.isCancelled else { return }
            let isReachable = await BackendReachabilityPolicy.isReachableWithRetries(
                maxAttempts: Self.startupHealthCheckAttempts,
                retryDelayNanoseconds: Self.startupHealthCheckRetryDelayNs,
                probe: {
                    await BackendService.firstReachableBaseUrlQuick() != nil
                }
            )
            await MainActor.run {
                guard let self else { return }
                self.reachabilityTask = nil
                guard !isReachable else { return }
                let attemptedUrls = BackendService.configuredBaseUrls.joined(separator: ", ")
                self.statusText = "Статус: сервер недоступен. Проверьте backend и сеть iPhone↔Mac.\nПроверенные адреса: \(attemptedUrls)"
                self.statusKind = .error
                self.isLoading = false
            }
        }
    }
}
