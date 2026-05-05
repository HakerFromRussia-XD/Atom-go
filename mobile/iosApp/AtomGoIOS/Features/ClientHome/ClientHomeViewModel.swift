import Foundation

enum ClientHomeState {
    case idle
    case loading
    case loaded(ClientDashboardResponse)
    case failed(String)
}

@MainActor
final class ClientHomeViewModel: ObservableObject {
    @Published private(set) var state: ClientHomeState = .idle
    @Published private(set) var isCreatingPayment = false
    @Published private(set) var isRefreshingPaymentStatus = false
    @Published var paymentErrorMessage: String?
    @Published var paymentResult: PaymentCreationResponse?
    @Published var paymentStatusMessage: String?

    let session: AuthSession
    private let apiService: BackendServicing

    init(session: AuthSession, apiService: BackendServicing) {
        self.session = session
        self.apiService = apiService
    }

    func load(clearPaymentMessages shouldClearPaymentMessages: Bool = true) {
        if shouldClearPaymentMessages {
            clearPaymentMessages()
        }
        state = .loading

        Task {
            do {
                let dashboard = try await apiService.fetchClientDashboard(accessToken: session.accessToken)
                state = .loaded(dashboard)
            } catch {
                state = .failed(error.localizedDescription)
            }
        }
    }

    func createPayment(type: ClientPaymentType) {
        guard !isCreatingPayment else { return }
        guard case let .loaded(dashboard) = state else { return }
        if type == .debtExact && dashboard.presets.debtExactRub <= 0 {
            paymentErrorMessage = "Долг отсутствует, оплата ровно долга недоступна."
            return
        }

        paymentErrorMessage = nil
        paymentStatusMessage = nil
        paymentResult = nil
        isCreatingPayment = true

        Task {
            do {
                let result = try await apiService.createPayment(
                    accessToken: session.accessToken,
                    paymentType: type
                )
                paymentResult = result
            } catch {
                paymentErrorMessage = error.localizedDescription
            }
            isCreatingPayment = false
        }
    }

    func refreshPaymentStatus(paymentId: String? = nil) {
        let resolvedPaymentId = paymentId ?? paymentResult?.paymentId
        guard let resolvedPaymentId, !resolvedPaymentId.isEmpty else { return }
        guard !isRefreshingPaymentStatus else { return }

        isRefreshingPaymentStatus = true
        paymentErrorMessage = nil

        Task {
            do {
                let status = try await apiService.fetchPaymentStatus(
                    accessToken: session.accessToken,
                    paymentId: resolvedPaymentId
                )
                paymentResult = PaymentCreationResponse(
                    paymentId: status.paymentId,
                    amountRub: status.amountRub,
                    confirmationUrl: status.confirmationUrl,
                    idempotenceKey: paymentResult?.idempotenceKey ?? "",
                    status: status.status
                )
                switch status.status {
                case "succeeded":
                    paymentStatusMessage = "Платеж успешно прошел. Данные аренды обновлены."
                    load(clearPaymentMessages: false)
                case "canceled", "failed":
                    paymentStatusMessage = nil
                    paymentErrorMessage = "Платеж не прошел. Деньги не начислены."
                    load(clearPaymentMessages: false)
                default:
                    paymentStatusMessage = "Платеж пока ожидает подтверждения ЮKassa."
                }
            } catch {
                paymentErrorMessage = error.localizedDescription
            }
            isRefreshingPaymentStatus = false
        }
    }

    func clearPaymentMessages() {
        paymentErrorMessage = nil
        paymentStatusMessage = nil
        paymentResult = nil
    }
}
