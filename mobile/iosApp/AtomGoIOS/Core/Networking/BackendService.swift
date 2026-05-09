import Foundation
import shared

private enum BackendRuntimeConfig {
    private static let simulatorKey = "BackendBaseURLSimulator"
    private static let deviceKey = "BackendBaseURLDevice"
    private static let deviceHostnameKey = "BackendBaseURLDeviceHostname"
    private static let envOverrideKey = "ATOMGO_BACKEND_URL"
    private static let simulatorFallback = "http://127.0.0.1:8080/api/v1"
    private static let deviceIPv4Fallback = "http://192.168.1.234:8080/api/v1"
    private static let deviceHostnameFallback = "http://MacBook-Pro-2.local:8080/api/v1"

    static var baseUrl: String {
        candidateBaseUrls.first ?? simulatorFallback
    }

    static var candidateBaseUrls: [String] {
        if let envValue = ProcessInfo.processInfo.environment[envOverrideKey],
           let normalized = normalize(envValue) {
            return [normalized]
        }

        #if targetEnvironment(simulator)
        let primary = normalize(Bundle.main.object(forInfoDictionaryKey: simulatorKey) as? String) ?? simulatorFallback
        return [primary]
        #else
        var urls: [String] = []
        if let host = normalize(Bundle.main.object(forInfoDictionaryKey: deviceHostnameKey) as? String) {
            urls.append(host)
        }
        if let ip = normalize(Bundle.main.object(forInfoDictionaryKey: deviceKey) as? String) {
            urls.append(ip)
        }
        urls.append(deviceHostnameFallback)
        urls.append(deviceIPv4Fallback)

        var unique: [String] = []
        for value in urls where !unique.contains(value) {
            unique.append(value)
        }
        return unique
        #endif
    }

    private static func normalize(_ rawValue: String?) -> String? {
        guard let rawValue else { return nil }
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.hasSuffix("/") {
            return String(trimmed.dropLast())
        }
        return trimmed
    }
}

private final class ContinuationResumeGate: @unchecked Sendable {
    private let lock = NSLock()
    private var didResume = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !didResume else { return false }
        didResume = true
        return true
    }
}

private struct NativeLoginRequest: Encodable {
    let login: String
    let password: String
}

private struct NativeCreatePaymentRequest: Encodable {
    let paymentType: String

    enum CodingKeys: String, CodingKey {
        case paymentType = "payment_type"
    }
}

private struct NativeUpdateReceiptEmailRequest: Encodable {
    let email: String
}

private struct NativeUpdateReceiptEmailResponse: Decodable {
    let clientId: String
    let email: String

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case email
    }
}

private struct NativeEmptyRequest: Encodable {}

private struct NativeUpdateRentalPipelineStatusRequest: Encodable {
    let pipelineStatus: String

    enum CodingKeys: String, CodingKey {
        case pipelineStatus = "pipeline_status"
    }
}

private struct NativeUpdateRentalPipelineStatusResponse: Decodable {
    let rentalId: String
    let pipelineStatus: String

    enum CodingKeys: String, CodingKey {
        case rentalId = "rental_id"
        case pipelineStatus = "pipeline_status"
    }
}

private struct NativeFinishRentalResponse: Decodable {
    let rentalId: String
    let periodEnd: String

    enum CodingKeys: String, CodingKey {
        case rentalId = "rental_id"
        case periodEnd = "period_end"
    }
}

protocol BackendServicing {
    func isServerReachable() async -> Bool
    func login(login: String, password: String) async throws -> AuthSession
    func fetchClientDashboard(accessToken: String) async throws -> ClientDashboardResponse
    func fetchAdminClients(accessToken: String) async throws -> [AdminClientSummaryResponse]
    func fetchAdminRents(accessToken: String) async throws -> [AdminClientSummaryResponse]
    func fetchAdminClientCatalog(accessToken: String) async throws -> [AdminClientSummaryResponse]
    func fetchAdminBikes(accessToken: String) async throws -> [AdminBikeResponse]
    func updateClientReceiptEmail(accessToken: String, email: String) async throws
    func createPayment(accessToken: String, paymentType: ClientPaymentType) async throws -> PaymentCreationResponse
    func fetchPaymentStatus(accessToken: String, paymentId: String) async throws -> PaymentStatusResponse
    func fetchAdminClientDetails(accessToken: String, clientId: String) async throws -> AdminClientDetailsResponse
    func createAdminClient(accessToken: String, payload: CreateClientPayload) async throws -> AdminClientDetailsResponse
    func createAdminBike(accessToken: String, payload: CreateBikePayload) async throws -> AdminBikeResponse
    func updateAdminBike(accessToken: String, payload: UpdateBikePayload) async throws -> AdminBikeResponse
    func deleteAdminBike(accessToken: String, bikeId: String) async throws -> DeleteBikeResult
    func updateAdminClientProfile(
        accessToken: String,
        clientId: String,
        payload: UpdateClientProfilePayload
    ) async throws -> AdminClientDetailsResponse
    func deleteAdminClient(accessToken: String, clientId: String) async throws -> DeleteClientResult
    func createAdminRental(
        accessToken: String,
        payload: CreateRentalPayload
    ) async throws -> AdminRentalHistoryItem
    func updateAdminRental(
        accessToken: String,
        payload: UpdateRentalPayload
    ) async throws -> AdminRentalHistoryItem
    func deleteAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws -> DeleteRentalResult
    func updateAdminRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String
    ) async throws
    func finishAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws
    func adjustAdminClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: DebtAdjustmentSign,
        comment: String?
    ) async throws -> DebtAdjustmentResult
    func updateAdminRentalComment(
        accessToken: String,
        rentalId: String,
        comment: String
    ) async throws -> String
    func updateAdminRentalLinks(
        accessToken: String,
        rentalId: String,
        videoUrl: String?,
        contractUrl: String?
    ) async throws -> AdminRentalLinksUpdateResult
}

