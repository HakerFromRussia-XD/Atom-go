import Foundation

enum AppRole: String, Decodable {
    case admin
    case client
}

struct AuthSession: Equatable {
    let accessToken: String
    let role: AppRole
    let userId: String
}

struct LoginResponse: Decodable {
    let accessToken: String
    let role: AppRole
    let userId: String

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case role
        case userId = "user_id"
    }
}

struct ClientPaymentPresets: Decodable {
    let dayRub: Int
    let weekRub: Int
    let twoWeeksRub: Int
    let monthRub: Int
    let debtExactRub: Int

    enum CodingKeys: String, CodingKey {
        case dayRub = "day_rub"
        case weekRub = "week_rub"
        case twoWeeksRub = "two_weeks_rub"
        case monthRub = "month_rub"
        case debtExactRub = "debt_exact_rub"
    }
}

struct ClientDashboardResponse: Decodable {
    let clientId: String
    let bikeModel: String
    let bikeAvatarUrl: String?
    let rentalStart: String
    let paidUntil: String
    let completedAt: String?
    let rentalIsActive: Bool
    let debtRub: Int
    let balanceRub: Int?
    let totalAdjustmentRub: Int
    let presets: ClientPaymentPresets
    let taxMode: String?
    let requiresReceiptEmail: Bool

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case bikeModel = "bike_model"
        case bikeAvatarUrl = "bike_avatar_url"
        case rentalStart = "rental_start"
        case paidUntil = "paid_until"
        case completedAt = "completed_at"
        case rentalIsActive = "rental_is_active"
        case debtRub = "debt_rub"
        case balanceRub = "balance_rub"
        case totalAdjustmentRub = "total_adjustment_rub"
        case presets
        case taxMode = "tax_mode"
        case requiresReceiptEmail = "requires_receipt_email"
    }

    init(
        clientId: String,
        bikeModel: String,
        bikeAvatarUrl: String?,
        rentalStart: String,
        paidUntil: String,
        completedAt: String? = nil,
        rentalIsActive: Bool = false,
        debtRub: Int,
        balanceRub: Int?,
        totalAdjustmentRub: Int,
        presets: ClientPaymentPresets,
        taxMode: String?,
        requiresReceiptEmail: Bool
    ) {
        self.clientId = clientId
        self.bikeModel = bikeModel
        self.bikeAvatarUrl = bikeAvatarUrl
        self.rentalStart = rentalStart
        self.paidUntil = paidUntil
        self.completedAt = completedAt
        self.rentalIsActive = rentalIsActive
        self.debtRub = debtRub
        self.balanceRub = balanceRub
        self.totalAdjustmentRub = totalAdjustmentRub
        self.presets = presets
        self.taxMode = taxMode
        self.requiresReceiptEmail = requiresReceiptEmail
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        clientId = try container.decodeIfPresent(String.self, forKey: .clientId) ?? ""
        bikeModel = try container.decodeIfPresent(String.self, forKey: .bikeModel) ?? ""
        bikeAvatarUrl = try container.decodeIfPresent(String.self, forKey: .bikeAvatarUrl)
        rentalStart = try container.decodeIfPresent(String.self, forKey: .rentalStart) ?? ""
        paidUntil = try container.decodeIfPresent(String.self, forKey: .paidUntil) ?? ""
        completedAt = try container.decodeIfPresent(String.self, forKey: .completedAt)
        rentalIsActive = try container.decodeIfPresent(Bool.self, forKey: .rentalIsActive) ?? false
        debtRub = try container.decodeIfPresent(Int.self, forKey: .debtRub) ?? 0
        balanceRub = try container.decodeIfPresent(Int.self, forKey: .balanceRub)
        totalAdjustmentRub = try container.decodeIfPresent(Int.self, forKey: .totalAdjustmentRub) ?? 0
        presets = try container.decodeIfPresent(ClientPaymentPresets.self, forKey: .presets)
            ?? ClientPaymentPresets(dayRub: 0, weekRub: 0, twoWeeksRub: 0, monthRub: 0, debtExactRub: 0)
        taxMode = try container.decodeIfPresent(String.self, forKey: .taxMode)
        requiresReceiptEmail = try container.decodeIfPresent(Bool.self, forKey: .requiresReceiptEmail) ?? false
    }
}

struct AdminClientSummaryResponse: Decodable, Identifiable {
    let clientId: String
    let rentalId: String?
    let clientLogin: String?
    let fullName: String
    let bikeModel: String
    let bikeAvatarUrl: String
    let statusText: String
    let paidUntil: String?
    let rentalPipelineStatus: String?
    let rentalIsActive: Bool
    let debtRub: Int
    let profitRub: Int
    let totalAdjustmentRub: Int

    var id: String { clientId }

