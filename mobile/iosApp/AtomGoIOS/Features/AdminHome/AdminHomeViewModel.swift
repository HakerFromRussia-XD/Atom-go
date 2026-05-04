import Foundation

enum AdminHomeState {
    case idle
    case loading
    case loaded([AdminClientSummaryResponse])
    case failed(String)
}

@MainActor
final class AdminHomeViewModel: ObservableObject {
    @Published private(set) var state: AdminHomeState = .idle
    @Published private(set) var bikes: [AdminBikeResponse] = []
    @Published private(set) var selectedClientDetails: AdminClientDetailsResponse?
    @Published var detailsErrorMessage: String?
    @Published var operationErrorMessage: String?
    @Published var operationSuccessMessage: String?
    @Published var isDetailsLoading = false
    @Published var isOperationInProgress = false

    let session: AuthSession
    private let apiService: BackendServicing

    init(session: AuthSession, apiService: BackendServicing) {
        self.session = session
        self.apiService = apiService
    }

    func load() {
        state = .loading
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                async let clientsTask = apiService.fetchAdminClients(accessToken: session.accessToken)
                async let bikesTask = apiService.fetchAdminBikes(accessToken: session.accessToken)
                let clients = try await clientsTask
                let loadedBikes = try await bikesTask
                state = .loaded(clients)
                bikes = loadedBikes
            } catch {
                state = .failed(error.localizedDescription)
            }
        }
    }

    func openClientDetails(clientId: String) {
        isDetailsLoading = true
        detailsErrorMessage = nil

        Task {
            do {
                selectedClientDetails = try await apiService.fetchAdminClientDetails(
                    accessToken: session.accessToken,
                    clientId: clientId
                )
            } catch {
                detailsErrorMessage = error.localizedDescription
            }
            isDetailsLoading = false
        }
    }

    func closeClientDetails() {
        selectedClientDetails = nil
        detailsErrorMessage = nil
    }

    func createClient(payload: CreateClientPayload, onSuccess: (() -> Void)? = nil) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let details = try await apiService.createAdminClient(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Клиент создан: \(details.fullName)"
                await refreshAfterMutation(openDetailsFor: details.clientId)
                await MainActor.run {
                    onSuccess?()
                }
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func createBike(payload: CreateBikePayload, onSuccess: (() -> Void)? = nil) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let bike = try await apiService.createAdminBike(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Велосипед создан: \(bike.bikeModel)"
                await refreshAfterMutation(openDetailsFor: nil)
                await MainActor.run {
                    onSuccess?()
                }
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func updateBike(payload: UpdateBikePayload, onSuccess: (() -> Void)? = nil) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let bike = try await apiService.updateAdminBike(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Велосипед обновлен: \(bike.bikeModel)"
                await refreshAfterMutation(openDetailsFor: nil)
                await MainActor.run {
                    onSuccess?()
                }
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func updateClientProfile(clientId: String, payload: UpdateClientProfilePayload) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let details = try await apiService.updateAdminClientProfile(
                    accessToken: session.accessToken,
                    clientId: clientId,
                    payload: payload
                )
                operationSuccessMessage = "Профиль клиента обновлен: \(details.fullName)"
                await refreshAfterMutation(openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func createRental(payload: CreateRentalPayload) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let rental = try await apiService.createAdminRental(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Аренда создана: \(rental.periodStart)"
                await refreshAfterMutation(openDetailsFor: payload.clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func updateRental(payload: UpdateRentalPayload) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let rental = try await apiService.updateAdminRental(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Аренда обновлена: \(rental.periodStart)"
                await refreshAfterMutation(openDetailsFor: payload.clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func deleteRental(clientId: String, rentalId: String) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let result = try await apiService.deleteAdminRental(
                    accessToken: session.accessToken,
                    rentalId: rentalId
                )
                if result.deleted {
                    operationSuccessMessage = "Аренда удалена: \(result.rentalId)"
                } else {
                    operationSuccessMessage = "Удаление завершено"
                }
                await refreshAfterMutation(openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func adjustDebt(clientId: String, amountRub: Int, sign: DebtAdjustmentSign, comment: String?) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let result = try await apiService.adjustAdminClientDebt(
                    accessToken: session.accessToken,
                    clientId: clientId,
                    amountRub: amountRub,
                    sign: sign,
                    comment: comment
                )
                operationSuccessMessage = "Корректировка сохранена. Новый долг: \(result.debtRub) ₽"
                await refreshAfterMutation(openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func updateRentalComment(clientId: String, rentalId: String, comment: String) {
        let normalizedComment = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedComment.isEmpty else {
            operationErrorMessage = "Комментарий не может быть пустым"
            operationSuccessMessage = nil
            return
        }

        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                _ = try await apiService.updateAdminRentalComment(
                    accessToken: session.accessToken,
                    rentalId: rentalId,
                    comment: normalizedComment
                )
                operationSuccessMessage = "Комментарий сохранен"
                await refreshAfterMutation(openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func updateRentalLinks(clientId: String, rentalId: String, videoUrl: String?, contractUrl: String?) {
        let normalizedVideoUrl = videoUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedContractUrl = contractUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
        let safeVideoUrl = (normalizedVideoUrl?.isEmpty == true) ? nil : normalizedVideoUrl
        let safeContractUrl = (normalizedContractUrl?.isEmpty == true) ? nil : normalizedContractUrl

        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                _ = try await apiService.updateAdminRentalLinks(
                    accessToken: session.accessToken,
                    rentalId: rentalId,
                    videoUrl: safeVideoUrl,
                    contractUrl: safeContractUrl
                )
                operationSuccessMessage = "Ссылки аренды обновлены"
                await refreshAfterMutation(openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    private func refreshAfterMutation(openDetailsFor clientId: String?) async {
        do {
            async let clientsTask = apiService.fetchAdminClients(accessToken: session.accessToken)
            async let bikesTask = apiService.fetchAdminBikes(accessToken: session.accessToken)
            let clients = try await clientsTask
            let loadedBikes = try await bikesTask
            state = .loaded(clients)
            bikes = loadedBikes
            if let clientId {
                selectedClientDetails = try? await apiService.fetchAdminClientDetails(
                    accessToken: session.accessToken,
                    clientId: clientId
                )
            }
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
