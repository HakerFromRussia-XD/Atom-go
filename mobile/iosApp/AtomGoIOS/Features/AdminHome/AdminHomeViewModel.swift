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
    @Published private(set) var clientCatalog: [AdminClientSummaryResponse] = []
    @Published private(set) var bikes: [AdminBikeResponse] = []
    @Published private(set) var selectedClientDetails: AdminClientDetailsResponse?
    @Published private(set) var selectedRentalDetails: AdminRentalDetailsResponse?
    @Published var detailsErrorMessage: String?
    @Published var rentalDetailsErrorMessage: String?
    @Published var operationErrorMessage: String?
    @Published var operationSuccessMessage: String?
    @Published var isDetailsLoading = false
    @Published var isRentalDetailsLoading = false
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
                async let clientsTask = apiService.fetchAdminRents(accessToken: session.accessToken)
                async let clientCatalogTask = apiService.fetchAdminClientCatalog(accessToken: session.accessToken)
                async let bikesTask = apiService.fetchAdminBikes(accessToken: session.accessToken)
                let clients = try await clientsTask
                let loadedClientCatalog = try await clientCatalogTask
                let loadedBikes = try await bikesTask
                state = .loaded(clients)
                clientCatalog = loadedClientCatalog
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

    func openRentalDetails(rentalId: String) {
        isRentalDetailsLoading = true
        rentalDetailsErrorMessage = nil

        Task {
            do {
                selectedRentalDetails = try await apiService.fetchAdminRentalDetails(
                    accessToken: session.accessToken,
                    rentalId: rentalId
                )
            } catch {
                rentalDetailsErrorMessage = error.localizedDescription
            }
            isRentalDetailsLoading = false
        }
    }

    func closeRentalDetails() {
        selectedRentalDetails = nil
        rentalDetailsErrorMessage = nil
    }

    func refreshClientCatalog(onSuccess: (() -> Void)? = nil) {
        operationErrorMessage = nil

        Task {
            do {
                clientCatalog = try await apiService.fetchAdminClientCatalog(accessToken: session.accessToken)
                await MainActor.run {
                    onSuccess?()
                }
            } catch {
                operationErrorMessage = error.localizedDescription
            }
        }
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
                upsertBike(bike)
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

    func deleteBike(bikeId: String, onSuccess: (() -> Void)? = nil) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let result = try await apiService.deleteAdminBike(
                    accessToken: session.accessToken,
                    bikeId: bikeId
                )
                operationSuccessMessage = result.deleted ? "Велосипед удален" : "Удаление завершено"
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

    func deleteClient(clientId: String, onSuccess: (() -> Void)? = nil) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let result = try await apiService.deleteAdminClient(
                    accessToken: session.accessToken,
                    clientId: clientId
                )
                operationSuccessMessage = result.deleted ? "Клиент удален" : "Удаление завершено"
                selectedClientDetails = nil
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

    func createRental(payload: CreateRentalPayload, onSuccess: ((AdminRentalHistoryItem) -> Void)? = nil) {
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
                await MainActor.run {
                    onSuccess?(rental)
                }
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

    func updateRentalPipelineStatus(clientId: String, rentalId: String, pipelineStatus: String) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                try await apiService.updateAdminRentalPipelineStatus(
                    accessToken: session.accessToken,
                    rentalId: rentalId,
                    pipelineStatus: pipelineStatus
                )
                operationSuccessMessage = "Статус аренды обновлен"
                await refreshAfterMutation(openDetailsFor: nil)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func finishRental(clientId: String, rentalId: String) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                try await apiService.finishAdminRental(
                    accessToken: session.accessToken,
                    rentalId: rentalId
                )
                operationSuccessMessage = "Аренда завершена"
                await refreshAfterMutation(openDetailsFor: nil)
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
            async let clientsTask = apiService.fetchAdminRents(accessToken: session.accessToken)
            async let clientCatalogTask = apiService.fetchAdminClientCatalog(accessToken: session.accessToken)
            async let bikesTask = apiService.fetchAdminBikes(accessToken: session.accessToken)
            let clients = try await clientsTask
            let loadedClientCatalog = try await clientCatalogTask
            let loadedBikes = try await bikesTask
            state = .loaded(clients)
            clientCatalog = loadedClientCatalog
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

    private func upsertBike(_ bike: AdminBikeResponse) {
        if let index = bikes.firstIndex(where: { $0.bikeId == bike.bikeId }) {
            bikes[index] = bike
        } else {
            bikes.append(bike)
        }
        bikes.sort { $0.bikeModel.localizedCaseInsensitiveCompare($1.bikeModel) == .orderedAscending }
    }
}