    init(
        clientId: String,
        rentalId: String? = nil,
        clientLogin: String?,
        fullName: String,
        bikeModel: String,
        bikeAvatarUrl: String,
        statusText: String,
        paidUntil: String? = nil,
        rentalPipelineStatus: String? = nil,
        rentalIsActive: Bool = false,
        debtRub: Int,
        profitRub: Int,
        totalAdjustmentRub: Int
    ) {
        self.clientId = clientId
        self.rentalId = rentalId
        self.clientLogin = clientLogin
        self.fullName = fullName
        self.bikeModel = bikeModel
        self.bikeAvatarUrl = bikeAvatarUrl
        self.statusText = statusText
        self.paidUntil = paidUntil
        self.rentalPipelineStatus = rentalPipelineStatus
        self.rentalIsActive = rentalIsActive
        self.debtRub = debtRub
        self.profitRub = profitRub
        self.totalAdjustmentRub = totalAdjustmentRub
    }

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case rentalId = "rental_id"
        case clientLogin = "client_login"
        case fullName = "full_name"
        case bikeModel = "bike_model"
        case bikeAvatarUrl = "bike_avatar_url"
        case statusText = "status_text"
        case paidUntil = "paid_until"
        case rentalPipelineStatus = "rental_pipeline_status"
        case rentalIsActive = "rental_is_active"
        case debtRub = "debt_rub"
        case profitRub = "profit_rub"
        case totalAdjustmentRub = "total_adjustment_rub"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        clientId = try container.decode(String.self, forKey: .clientId)
        rentalId = try container.decodeIfPresent(String.self, forKey: .rentalId)
        clientLogin = try container.decodeIfPresent(String.self, forKey: .clientLogin)
        fullName = try container.decode(String.self, forKey: .fullName)
        bikeModel = try container.decode(String.self, forKey: .bikeModel)
        bikeAvatarUrl = try container.decode(String.self, forKey: .bikeAvatarUrl)
        statusText = try container.decode(String.self, forKey: .statusText)
        paidUntil = try container.decodeIfPresent(String.self, forKey: .paidUntil)
        rentalPipelineStatus = try container.decodeIfPresent(String.self, forKey: .rentalPipelineStatus)
        rentalIsActive = try container.decodeIfPresent(Bool.self, forKey: .rentalIsActive) ?? false
        debtRub = try container.decode(Int.self, forKey: .debtRub)
        profitRub = try container.decode(Int.self, forKey: .profitRub)
        totalAdjustmentRub = try container.decode(Int.self, forKey: .totalAdjustmentRub)
    }
}

enum ClientPaymentType: String, CaseIterable, Identifiable {
    case day
    case week
    case twoWeeks
    case month
    case debtExact

    var id: String { rawValue }

    var title: String {
        switch self {
        case .day:
            return "1 день"
        case .week:
            return "1 неделя"
        case .twoWeeks:
            return "2 недели"
        case .month:
            return "1 месяц"
        case .debtExact:
            return "Ровно долг"
        }
    }

    var apiValue: String {
        switch self {
        case .day:
            return "day"
        case .week:
            return "week"
        case .twoWeeks:
            return "two_weeks"
        case .month:
            return "month"
        case .debtExact:
            return "debt_exact"
        }
    }
}

struct PaymentCreationResponse: Decodable, Equatable {
    let paymentId: String
    let amountRub: Int
    let confirmationUrl: String
    let idempotenceKey: String
    let status: String
    var taxMode: String? = nil
    var fiscalizationStatus: String? = nil

    enum CodingKeys: String, CodingKey {
        case paymentId = "payment_id"
        case amountRub = "amount_rub"
        case confirmationUrl = "confirmation_url"
        case idempotenceKey = "idempotence_key"
        case status
        case taxMode = "tax_mode"
        case fiscalizationStatus = "fiscalization_status"
    }
}

struct PaymentStatusResponse: Decodable, Equatable {
    let paymentId: String
    let amountRub: Int
    let confirmationUrl: String
    let providerPaymentId: String?
    let status: String
    let debtRub: Int?
    var taxMode: String? = nil
    var fiscalizationStatus: String? = nil

    enum CodingKeys: String, CodingKey {
        case paymentId = "payment_id"
        case amountRub = "amount_rub"
        case confirmationUrl = "confirmation_url"
        case providerPaymentId = "provider_payment_id"
        case status
        case debtRub = "debt_rub"
        case taxMode = "tax_mode"
        case fiscalizationStatus = "fiscalization_status"
    }
}

struct AdminClientPhone: Identifiable, Equatable {
    let id: String
    let label: String
    let number: String
}

struct AdminRentalHistoryItem: Identifiable, Equatable {
    let id: String
    let bikeId: String
    let bikeAvatarUrl: String
    let periodStart: String
    let periodEnd: String?
    let bikeModel: String
    let videoUrl: String?
    let contractUrl: String?
    var comment: String?
}

