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
    private static let defaultLogin = ""
    private static let defaultPassword = ""
    
    private static let defaultClientSelfEmployedLogin = "1"
    private static let defaultClientPassword = "2"
    
    private static let defaultClientIpLogin = "ip.ui.54fz"
    private static let defaultClientIpPassword = "client123"
    private static let rememberMeKey = "login.rememberMe.enabled"
    private static let rememberedLoginKey = "login.remembered.value"
    private static let rememberedPasswordKey = "login.remembered.password"

    @Published var login: String
    @Published var password: String
    @Published var rememberMe = false
    @Published var statusText = waitingStatusText
    @Published var statusKind: LoginStatusKind = .idle
    @Published var isLoading = false

    var onAuthenticated: ((AuthSession) -> Void)?

    private let apiService: BackendServicing
    private var reachabilityTask: Task<Void, Never>?
    private let defaults: UserDefaults

    init(apiService: BackendServicing, defaults: UserDefaults = .standard) {
        self.apiService = apiService
        self.defaults = defaults

        let envLogin = ProcessInfo.processInfo.environment["ATOMGO_TEST_LOGIN"]
        let envPassword = ProcessInfo.processInfo.environment["ATOMGO_TEST_PASSWORD"]
        let shouldRemember = defaults.bool(forKey: Self.rememberMeKey)
        let rememberedLogin = defaults.string(forKey: Self.rememberedLoginKey)
        let rememberedPassword = defaults.string(forKey: Self.rememberedPasswordKey)

        self.rememberMe = shouldRemember
        self.login = envLogin ?? (shouldRemember ? (rememberedLogin ?? Self.defaultLogin) : Self.defaultLogin)
        self.password = envPassword ?? (shouldRemember ? (rememberedPassword ?? Self.defaultPassword) : Self.defaultPassword)
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
                persistCredentialsIfNeeded(login: normalizedLogin, password: password)
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

    func fillClientSelfEmployedCredentials() {
        login = Self.defaultClientSelfEmployedLogin
        password = Self.defaultClientPassword
    }

    func fillClientIpCredentials() {
        login = Self.defaultClientIpLogin
        password = Self.defaultClientIpPassword
    }

    func fillAdminCredentials() {
        login = "admin"
        password = "admin123"
    }

    func fillAdminIpCredentials() {
        login = "admin_ip"
        password = "adminip123"
    }

    func resetForNextLogin() {
        reachabilityTask?.cancel()
        if rememberMe {
            login = defaults.string(forKey: Self.rememberedLoginKey) ?? Self.defaultLogin
            password = defaults.string(forKey: Self.rememberedPasswordKey) ?? Self.defaultPassword
        } else {
            login = Self.defaultLogin
            password = Self.defaultPassword
        }
        statusText = Self.waitingStatusText
        statusKind = .idle
        isLoading = false
        checkServerOnStart()
    }

    func setRememberMe(_ enabled: Bool) {
        rememberMe = enabled
        defaults.set(enabled, forKey: Self.rememberMeKey)
        if !enabled {
            defaults.removeObject(forKey: Self.rememberedLoginKey)
            defaults.removeObject(forKey: Self.rememberedPasswordKey)
        }
    }

    private func persistCredentialsIfNeeded(login: String, password: String) {
        if rememberMe {
            defaults.set(true, forKey: Self.rememberMeKey)
            defaults.set(login, forKey: Self.rememberedLoginKey)
            defaults.set(password, forKey: Self.rememberedPasswordKey)
        } else {
            defaults.set(false, forKey: Self.rememberMeKey)
            defaults.removeObject(forKey: Self.rememberedLoginKey)
            defaults.removeObject(forKey: Self.rememberedPasswordKey)
        }
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