enum BackendError: Error {
    case invalidResponse
    case httpError(code: Int, body: String)
    case network(String)
    case unknown(String)
}

extension BackendError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Некорректный ответ backend"
        case let .httpError(code, body):
            let backendMessage = BackendErrorMessageParser.extractBackendMessage(from: body)
            if let backendMessage {
                return BackendErrorMessageParser.humanReadable(code: code, backendMessage: backendMessage)
            }
            return BackendErrorMessageParser.humanReadable(code: code, backendMessage: nil)
        case let .network(message):
            let normalized = message.trimmingCharacters(in: .whitespacesAndNewlines)
            if normalized.isEmpty {
                return "Сетевая ошибка: не удалось связаться с backend"
            }
            return "Сетевая ошибка: \(normalized)"
        case let .unknown(message):
            return "Ошибка backend: \(message)"
        }
    }
}

private enum BackendErrorMessageParser {
    private static let knownMessageMap: [String: String] = [
        "login is already used": "Логин уже занят. Укажите другой логин.",
        "login and password are required": "Укажите логин и пароль клиента.",
        "неверный логин или пароль": "Неверный логин или пароль.",
        "full_name is required": "Укажите ФИО клиента.",
        "weekly_rate_rub must be positive": "Сумма за неделю должна быть больше 0.",
        "rental_start must be YYYY-MM-DD": "Дата начала аренды должна быть в формате YYYY-MM-DD.",
        "bike_model is required": "Укажите модель велосипеда.",
        "bike_id is required": "Выберите велосипед.",
        "client_id is required": "Выберите клиента.",
        "period_start is required": "Укажите дату начала аренды.",
        "period_start must be YYYY-MM-DD": "Дата начала аренды должна быть в формате YYYY-MM-DD.",
        "period_end must be YYYY-MM-DD": "Дата окончания аренды должна быть в формате YYYY-MM-DD.",
        "period_end must be after or equal to period_start": "Дата окончания не может быть раньше даты начала.",
        "pipeline_status is invalid": "Некорректный статус аренды.",
        "bikeid is required": "Не указан идентификатор велосипеда.",
        "clientid is required": "Не указан идентификатор клиента.",
        "rentalid is required": "Не указан идентификатор аренды.",
        "amount_rub must be positive": "Сумма должна быть больше 0.",
        "sign must be plus or minus": "Тип корректировки указан некорректно.",
        "comment is required": "Комментарий не может быть пустым.",
        "frame_serial_number is required": "Укажите серийный номер рамы.",
        "motor_serial_number is required": "Укажите серийный номер мотора.",
        "battery_serial_number_1 is required": "Укажите серийный номер аккумулятора 1.",
        "frame_serial_number is already used": "Велосипед с таким серийным номером рамы уже существует.",
        "motor_serial_number is already used": "Велосипед с таким серийным номером мотора уже существует.",
        "battery_serial_number_1 is already used": "Велосипед с таким серийным номером аккумулятора 1 уже существует.",
        "battery_serial_number_2 is already used": "Велосипед с таким серийным номером аккумулятора 2 уже существует.",
        "bike serial numbers are already used": "Серийные номера уже используются в другом велосипеде.",
        "serial numbers must be unique inside bike": "Серийные номера в карточке велосипеда должны быть уникальными.",
        "invalid request body": "Некорректный формат данных. Проверьте заполнение полей.",
        "internal server error": "Внутренняя ошибка сервера. Попробуйте ещё раз.",
        "unauthorized": "Сессия недействительна. Войдите снова.",
        "client not found": "Клиент не найден.",
        "rental not found": "Аренда не найдена.",
        "bike not found": "Велосипед не найден.",
        "client is used by rentals": "Клиента с историей аренд нельзя удалить. Можно только редактировать профиль.",
        "bike is used by rentals": "Велосипед уже используется в арендах и не может быть удален.",
        "yookassa payment creation failed": "Не удалось создать платеж в ЮKassa. Попробуйте ещё раз.",
        "yookassa payment status check failed": "Не удалось проверить статус платежа в ЮKassa. Попробуйте ещё раз.",
        "yookassa is not configured": "ЮKassa не настроена на backend. Проверьте YOOKASSA_SECRET_KEY и YOOKASSA_PUBLIC_BASE_URL.",
        "client email is invalid": "Укажите корректный email для чека.",
        "client email is required for yookassa receipt": "Укажите email для чека перед оплатой.",
        "payment not found": "Платеж не найден.",
        "unknown payment_type": "Неизвестный тип платежа.",
        "amount is zero. nothing to pay.": "Сейчас нечего оплачивать."
    ]