struct AdminClientDetailsResponse: Equatable {
    let clientId: String
    let fullName: String
    let address: String
    let passportData: String
    let weeklyRateRub: Int
    let bikeModel: String
    let bikeAvatarUrl: String
    let rentalStart: String
    let paidUntil: String
    let totalPaidRub: Int
    let debtRub: Int
    let totalAdjustmentRub: Int
    let phones: [AdminClientPhone]
    let rentals: [AdminRentalHistoryItem]
}

struct CreateClientPayload {
    var fullName: String
    var address: String
    var passportData: String
    var phones: [AdminClientPhone]
}

struct UpdateClientProfilePayload {
    var fullName: String
    var address: String
    var passportData: String
    var phones: [AdminClientPhone]
}

struct CreateRentalPayload {
    var clientId: String
    var bikeId: String
    var login: String
    var password: String
    var periodStart: String
    var periodEnd: String?
    var videoUrl: String?
    var contractUrl: String?
    var comment: String?
}

struct AdminBikeResponse: Equatable, Identifiable {
    let bikeId: String
    let photoUrl: String?
    let bikeModel: String
    let weeklyRateRub: Int
    let frameSerialNumber: String
    let motorSerialNumber: String
    let batterySerialNumber1: String
    let batterySerialNumber2: String?

    var id: String { bikeId }
}

struct CreateBikePayload {
    var photoUrl: String?
    var bikeModel: String
    var weeklyRateRub: Int
    var frameSerialNumber: String
    var motorSerialNumber: String
    var batterySerialNumber1: String
    var batterySerialNumber2: String?
}

struct UpdateBikePayload {
    var bikeId: String
    var photoUrl: String?
    var bikeModel: String
    var weeklyRateRub: Int
    var frameSerialNumber: String
    var motorSerialNumber: String
    var batterySerialNumber1: String
    var batterySerialNumber2: String?
}

struct UpdateRentalPayload {
    var clientId: String
    var rentalId: String
    var bikeId: String
    var periodStart: String
    var periodEnd: String?
}

struct DeleteRentalResult: Equatable {
    let rentalId: String
    let deleted: Bool
}

struct DeleteClientResult: Decodable, Equatable {
    let clientId: String
    let deleted: Bool

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case deleted
    }
}

struct DeleteBikeResult: Decodable, Equatable {
    let bikeId: String
    let deleted: Bool

    enum CodingKeys: String, CodingKey {
        case bikeId = "bike_id"
        case deleted
    }
}

enum DebtAdjustmentSign {
    case plus
    case minus

    var apiValue: String {
        switch self {
        case .plus:
            return "plus"
        case .minus:
            return "minus"
        }
    }
}

struct DebtAdjustmentResult: Equatable {
    let clientId: String
    let debtRub: Int
    let totalAdjustmentRub: Int
}

struct AdminRentalLinksUpdateResult: Equatable {
    let rentalId: String
    let videoUrl: String?
    let contractUrl: String?
}

struct AdminRentalJournalEntry: Decodable, Equatable, Identifiable {
    let type: String
    let amountRub: Int
    let createdAt: String

    var id: String { "\(type)-\(createdAt)-\(amountRub)" }

    enum CodingKeys: String, CodingKey {
        case type
        case amountRub = "amount_rub"
        case createdAt = "created_at"
    }
}

struct AdminRentalDetailsResponse: Decodable, Equatable {
    let rentalId: String
    let clientId: String
    let clientFullName: String
    let clientLogin: String?
    let clientPassword: String?
    let bikeId: String
    let bikeModel: String
    let bikeAvatarUrl: String
    let weeklyRateRub: Int
    let rentalStart: String
    let completedAt: String?
    let paidUntil: String
    let totalPaidRub: Int
    let debtRub: Int
    let totalAdjustmentRub: Int
    let rentalPipelineStatus: String
    let rentalIsActive: Bool
    let journalEntries: [AdminRentalJournalEntry]

    enum CodingKeys: String, CodingKey {
        case rentalId = "rental_id"
        case clientId = "client_id"
        case clientFullName = "client_full_name"
        case clientLogin = "client_login"
        case clientPassword = "client_password"
        case bikeId = "bike_id"
        case bikeModel = "bike_model"
        case bikeAvatarUrl = "bike_avatar_url"
        case weeklyRateRub = "weekly_rate_rub"
        case rentalStart = "rental_start"
        case completedAt = "completed_at"
        case paidUntil = "paid_until"
        case totalPaidRub = "total_paid_rub"
        case debtRub = "debt_rub"
        case totalAdjustmentRub = "total_adjustment_rub"
        case rentalPipelineStatus = "rental_pipeline_status"
        case rentalIsActive = "rental_is_active"
        case journalEntries = "journal_entries"
    }
}
