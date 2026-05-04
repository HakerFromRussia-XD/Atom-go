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
    @Published var paymentErrorMessage: String?
    @Published var paymentResult: PaymentCreationResponse?

    let session: AuthSession
    private let apiService: BackendServicing

    init(session: AuthSession, apiService: BackendServicing) {
        self.session = session
        self.apiService = apiService
    }

    func load() {
        clearPaymentMessages()
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
        guard case let .loaded(dashboard) = state else { return }
        if type == .debtExact && dashboard.presets.debtExactRub <= 0 {
            paymentErrorMessage = "Долг отсутствует, оплата ровно долга недоступна."
            return
        }

        paymentErrorMessage = nil
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

    func clearPaymentMessages() {
        paymentErrorMessage = nil
        paymentResult = nil
    }
}
