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
                await refreshAfterMutation(scope: .clientCatalog, openDetailsFor: nil)
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
        operationErrorMessage = nil
        operationSuccessMessage = nil
        let previousBike = bikes.first(where: { $0.bikeId == payload.bikeId })
        let optimisticBike = AdminBikeResponse(
            bikeId: payload.bikeId,
            photoUrl: payload.photoUrl,
            bikeModel: payload.bikeModel,
            weeklyRateRub: payload.weeklyRateRub,
            frameSerialNumber: payload.frameSerialNumber,
            motorSerialNumber: payload.motorSerialNumber,
            batterySerialNumber1: payload.batterySerialNumber1,
            batterySerialNumber2: payload.batterySerialNumber2,
            bikeIsInRental: previousBike?.bikeIsInRental ?? false
        )
        upsertBike(optimisticBike)
        onSuccess?()

        Task {
            do {
                let bike = try await apiService.updateAdminBike(
                    accessToken: session.accessToken,
                    payload: payload
                )
                operationSuccessMessage = "Велосипед обновлен: \(bike.bikeModel)"
                upsertBike(bike)
            } catch {
                if let previousBike,
                   bikes.first(where: { $0.bikeId == payload.bikeId }) == optimisticBike {
                    upsertBike(previousBike)
                }
                operationErrorMessage = error.localizedDescription
            }
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
                await refreshAfterMutation(scope: .bikes, openDetailsFor: nil)
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
                await refreshAfterMutation(scope: .clientCatalog, openDetailsFor: clientId)
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
                // Удаление клиента: каталог клиентов точно меняется, имена в
                // карточках аренд тоже могут поменяться → catalog + rents.
                await refreshAfterMutation(scope: [.clientCatalog, .rents], openDetailsFor: nil)
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
                // Новая аренда: bike занялся (bikes), client.rentalIsActive
                // флипнулся (catalog), новая карточка в rents.
                await refreshAfterMutation(scope: .all, openDetailsFor: payload.clientId)
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
                // Обновили rental (bike/period) — bike мог поменяться, поэтому полный scope.
                await refreshAfterMutation(scope: .all, openDetailsFor: payload.clientId)
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

        // IN_STOCK lifecycle-аренда не имеет активного клиента, в этом случае
        // вызывающий передаёт пустой clientId — refreshAfterMutation тогда
        // не пытается реоткрыть карточку клиента (этого клиента может уже не
        // существовать или это просто не нужно).
        let openClientId: String? = clientId.isEmpty ? nil : clientId

        Task {
            do {
                let result = try await apiService.deleteAdminRental(
                    accessToken: session.accessToken,
                    rentalId: rentalId
                )
                if result.deleted {
                    operationSuccessMessage = "Велосипед выведен из эксплуатации"
                } else {
                    operationSuccessMessage = "Удаление завершено"
                }
                // Delete rental — bike освобождается, клиент мог стать «свободным».
                await refreshAfterMutation(scope: .all, openDetailsFor: openClientId)
            } catch let backendError as BackendError {
                if case .httpError(let code, _) = backendError, code == 404 {
                    // Параллельное удаление другим админом или устаревший
                    // список: вместо алёрта-ошибки молча рефрешим состояние
                    // и сообщаем нейтрально. Согласно docs/14_rental_lifecycle.md §7
                    // карточка lifecycle-аренды уже исчезла с главного экрана.
                    operationSuccessMessage = "Аренда уже удалена"
                    await refreshAfterMutation(scope: .all, openDetailsFor: openClientId)
                } else {
                    operationErrorMessage = backendError.localizedDescription
                }
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
                // Только pipeline status — затрагивает rents (цвет рамки/фильтр).
                await refreshAfterMutation(scope: .rents, openDetailsFor: nil)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func finishRental(clientId: String, rentalId: String, onSuccess: (() -> Void)? = nil) {
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
                // Finish: client.rentalIsActive флипнулся (catalog), rents меняется.
                await refreshAfterMutation(scope: .rentalMutation, openDetailsFor: nil)
                // onSuccess зовётся ТОЛЬКО после того как backend отработал finish
                // и мы освежили списки. Без этого call-сайт зовёт openRentalDetails
                // параллельно с finish и получает старые details (race condition).
                await MainActor.run {
                    onSuccess?()
                }
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    func startClientRentalInExisting(
        rentalId: String,
        clientId: String,
        login: String,
        password: String,
        periodStart: String,
        onSuccess: (() -> Void)? = nil
    ) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                try await apiService.startAdminClientRentalInExisting(
                    accessToken: session.accessToken,
                    rentalId: rentalId,
                    clientId: clientId,
                    login: login,
                    password: password,
                    periodStart: periodStart
                )
                operationSuccessMessage = "Новая клиентская аренда запущена"
                // Запуск новой client_rental в существующей lifecycle:
                // catalog + rents (статус flipped, новый клиент в карточке).
                await refreshAfterMutation(scope: .rentalMutation, openDetailsFor: nil)
                await MainActor.run {
                    onSuccess?()
                }
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
                // Корректировка отражается в долге → rents (debt-индикатор на карточке).
                await refreshAfterMutation(scope: .rents, openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    /// Admin-операция над перенесённым долгом клиента
    /// (docs/14_rental_lifecycle.md §7, docs/04_api_draft.md «Admin: carried debt operations»).
    /// Для `payment` излишек автоматически уходит в активную клиентскую аренду — backend
    /// возвращает разбивку, которую мы показываем в success-сообщении.
    func applyCarriedDebt(
        clientId: String,
        amountRub: Int,
        kind: CarriedDebtOperationKind,
        comment: String?
    ) {
        isOperationInProgress = true
        operationErrorMessage = nil
        operationSuccessMessage = nil

        Task {
            do {
                let result = try await apiService.applyCarriedDebtOperation(
                    accessToken: session.accessToken,
                    clientId: clientId,
                    amountRub: amountRub,
                    kind: kind,
                    comment: comment
                )
                operationSuccessMessage = buildCarriedDebtSuccessMessage(kind: kind, result: result)
                // carriedDebt отображается в details клиента + влияет на возможный
                // долг активной аренды (для excess) → catalog (carriedDebtRub) + rents.
                await refreshAfterMutation(scope: .rentalMutation, openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    private func buildCarriedDebtSuccessMessage(
        kind: CarriedDebtOperationKind,
        result: CarriedDebtOperationResult
    ) -> String {
        switch kind {
        case .writeoff:
            return "Списано \(result.appliedToCarriedRub) ₽. Перенесённый долг: \(result.carriedDebtRub) ₽"
        case .payment:
            if result.appliedToActiveRentalRub > 0 {
                return "Принято \(result.appliedToCarriedRub + result.appliedToActiveRentalRub) ₽: \(result.appliedToCarriedRub) ₽ в перенесённый долг, \(result.appliedToActiveRentalRub) ₽ в активную аренду"
            }
            return "Принято \(result.appliedToCarriedRub) ₽. Перенесённый долг: \(result.carriedDebtRub) ₽"
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
                // Комментарий — только в карточке rental (отображается в /admin/rents).
                await refreshAfterMutation(scope: .rents, openDetailsFor: clientId)
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
                // Ссылки видны только в details — main listing не меняется.
                // Но обновим rents на случай если они туда попадут в виде индикаторов.
                await refreshAfterMutation(scope: .rents, openDetailsFor: clientId)
            } catch {
                operationErrorMessage = error.localizedDescription
            }
            isOperationInProgress = false
        }
    }

    /// Какие списки нужно перезагрузить после мутации. Раньше каждая операция
    /// тянула все 3 списка + opened client details = 4 HTTP-запроса. Это давало
    /// видимый лаг — особенно после правки rental comment или adjust debt,
    /// которые меняют только rents.
    struct RefreshScope: OptionSet, Sendable {
        let rawValue: Int
        static let rents = RefreshScope(rawValue: 1 << 0)
        static let clientCatalog = RefreshScope(rawValue: 1 << 1)
        static let bikes = RefreshScope(rawValue: 1 << 2)
        /// «Стандартный» scope для мутаций на rental: список аренд + каталог
        /// клиентов (т.к. client.rentalIsActive может поменяться).
        static let rentalMutation: RefreshScope = [.rents, .clientCatalog]
        /// Полный refresh — для крупных операций (deleteRental, удаление клиента, etc).
        static let all: RefreshScope = [.rents, .clientCatalog, .bikes]
    }

    /// Параллельно перезагружает выбранные списки. Open client details обновляется,
    /// только если sheet уже был открыт (selectedClientDetails != nil) ИЛИ caller
    /// явно передал clientId. На сетевую ошибку refresh'а НЕ сбрасываем
    /// `state = .failed` — оставляем существующие данные, ошибку показываем
    /// в operationErrorMessage.
    private func refreshAfterMutation(
        scope: RefreshScope = .rentalMutation,
        openDetailsFor clientId: String? = nil
    ) async {
        // Details обновляем ТОЛЬКО если sheet клиентских деталей сейчас открыт.
        // Раньше мы делали лишний request на details даже если карточка закрыта.
        let normalizedClientId = clientId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let detailsClientId: String? = (normalizedClientId?.isEmpty == false && selectedClientDetails != nil)
            ? normalizedClientId
            : nil
        // Если открыт экран деталей аренды — тоже его перезаливаем. Без этого
        // selectedRentalDetails оставался устаревшим (login/password/статус
        // прежней client_rental), и при последующем «Создать новую!» payload
        // мог уйти с уже неактуальными данными.
        let rentalDetailsId: String? = selectedRentalDetails?.rentalId
        let accessToken = session.accessToken
        let api = apiService

        do {
            try await withThrowingTaskGroup(of: PartialRefreshResult.self) { group in
                if scope.contains(.rents) {
                    group.addTask { .rents(try await api.fetchAdminRents(accessToken: accessToken)) }
                }
                if scope.contains(.clientCatalog) {
                    group.addTask { .catalog(try await api.fetchAdminClientCatalog(accessToken: accessToken)) }
                }
                if scope.contains(.bikes) {
                    group.addTask { .bikes(try await api.fetchAdminBikes(accessToken: accessToken)) }
                }
                if let id = detailsClientId {
                    group.addTask {
                        do {
                            return .details(try await api.fetchAdminClientDetails(accessToken: accessToken, clientId: id))
                        } catch {
                            // Details — не критичны для refresh, ошибку проглатываем.
                            return .details(nil)
                        }
                    }
                }
                if let rentalId = rentalDetailsId {
                    group.addTask {
                        do {
                            return .rentalDetails(try await api.fetchAdminRentalDetails(accessToken: accessToken, rentalId: rentalId))
                        } catch {
                            return .rentalDetails(nil)
                        }
                    }
                }

                for try await partial in group {
                    apply(partial)
                }
            }
        } catch {
            // Refresh упал — НЕ сбрасываем state в .failed (мутация-то могла
            // быть успешной). Просто оставляем существующие данные и
            // показываем сообщение об ошибке refresh'а.
            if case .loaded = state {
                operationErrorMessage = "Не удалось обновить список: \(error.localizedDescription)"
            } else {
                state = .failed(error.localizedDescription)
            }
        }
    }

    private enum PartialRefreshResult {
        case rents([AdminClientSummaryResponse])
        case catalog([AdminClientSummaryResponse])
        case bikes([AdminBikeResponse])
        case details(AdminClientDetailsResponse?)
        case rentalDetails(AdminRentalDetailsResponse?)
    }

    private func apply(_ partial: PartialRefreshResult) {
        switch partial {
        case .rents(let value):
            state = .loaded(value)
        case .catalog(let value):
            clientCatalog = value
        case .bikes(let value):
            bikes = value
        case .details(let value):
            if let value {
                selectedClientDetails = value
            }
        case .rentalDetails(let value):
            if let value {
                selectedRentalDetails = value
            }
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