    static func extractBackendMessage(from body: String) -> String? {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if let jsonData = trimmed.data(using: .utf8),
           let jsonObject = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
           let message = jsonObject["message"] as? String {
            let normalized = message.trimmingCharacters(in: .whitespacesAndNewlines)
            return normalized.isEmpty ? nil : normalized
        }

        return trimmed
    }

    static func humanReadable(code: Int, backendMessage: String?) -> String {
        let normalizedBackendMessage = backendMessage?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let normalizedBackendMessage, !normalizedBackendMessage.isEmpty {
            let mapped = knownMessageMap[normalizedBackendMessage.lowercased()]
            if let mapped {
                return mapped
            }
            switch code {
            case 400:
                return "Проверьте заполнение формы: \(normalizedBackendMessage)"
            case 401:
                return "Сессия недействительна. Войдите снова."
            case 403:
                return "Недостаточно прав для выполнения операции."
            case 404:
                return "Объект не найден: \(normalizedBackendMessage)"
            case 409:
                return "Конфликт данных: \(normalizedBackendMessage)"
            case 500 ... 599:
                return "Ошибка сервера (\(code)). Попробуйте ещё раз."
            default:
                return "Ошибка backend (\(code)): \(normalizedBackendMessage)"
            }
        }

        switch code {
        case 400:
            return "Проверьте корректность введенных данных."
        case 401:
            return "Сессия недействительна. Войдите снова."
        case 403:
            return "Недостаточно прав для выполнения операции."
        case 404:
            return "Данные не найдены."
        case 409:
            return "Конфликт данных. Проверьте уникальность логина."
        case 500 ... 599:
            return "Ошибка сервера (\(code)). Попробуйте ещё раз."
        default:
            return "Ошибка backend (\(code))."
        }
    }
}

final class BackendService: BackendServicing {
    private static let quickHealthCheckTimeout: TimeInterval = 0.6

    private let baseUrl: String
    private let apiClient: AtomGoApiClient
    private let urlSession: URLSession
    private let jsonEncoder: JSONEncoder
    private let jsonDecoder: JSONDecoder

    init(baseUrl: String = BackendRuntimeConfig.baseUrl) {
        self.baseUrl = baseUrl
        self.apiClient = AtomGoApiClient(baseUrl: baseUrl)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 8
        self.urlSession = URLSession(configuration: configuration)
        self.jsonEncoder = JSONEncoder()
        self.jsonDecoder = JSONDecoder()
    }

    deinit {
        close()
    }

    func close() {
        self.apiClient.close()
        self.urlSession.invalidateAndCancel()
    }

    static var configuredBaseUrls: [String] {
        BackendRuntimeConfig.candidateBaseUrls
    }

    static func firstReachableBaseUrlQuick() async -> String? {
        let candidates = configuredBaseUrls
        for baseUrl in candidates {
            if await isServerReachableQuick(baseUrl: baseUrl) {
                return baseUrl
            }
        }
        return nil
    }

