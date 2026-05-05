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
    let rentalStart: String
    let paidUntil: String
    let debtRub: Int
    let totalAdjustmentRub: Int
    let presets: ClientPaymentPresets

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case bikeModel = "bike_model"
        case rentalStart = "rental_start"
        case paidUntil = "paid_until"
        case debtRub = "debt_rub"
        case totalAdjustmentRub = "total_adjustment_rub"
        case presets
    }
}

struct AdminClientSummaryResponse: Decodable, Identifiable {
    let clientId: String
    let clientLogin: String?
    let fullName: String
    let bikeModel: String
    let bikeAvatarUrl: String
    let statusText: String
    let paidUntil: String?
    let debtRub: Int
    let profitRub: Int
    let totalAdjustmentRub: Int

    var id: String { clientId }

    init(
        clientId: String,
        clientLogin: String?,
        fullName: String,
        bikeModel: String,
        bikeAvatarUrl: String,
        statusText: String,
        paidUntil: String? = nil,
        debtRub: Int,
        profitRub: Int,
        totalAdjustmentRub: Int
    ) {
        self.clientId = clientId
        self.clientLogin = clientLogin
        self.fullName = fullName
        self.bikeModel = bikeModel
        self.bikeAvatarUrl = bikeAvatarUrl
        self.statusText = statusText
        self.paidUntil = paidUntil
        self.debtRub = debtRub
        self.profitRub = profitRub
        self.totalAdjustmentRub = totalAdjustmentRub
    }

    enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case clientLogin = "client_login"
        case fullName = "full_name"
        case bikeModel = "bike_model"
        case bikeAvatarUrl = "bike_avatar_url"
        case statusText = "status_text"
        case paidUntil = "paid_until"
        case debtRub = "debt_rub"
        case profitRub = "profit_rub"
        case totalAdjustmentRub = "total_adjustment_rub"
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

    enum CodingKeys: String, CodingKey {
        case paymentId = "payment_id"
        case amountRub = "amount_rub"
        case confirmationUrl = "confirmation_url"
        case idempotenceKey = "idempotence_key"
        case status
    }
}

struct PaymentStatusResponse: Decodable, Equatable {
    let paymentId: String
    let amountRub: Int
    let confirmationUrl: String
    let providerPaymentId: String?
    let status: String
    let debtRub: Int?

    enum CodingKeys: String, CodingKey {
        case paymentId = "payment_id"
        case amountRub = "amount_rub"
        case confirmationUrl = "confirmation_url"
        case providerPaymentId = "provider_payment_id"
        case status
        case debtRub = "debt_rub"
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