    static func isServerReachableQuick(baseUrl: String = BackendRuntimeConfig.baseUrl) async -> Bool {
        guard let url = healthCheckURL(fromBaseURL: baseUrl) else {
            return false
        }

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = quickHealthCheckTimeout
        config.timeoutIntervalForResource = quickHealthCheckTimeout

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = quickHealthCheckTimeout

        do {
            let (_, response) = try await URLSession(configuration: config).data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return false
            }
            return (200 ... 299).contains(httpResponse.statusCode)
        } catch {
            return false
        }
    }

    func isServerReachable() async -> Bool {
        do {
            let reachable: KotlinBoolean = try await awaitResult { completion in
                self.apiClient.isServerReachable(completionHandler: completion)
            }
            return reachable.boolValue
        } catch {
            return false
        }
    }

    func login(login: String, password: String) async throws -> AuthSession {
        let response: LoginResponse = try await sendNativeRequest(
            path: "/auth/login",
            method: "POST",
            body: NativeLoginRequest(login: login, password: password)
        )
        return AuthSession(
            accessToken: response.accessToken,
            role: response.role,
            userId: response.userId
        )
    }

    func fetchClientDashboard(accessToken: String) async throws -> ClientDashboardResponse {
        try await sendNativeRequest(
            path: "/client/me/dashboard",
            method: "GET",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    func fetchAdminClients(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        let clients: [shared.AdminClientSummaryResponse] = try await awaitResult { completion in
            self.apiClient.fetchAdminClients(accessToken: accessToken, completionHandler: completion)
        }

        return clients.map(mapAdminClientSummary)
    }

    func fetchAdminRents(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        try await sendNativeRequest(
            path: "/admin/rents",
            method: "GET",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    func fetchAdminClientCatalog(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        try await sendNativeRequest(
            path: "/admin/clients",
            method: "GET",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    private func mapAdminClientSummary(_ client: shared.AdminClientSummaryResponse) -> AdminClientSummaryResponse {
        AdminClientSummaryResponse(
            clientId: client.clientId,
            rentalId: client.rentalId,
            clientLogin: client.clientLogin,
            fullName: client.fullName,
            bikeModel: client.bikeModel,
            bikeAvatarUrl: client.bikeAvatarUrl,
            statusText: client.statusText,
            paidUntil: client.paidUntil,
            rentalPipelineStatus: client.rentalPipelineStatus,
            rentalIsActive: client.rentalIsActive,
            debtRub: Int(client.debtRub),
            profitRub: Int(client.profitRub),
            totalAdjustmentRub: Int(client.totalAdjustmentRub)
        )
    }

    func fetchAdminBikes(accessToken: String) async throws -> [AdminBikeResponse] {
        let bikes: [shared.AdminBikeResponse] = try await awaitResult { completion in
            self.apiClient.fetchAdminBikes(accessToken: accessToken, completionHandler: completion)
        }

        return bikes.map {
            AdminBikeResponse(
                bikeId: $0.bikeId,
                photoUrl: $0.photoUrl,
                bikeModel: $0.bikeModel,
                weeklyRateRub: Int($0.weeklyRateRub),
                frameSerialNumber: $0.frameSerialNumber,
                motorSerialNumber: $0.motorSerialNumber,
                batterySerialNumber1: $0.batterySerialNumber1,
                batterySerialNumber2: $0.batterySerialNumber2
            )
        }
    }

    func fetchAdminClientDetails(accessToken: String, clientId: String) async throws -> AdminClientDetailsResponse {
        let details: shared.AdminClientDetailsResponse = try await awaitResult { completion in
            self.apiClient.fetchAdminClientDetails(accessToken: accessToken, clientId: clientId, completionHandler: completion)
        }
        return mapAdminClientDetails(details)
    }

    func createAdminClient(accessToken: String, payload: CreateClientPayload) async throws -> AdminClientDetailsResponse {
        let phones = payload.phones.map {
            shared.AdminClientPhone(label: $0.label, number: $0.number)
        }
        let request = shared.AdminCreateClientRequest(
            fullName: payload.fullName,
            address: payload.address,
            passportData: payload.passportData,
            phones: phones
        )
        let details: shared.AdminClientDetailsResponse = try await awaitResult { completion in
            self.apiClient.createAdminClient(accessToken: accessToken, requestBody: request, completionHandler: completion)
        }
        return mapAdminClientDetails(details)
    }

    func createAdminBike(accessToken: String, payload: CreateBikePayload) async throws -> AdminBikeResponse {
        let request = shared.AdminCreateBikeRequest(
            photoUrl: payload.photoUrl,
            bikeModel: payload.bikeModel,
            weeklyRateRub: Int32(payload.weeklyRateRub),
            frameSerialNumber: payload.frameSerialNumber,
            motorSerialNumber: payload.motorSerialNumber,
            batterySerialNumber1: payload.batterySerialNumber1,
            batterySerialNumber2: payload.batterySerialNumber2
        )
        let bike: shared.AdminBikeResponse = try await awaitResult { completion in
            self.apiClient.createAdminBike(accessToken: accessToken, requestBody: request, completionHandler: completion)
        }
        return AdminBikeResponse(
            bikeId: bike.bikeId,
            photoUrl: bike.photoUrl,
            bikeModel: bike.bikeModel,
            weeklyRateRub: Int(bike.weeklyRateRub),
            frameSerialNumber: bike.frameSerialNumber,
            motorSerialNumber: bike.motorSerialNumber,
            batterySerialNumber1: bike.batterySerialNumber1,
            batterySerialNumber2: bike.batterySerialNumber2
        )
    }

    func updateAdminBike(accessToken: String, payload: UpdateBikePayload) async throws -> AdminBikeResponse {
        let request = shared.AdminUpdateBikeRequest(
            photoUrl: payload.photoUrl,
            bikeModel: payload.bikeModel,
            weeklyRateRub: Int32(payload.weeklyRateRub),
            frameSerialNumber: payload.frameSerialNumber,
            motorSerialNumber: payload.motorSerialNumber,
            batterySerialNumber1: payload.batterySerialNumber1,
            batterySerialNumber2: payload.batterySerialNumber2
        )
        let bike: shared.AdminBikeResponse = try await awaitResult { completion in
            self.apiClient.updateAdminBike(
                accessToken: accessToken,
                bikeId: payload.bikeId,
                requestBody: request,
                completionHandler: completion
            )
        }

        return AdminBikeResponse(
            bikeId: bike.bikeId,
            photoUrl: bike.photoUrl,
            bikeModel: bike.bikeModel,
            weeklyRateRub: Int(bike.weeklyRateRub),
            frameSerialNumber: bike.frameSerialNumber,
            motorSerialNumber: bike.motorSerialNumber,
            batterySerialNumber1: bike.batterySerialNumber1,
            batterySerialNumber2: bike.batterySerialNumber2
        )
    }

    func deleteAdminBike(accessToken: String, bikeId: String) async throws -> DeleteBikeResult {
        try await sendNativeRequest(
            path: "/admin/bikes/\(bikeId)/delete",
            method: "POST",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    func createAdminRental(
        accessToken: String,
        payload: CreateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        let request = shared.AdminCreateRentalRequest(
            clientId: payload.clientId,
            bikeId: payload.bikeId,
            login: payload.login,
            password: payload.password,
            periodStart: payload.periodStart,
            periodEnd: payload.periodEnd,
            videoUrl: payload.videoUrl,
            contractUrl: payload.contractUrl,
            comment: payload.comment
        )
        let response: shared.AdminRentalHistoryItemResponse = try await awaitResult { completion in
            self.apiClient.createAdminRental(
                accessToken: accessToken,
                requestBody: request,
                completionHandler: completion
            )
        }
        return AdminRentalHistoryItem(
            id: response.rentalId,
            bikeId: response.bikeId,
            bikeAvatarUrl: response.bikeAvatarUrl,
            periodStart: response.periodStart,
            periodEnd: response.periodEnd,
            bikeModel: response.bikeModel,
            videoUrl: response.videoUrl,
            contractUrl: response.contractUrl,
            comment: response.comment
        )
    }

    func updateAdminRental(
        accessToken: String,
        payload: UpdateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        let request = shared.AdminUpdateRentalRequest(
            bikeId: payload.bikeId,
            periodStart: payload.periodStart,
            periodEnd: payload.periodEnd
        )
        let response: shared.AdminRentalHistoryItemResponse = try await awaitResult { completion in
            self.apiClient.updateAdminRental(
                accessToken: accessToken,
                rentalId: payload.rentalId,
                requestBody: request,
                completionHandler: completion
            )
        }
        return AdminRentalHistoryItem(
            id: response.rentalId,
            bikeId: response.bikeId,
            bikeAvatarUrl: response.bikeAvatarUrl,
            periodStart: response.periodStart,
            periodEnd: response.periodEnd,
            bikeModel: response.bikeModel,
            videoUrl: response.videoUrl,
            contractUrl: response.contractUrl,
            comment: response.comment
        )
    }

    func deleteAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws -> DeleteRentalResult {
        let response: shared.AdminDeleteRentalResponse = try await awaitResult { completion in
            self.apiClient.deleteAdminRental(
                accessToken: accessToken,
                rentalId: rentalId,
                completionHandler: completion
            )
        }
        return DeleteRentalResult(
            rentalId: response.rentalId,
            deleted: response.deleted
        )
    }

    func updateAdminRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String
    ) async throws {
        let _: NativeUpdateRentalPipelineStatusResponse = try await sendNativeRequest(
            path: "/admin/rentals/\(rentalId)/pipeline-status",
            method: "POST",
            accessToken: accessToken,
            body: NativeUpdateRentalPipelineStatusRequest(pipelineStatus: pipelineStatus)
        )
    }

    func finishAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws {
        let _: NativeFinishRentalResponse = try await sendNativeRequest(
            path: "/admin/rentals/\(rentalId)/finish",
            method: "POST",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    func updateAdminClientProfile(
        accessToken: String,
        clientId: String,
        payload: UpdateClientProfilePayload
    ) async throws -> AdminClientDetailsResponse {
        let phones = payload.phones.map {
            shared.AdminClientPhone(label: $0.label, number: $0.number)
        }
        let request = shared.AdminUpdateClientRequest(
            fullName: payload.fullName,
            address: payload.address,
            passportData: payload.passportData,
            phones: phones
        )
        let details: shared.AdminClientDetailsResponse = try await awaitResult { completion in
            self.apiClient.updateAdminClient(
                accessToken: accessToken,
                clientId: clientId,
                requestBody: request,
                completionHandler: completion
            )
        }
        return mapAdminClientDetails(details)
    }

    func deleteAdminClient(accessToken: String, clientId: String) async throws -> DeleteClientResult {
        try await sendNativeRequest(
            path: "/admin/clients/\(clientId)/delete",
            method: "POST",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    func adjustAdminClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: DebtAdjustmentSign,
        comment: String?
    ) async throws -> DebtAdjustmentResult {
        let response: shared.AdminDebtAdjustmentResponse = try await awaitResult { completion in
            self.apiClient.adjustAdminClientDebt(
                accessToken: accessToken,
                clientId: clientId,
                amountRub: Int32(amountRub),
                sign: sign.apiValue,
                comment: comment,
                completionHandler: completion
            )
        }
        return DebtAdjustmentResult(
            clientId: response.clientId,
            debtRub: Int(response.debtRub),
            totalAdjustmentRub: Int(response.totalAdjustmentRub)
        )
    }

    func updateAdminRentalComment(
        accessToken: String,
        rentalId: String,
        comment: String
    ) async throws -> String {
        let response: shared.AdminRentalCommentUpdateResponse = try await awaitResult { completion in
            self.apiClient.updateAdminRentalComment(
                accessToken: accessToken,
                rentalId: rentalId,
                comment: comment,
                completionHandler: completion
            )
        }
        return response.comment
    }

    func updateAdminRentalLinks(
        accessToken: String,
        rentalId: String,
        videoUrl: String?,
        contractUrl: String?
    ) async throws -> AdminRentalLinksUpdateResult {
        let response: shared.AdminRentalLinksUpdateResponse = try await awaitResult { completion in
            self.apiClient.updateAdminRentalLinks(
                accessToken: accessToken,
                rentalId: rentalId,
                videoUrl: videoUrl,
                contractUrl: contractUrl,
                completionHandler: completion
            )
        }
        return AdminRentalLinksUpdateResult(
            rentalId: response.rentalId,
            videoUrl: response.videoUrl,
            contractUrl: response.contractUrl
        )
    }

    func updateClientReceiptEmail(accessToken: String, email: String) async throws {
        let _: NativeUpdateReceiptEmailResponse = try await sendNativeRequest(
            path: "/client/me/receipt-email",
            method: "POST",
            accessToken: accessToken,
            body: NativeUpdateReceiptEmailRequest(email: email)
        )
    }

    func createPayment(accessToken: String, paymentType: ClientPaymentType) async throws -> PaymentCreationResponse {
        try await sendNativeRequest(
            path: "/payments/create",
            method: "POST",
            accessToken: accessToken,
            body: NativeCreatePaymentRequest(paymentType: paymentType.apiValue)
        )
    }

    func fetchPaymentStatus(accessToken: String, paymentId: String) async throws -> PaymentStatusResponse {
        try await sendNativeRequest(
            path: "/payments/\(paymentId)",
            method: "GET",
            accessToken: accessToken,
            body: Optional<NativeEmptyRequest>.none
        )
    }

    private func sendNativeRequest<RequestBody: Encodable, ResponseBody: Decodable>(
        path: String,
        method: String,
        accessToken: String? = nil,
        body: RequestBody?
    ) async throws -> ResponseBody {
        guard let url = URL(string: "\(baseUrl)\(path)") else {
            throw BackendError.network("Некорректный адрес backend: \(baseUrl)\(path)")
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try jsonEncoder.encode(body)
        }

        do {
            let (data, response) = try await urlSession.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw BackendError.invalidResponse
            }

            guard (200 ... 299).contains(httpResponse.statusCode) else {
                let responseBody = String(data: data, encoding: .utf8) ?? ""
                throw BackendError.httpError(code: httpResponse.statusCode, body: responseBody)
            }

            do {
                return try jsonDecoder.decode(ResponseBody.self, from: data)
            } catch {
                throw BackendError.unknown("Не удалось прочитать ответ backend: \(error.localizedDescription)")
            }
        } catch let backendError as BackendError {
            throw backendError
        } catch {
            throw BackendError.network(error.localizedDescription)
        }
    }

    private func mapRole(_ role: shared.UserRole) -> AppRole {
        switch role.name.lowercased() {
        case "admin":
            return .admin
        case "client":
            return .client
        default:
            return .client
        }
    }

    private func awaitResult<T>(
        _ block: @escaping (@escaping @Sendable (T?, Error?) -> Void) -> Void
    ) async throws -> T {
        do {
            return try await withCheckedThrowingContinuation { continuation in
                let gate = ContinuationResumeGate()
                let completion: @Sendable (T?, Error?) -> Void = { value, error in
                    guard gate.claim() else {
                        return
                    }

                    if let error {
                        continuation.resume(throwing: self.normalizeError(error))
                        return
                    }

                    guard let value else {
                        continuation.resume(throwing: BackendError.invalidResponse)
                        return
                    }

                    continuation.resume(returning: value)
                }

                if Thread.isMainThread {
                    DispatchQueue.global(qos: .userInitiated).async {
                        block(completion)
                    }
                } else {
                    block(completion)
                }
            }
        } catch let backendError as BackendError {
            throw backendError
        } catch {
            throw BackendError.unknown(error.localizedDescription)
        }
    }

    private func normalizeError(_ error: Error) -> BackendError {
        let nsError = error as NSError
        if let message = nsError.kotlinExceptionMessage, !message.isEmpty {
            if let parsed = parseHttpError(message) {
                return parsed
            }
            return .network(message)
        }

        if nsError.domain == NSURLErrorDomain {
            return .network(nsError.localizedDescription)
        }

        let fallback = nsError.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        if fallback.isEmpty {
            return .unknown("Неизвестная ошибка")
        }
        return .unknown(fallback)
    }

    private func parseHttpError(_ message: String) -> BackendError? {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("HTTP ") else { return nil }
        let parts = trimmed.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
        guard let firstPart = parts.first else { return nil }
        let statusCode = firstPart.replacingOccurrences(of: "HTTP ", with: "")
        guard let code = Int(statusCode) else { return nil }
        let body = parts.count > 1 ? String(parts[1]).trimmingCharacters(in: .whitespaces) : ""
        return .httpError(code: code, body: body)
    }

    private func mapAdminClientDetails(_ details: shared.AdminClientDetailsResponse) -> AdminClientDetailsResponse {
        let phones = details.phones.enumerated().map { index, value in
            AdminClientPhone(
                id: "\(details.clientId)-phone-\(index)",
                label: value.label,
                number: value.number
            )
        }
        let rentals = details.rentals.map {
            AdminRentalHistoryItem(
                id: $0.rentalId,
                bikeId: $0.bikeId,
                bikeAvatarUrl: $0.bikeAvatarUrl,
                periodStart: $0.periodStart,
                periodEnd: $0.periodEnd,
                bikeModel: $0.bikeModel,
                videoUrl: $0.videoUrl,
                contractUrl: $0.contractUrl,
                comment: $0.comment
            )
        }

        return AdminClientDetailsResponse(
            clientId: details.clientId,
            fullName: details.fullName,
            address: details.address,
            passportData: details.passportData,
            weeklyRateRub: Int(details.weeklyRateRub),
            bikeModel: details.bikeModel,
            bikeAvatarUrl: details.bikeAvatarUrl,
            rentalStart: details.rentalStart,
            paidUntil: details.paidUntil,
            totalPaidRub: Int(details.totalPaidRub),
            debtRub: Int(details.debtRub),
            totalAdjustmentRub: Int(details.totalAdjustmentRub),
            phones: phones,
            rentals: rentals
        )
    }

    private static func healthCheckURL(fromBaseURL baseUrl: String) -> URL? {
        let normalizedBase = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !normalizedBase.isEmpty else { return nil }
        let healthBase = normalizedBase.hasSuffix("/api/v1")
            ? String(normalizedBase.dropLast("/api/v1".count))
            : normalizedBase
        return URL(string: "\(healthBase)/health/ready")
    }
}

final class LazyBackendService: BackendServicing {
    private let baseUrls: [String]
    private let lock = NSLock()
    private var preferredBaseUrl: String
    private var servicesByBaseUrl: [String: BackendService] = [:]

    init(baseUrl: String? = nil) {
        let normalizedCustom = baseUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let normalizedCustom, !normalizedCustom.isEmpty {
            let trimmed = normalizedCustom.hasSuffix("/") ? String(normalizedCustom.dropLast()) : normalizedCustom
            self.baseUrls = [trimmed]
            self.preferredBaseUrl = trimmed
        } else {
            let candidates = BackendRuntimeConfig.candidateBaseUrls
            self.baseUrls = candidates.isEmpty ? [BackendRuntimeConfig.baseUrl] : candidates
            self.preferredBaseUrl = self.baseUrls.first ?? BackendRuntimeConfig.baseUrl
        }
    }

    deinit {
        lock.lock()
        let services = Array(servicesByBaseUrl.values)
        lock.unlock()
        services.forEach { $0.close() }
    }

    func isServerReachable() async -> Bool {
        for baseUrl in orderedBaseUrls() {
            if await BackendService.isServerReachableQuick(baseUrl: baseUrl) {
                setPreferred(baseUrl)
                return true
            }
        }
        return false
    }

    func login(login: String, password: String) async throws -> AuthSession {
        try await withFallback { service in
            try await service.login(login: login, password: password)
        }
    }

    func fetchClientDashboard(accessToken: String) async throws -> ClientDashboardResponse {
        try await withFallback { service in
            try await service.fetchClientDashboard(accessToken: accessToken)
        }
    }

    func fetchAdminClients(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        try await withFallback { service in
            try await service.fetchAdminClients(accessToken: accessToken)
        }
    }

    func fetchAdminRents(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        try await withFallback { service in
            try await service.fetchAdminRents(accessToken: accessToken)
        }
    }

    func fetchAdminClientCatalog(accessToken: String) async throws -> [AdminClientSummaryResponse] {
        try await withFallback { service in
            try await service.fetchAdminClientCatalog(accessToken: accessToken)
        }
    }

    func fetchAdminBikes(accessToken: String) async throws -> [AdminBikeResponse] {
        try await withFallback { service in
            try await service.fetchAdminBikes(accessToken: accessToken)
        }
    }

    func updateClientReceiptEmail(accessToken: String, email: String) async throws {
        try await withFallback { service in
            try await service.updateClientReceiptEmail(accessToken: accessToken, email: email)
        }
    }

    func createPayment(accessToken: String, paymentType: ClientPaymentType) async throws -> PaymentCreationResponse {
        try await withFallback { service in
            try await service.createPayment(accessToken: accessToken, paymentType: paymentType)
        }
    }

    func fetchPaymentStatus(accessToken: String, paymentId: String) async throws -> PaymentStatusResponse {
        try await withFallback { service in
            try await service.fetchPaymentStatus(accessToken: accessToken, paymentId: paymentId)
        }
    }

    func fetchAdminClientDetails(accessToken: String, clientId: String) async throws -> AdminClientDetailsResponse {
        try await withFallback { service in
            try await service.fetchAdminClientDetails(accessToken: accessToken, clientId: clientId)
        }
    }

    func createAdminClient(accessToken: String, payload: CreateClientPayload) async throws -> AdminClientDetailsResponse {
        try await withFallback { service in
            try await service.createAdminClient(accessToken: accessToken, payload: payload)
        }
    }

    func createAdminBike(accessToken: String, payload: CreateBikePayload) async throws -> AdminBikeResponse {
        try await withFallback { service in
            try await service.createAdminBike(accessToken: accessToken, payload: payload)
        }
    }

    func updateAdminBike(accessToken: String, payload: UpdateBikePayload) async throws -> AdminBikeResponse {
        try await withFallback { service in
            try await service.updateAdminBike(accessToken: accessToken, payload: payload)
        }
    }

    func deleteAdminBike(accessToken: String, bikeId: String) async throws -> DeleteBikeResult {
        try await withFallback { service in
            try await service.deleteAdminBike(accessToken: accessToken, bikeId: bikeId)
        }
    }

    func updateAdminClientProfile(
        accessToken: String,
        clientId: String,
        payload: UpdateClientProfilePayload
    ) async throws -> AdminClientDetailsResponse {
        try await withFallback { service in
            try await service.updateAdminClientProfile(
                accessToken: accessToken,
                clientId: clientId,
                payload: payload
            )
        }
    }

    func deleteAdminClient(accessToken: String, clientId: String) async throws -> DeleteClientResult {
        try await withFallback { service in
            try await service.deleteAdminClient(accessToken: accessToken, clientId: clientId)
        }
    }

    func createAdminRental(
        accessToken: String,
        payload: CreateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        try await withFallback { service in
            try await service.createAdminRental(
                accessToken: accessToken,
                payload: payload
            )
        }
    }

    func updateAdminRental(
        accessToken: String,
        payload: UpdateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        try await withFallback { service in
            try await service.updateAdminRental(
                accessToken: accessToken,
                payload: payload
            )
        }
    }

    func deleteAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws -> DeleteRentalResult {
        try await withFallback { service in
            try await service.deleteAdminRental(
                accessToken: accessToken,
                rentalId: rentalId
            )
        }
    }

    func updateAdminRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String
    ) async throws {
        try await withFallback { service in
            try await service.updateAdminRentalPipelineStatus(
                accessToken: accessToken,
                rentalId: rentalId,
                pipelineStatus: pipelineStatus
            )
        }
    }

    func finishAdminRental(
        accessToken: String,
        rentalId: String
    ) async throws {
        try await withFallback { service in
            try await service.finishAdminRental(
                accessToken: accessToken,
                rentalId: rentalId
            )
        }
    }

    func adjustAdminClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: DebtAdjustmentSign,
        comment: String?
    ) async throws -> DebtAdjustmentResult {
        try await withFallback { service in
            try await service.adjustAdminClientDebt(
                accessToken: accessToken,
                clientId: clientId,
                amountRub: amountRub,
                sign: sign,
                comment: comment
            )
        }
    }

    func updateAdminRentalComment(
        accessToken: String,
        rentalId: String,
        comment: String
    ) async throws -> String {
        try await withFallback { service in
            try await service.updateAdminRentalComment(
                accessToken: accessToken,
                rentalId: rentalId,
                comment: comment
            )
        }
    }

    func updateAdminRentalLinks(
        accessToken: String,
        rentalId: String,
        videoUrl: String?,
        contractUrl: String?
    ) async throws -> AdminRentalLinksUpdateResult {
        try await withFallback { service in
            try await service.updateAdminRentalLinks(
                accessToken: accessToken,
                rentalId: rentalId,
                videoUrl: videoUrl,
                contractUrl: contractUrl
            )
        }
    }

    private func withFallback<T>(_ operation: (BackendService) async throws -> T) async throws -> T {
        let urls = orderedBaseUrls()
        var lastError: Error?

        for baseUrl in urls {
            let service = resolve(for: baseUrl)
            do {
                let result = try await operation(service)
                setPreferred(baseUrl)
                return result
            } catch {
                lastError = error
                if shouldTryNext(error) {
                    continue
                }
                throw error
            }
        }

        throw lastError ?? BackendError.network("Не удалось подключиться к backend")
    }

    private func shouldTryNext(_ error: Error) -> Bool {
        guard let backendError = error as? BackendError else {
            return true
        }
        switch backendError {
        case .network, .unknown, .invalidResponse:
            return true
        case .httpError:
            return false
        }
    }

    private func orderedBaseUrls() -> [String] {
        lock.lock()
        let preferred = preferredBaseUrl
        let candidates = baseUrls
        lock.unlock()

        var ordered: [String] = [preferred]
        for candidate in candidates where candidate != preferred {
            ordered.append(candidate)
        }
        return ordered
    }

    private func resolve(for baseUrl: String) -> BackendService {
        lock.lock()
        defer { lock.unlock() }
        if let cachedService = servicesByBaseUrl[baseUrl] {
            return cachedService
        }

        let service = BackendService(baseUrl: baseUrl)
        servicesByBaseUrl[baseUrl] = service
        return service
    }

    private func setPreferred(_ baseUrl: String) {
        lock.lock()
        preferredBaseUrl = baseUrl
        lock.unlock()
    }
}

private extension NSError {
    var kotlinExceptionMessage: String? {
        if let throwable = kotlinException as? KotlinThrowable {
            let message = throwable.message?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return message.isEmpty ? nil : message
        }
        return nil
    }
}
