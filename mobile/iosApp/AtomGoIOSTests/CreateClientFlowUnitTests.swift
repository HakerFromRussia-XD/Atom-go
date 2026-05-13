import XCTest
@testable import AtomGoIOS

final class CreateClientFlowUnitTests: XCTestCase {
    func testAdminClientSummaryUsesRentalIdAsIdentityForInStockCards() {
        let first = AdminClientSummaryResponse(
            clientId: "",
            rentalId: "rental-in-stock-1",
            clientLogin: nil,
            fullName: "",
            bikeModel: "№16 Model X",
            bikeAvatarUrl: "",
            statusText: "У меня",
            paidUntil: nil,
            rentalPipelineStatus: "in_stock",
            rentalIsActive: false,
            debtRub: 0,
            profitRub: 0,
            totalAdjustmentRub: 0
        )
        let second = AdminClientSummaryResponse(
            clientId: "",
            rentalId: "rental-in-stock-2",
            clientLogin: nil,
            fullName: "",
            bikeModel: "№13 Aventon Level 2",
            bikeAvatarUrl: "",
            statusText: "У меня",
            paidUntil: nil,
            rentalPipelineStatus: "in_stock",
            rentalIsActive: false,
            debtRub: 0,
            profitRub: 0,
            totalAdjustmentRub: 0
        )

        XCTAssertEqual(first.id, "rental-in-stock-1")
        XCTAssertEqual(second.id, "rental-in-stock-2")
        XCTAssertNotEqual(first.id, second.id)
    }

    func testClientDashboardDecodesWhenHistoricalFieldsAreMissing() throws {
        let payload = """
        {
          "client_id":"client-legacy-1",
          "bike_model":"Legacy Bike",
          "bike_avatar_url":"",
          "rental_start":"2026-05-01",
          "paid_until":"2026-05-07",
          "debt_rub":0,
          "balance_rub":0,
          "total_adjustment_rub":0,
          "presets":{
            "day_rub":500,
            "week_rub":3500,
            "two_weeks_rub":7000,
            "month_rub":14000,
            "debt_exact_rub":0
          },
          "tax_mode":"self_employed",
          "requires_receipt_email":false
        }
        """

        let data = try XCTUnwrap(payload.data(using: .utf8))
        let decoded = try JSONDecoder().decode(ClientDashboardResponse.self, from: data)

        XCTAssertEqual(decoded.clientId, "client-legacy-1")
        XCTAssertEqual(decoded.completedAt, nil)
        XCTAssertFalse(decoded.rentalIsActive)
    }

    func testValidatorBuildsPayloadForValidInput() {
        let input = CreateClientFormInput(
            fullName: " Roman Sergeev ",
            address: "Moscow, 123",
            passportData: "1234 567890",
            phones: [
                CreateClientPhoneInput(label: "Рабочий (TG)", number: "89859325907"),
                CreateClientPhoneInput(label: " ", number: " ")
            ]
        )

        let result = CreateClientFormValidator.buildPayload(from: input)
        switch result {
        case let .success(payload):
            XCTAssertEqual(payload.fullName, "Roman Sergeev")
            XCTAssertEqual(payload.address, "Moscow, 123")
            XCTAssertEqual(payload.passportData, "1234 567890")
            XCTAssertEqual(payload.phones.count, 1)
            XCTAssertEqual(payload.phones.first?.label, "Рабочий (TG)")
            XCTAssertEqual(payload.phones.first?.number, "89859325907")
        case let .failure(error):
            XCTFail("Expected success, got validation error: \(String(describing: error.errorDescription))")
        }
    }

    func testValidatorRejectsBlankFullName() {
        let input = CreateClientFormInput(
            fullName: " ",
            address: "Moscow, 123",
            passportData: "1234 567890",
            phones: []
        )

        let result = CreateClientFormValidator.buildPayload(from: input)
        XCTAssertEqual(result.failureValue, .missingFullName)
        XCTAssertEqual(result.failureValue?.errorDescription, "Укажите ФИО клиента")
    }

    func testBikeSerialValidatorRejectsDuplicateAgainstExistingBike() {
        let existingBikes = [
            AdminBikeResponse(
                bikeId: "bike-1",
                photoUrl: nil,
                bikeModel: "Monster",
                weeklyRateRub: 3000,
                frameSerialNumber: "FRAME-100",
                motorSerialNumber: "MOTOR-100",
                batterySerialNumber1: "BAT-100",
                batterySerialNumber2: "BAT-101"
            )
        ]

        let error = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: existingBikes,
            bikeIdToIgnore: nil,
            frameSerial: "FRAME-200",
            motorSerial: "MOTOR-100",
            batterySerialNumber1: "BAT-200",
            batterySerialNumber2: nil
        )

        XCTAssertEqual(error, "Серийный номер мотора уже используется в другом велосипеде")
    }

    func testBikeSerialValidatorAllowsSameBikeWhenEditing() {
        let existingBikes = [
            AdminBikeResponse(
                bikeId: "bike-1",
                photoUrl: nil,
                bikeModel: "Monster",
                weeklyRateRub: 3000,
                frameSerialNumber: "FRAME-100",
                motorSerialNumber: "MOTOR-100",
                batterySerialNumber1: "BAT-100",
                batterySerialNumber2: "BAT-101"
            )
        ]

        let error = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: existingBikes,
            bikeIdToIgnore: "bike-1",
            frameSerial: "FRAME-100",
            motorSerial: "MOTOR-100",
            batterySerialNumber1: "BAT-100",
            batterySerialNumber2: "BAT-101"
        )

        XCTAssertNil(error)
    }

    func testBikeSerialValidatorRejectsDuplicateInsideSamePayload() {
        let error = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: [],
            bikeIdToIgnore: nil,
            frameSerial: "SER-1",
            motorSerial: "SER-1",
            batterySerialNumber1: "SER-2",
            batterySerialNumber2: nil
        )

        XCTAssertEqual(error, "Серийные номера внутри карточки велосипеда должны быть уникальными")
    }

    func testRentalLoginValidatorRejectsLoginFromAnotherClient() {
        let clients = [
            AdminClientSummaryResponse(
                clientId: "client-1",
                clientLogin: "client1",
                fullName: "One",
                bikeModel: "-",
                bikeAvatarUrl: "",
                statusText: "",
                debtRub: 0,
                profitRub: 0,
                totalAdjustmentRub: 0
            ),
            AdminClientSummaryResponse(
                clientId: "client-2",
                clientLogin: "client2",
                fullName: "Two",
                bikeModel: "-",
                bikeAvatarUrl: "",
                statusText: "",
                debtRub: 0,
                profitRub: 0,
                totalAdjustmentRub: 0
            )
        ]

        let error = AdminFormValidator.validateRentalLoginDuplicate(
            clients: clients,
            selectedClientId: "client-1",
            login: "client2"
        )

        XCTAssertEqual(error, "Логин уже привязан к другому клиенту. Укажите другой логин")
    }

    func testRentalLoginValidatorAllowsSameClientLogin() {
        let clients = [
            AdminClientSummaryResponse(
                clientId: "client-1",
                clientLogin: "roman",
                fullName: "Roman",
                bikeModel: "-",
                bikeAvatarUrl: "",
                statusText: "",
                debtRub: 0,
                profitRub: 0,
                totalAdjustmentRub: 0
            )
        ]

        let error = AdminFormValidator.validateRentalLoginDuplicate(
            clients: clients,
            selectedClientId: "client-1",
            login: "ROMAN"
        )
        XCTAssertNil(error)
    }

    func testRentalDetailsDisplayPolicyForActiveRentalShowsRealValues() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: true, isInStockState: false)

        XCTAssertEqual(policy.metricText(activeValue: "+3 500 ₽"), "+3 500 ₽")
        XCTAssertEqual(policy.correctionLineText(formattedAdjustment: "−1 000 ₽"), "Корректировка: −1 000 ₽")
        XCTAssertTrue(policy.showsJournalHistory)
        XCTAssertTrue(policy.adjustmentButtonEnabled)
    }

    func testRentalDetailsDisplayPolicyForMineStateShowsDashes() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: false, isInStockState: true)

        XCTAssertEqual(policy.metricText(activeValue: "+3 500 ₽"), "—")
        XCTAssertEqual(policy.correctionLineText(formattedAdjustment: "−1 000 ₽"), "Корректировка: —")
        XCTAssertFalse(policy.showsJournalHistory)
        XCTAssertFalse(policy.adjustmentButtonEnabled)
    }

    func testRentalDetailsDisplayPolicyForClosedClientRentalShowsValuesAndJournal() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: false, isInStockState: false)

        XCTAssertEqual(policy.metricText(activeValue: "+3 500 ₽"), "+3 500 ₽")
        XCTAssertEqual(policy.correctionLineText(formattedAdjustment: "−1 000 ₽"), "Корректировка: −1 000 ₽")
        XCTAssertTrue(policy.showsJournalHistory)
        XCTAssertFalse(policy.adjustmentButtonEnabled)
    }

    func testRentalDetailsDisplayPolicyReadOnlyCredentialPrefersServerValue() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: true, isInStockState: false)
        let value = policy.readOnlyCredentialText(serverValue: "client1", draftValue: "")
        XCTAssertEqual(value, "client1")
    }

    func testRentalDetailsDisplayPolicyReadOnlyCredentialFallsBackToDraft() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: true, isInStockState: false)
        let value = policy.readOnlyCredentialText(serverValue: nil, draftValue: "client_draft")
        XCTAssertEqual(value, "client_draft")
    }

    func testRentalDetailsDisplayPolicyReadOnlyCredentialReturnsDashWhenBothMissing() {
        let policy = RentalDetailsDisplayPolicy(rentalIsActive: true, isInStockState: false)
        let value = policy.readOnlyCredentialText(serverValue: " ", draftValue: " ")
        XCTAssertEqual(value, "—")
    }

    @MainActor
    func testViewModelKeepsAppAliveAndShowsNetworkErrorForCreateClient() async {
        let service = MockAdminBackendService()
        service.createClientResult = .failure(BackendError.network("The Internet connection appears to be offline."))
        let viewModel = makeViewModel(service: service)

        let payload = CreateClientPayload(
            fullName: "Roman Sergeev",
            address: "Moscow, 123",
            passportData: "1234 567890",
            phones: []
        )

        viewModel.createClient(payload: payload)
        await waitUntilOperationCompletes(viewModel)

        XCTAssertNotNil(viewModel.operationErrorMessage)
        XCTAssertTrue(viewModel.operationErrorMessage?.contains("Сетевая ошибка") == true)
        XCTAssertNil(viewModel.operationSuccessMessage)
        XCTAssertFalse(viewModel.isOperationInProgress)
    }

    @MainActor
    func testViewModelCreatesClientAndRefreshesList() async {
        let service = MockAdminBackendService()
        service.fetchRentsResult = .success([])
        service.fetchClientCatalogResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([service.sampleBike])
        service.createClientResult = .success(service.sampleDetails)
        service.fetchClientDetailsResult = .success(service.sampleDetails)

        let viewModel = makeViewModel(service: service)
        var didCallOnSuccess = false
        let payload = CreateClientPayload(
            fullName: "Roman Sergeev",
            address: "Moscow, 123",
            passportData: "1234 567890",
            phones: [AdminClientPhone(id: "1", label: "Рабочий (TG)", number: "89859325907")]
        )

        viewModel.createClient(payload: payload) {
            didCallOnSuccess = true
        }
        await waitUntilOperationCompletes(viewModel)

        XCTAssertTrue(didCallOnSuccess)
        XCTAssertEqual(viewModel.operationSuccessMessage, "Клиент создан: Roman Sergeev")
        if case let .loaded(items) = viewModel.state {
            XCTAssertEqual(items.count, 0)
        } else {
            XCTFail("Expected .loaded state after successful create")
        }
        XCTAssertEqual(viewModel.clientCatalog.count, 1)
        XCTAssertEqual(viewModel.clientCatalog.first?.fullName, "Roman Sergeev")
    }

    @MainActor
    func testViewModelSeparatesClientsCatalogFromRentsForIpAdmin() async {
        let service = MockAdminBackendService()
        service.fetchRentsResult = .success([])
        service.fetchClientCatalogResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([])
        let viewModel = AdminHomeViewModel(
            session: AuthSession(accessToken: "token", role: .admin, userId: "admin-ip-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilAdminHomeLoads(viewModel)

        if case let .loaded(rents) = viewModel.state {
            XCTAssertTrue(rents.isEmpty)
        } else {
            XCTFail("Expected .loaded state")
        }
        XCTAssertEqual(viewModel.clientCatalog.count, 1)
        XCTAssertEqual(viewModel.clientCatalog.first?.clientId, service.sampleSummary.clientId)
    }

    @MainActor
    func testViewModelCreateRentalShowsReadableDuplicateLoginError() async {
        let service = MockAdminBackendService()
        service.createRentalResult = .failure(
            BackendError.httpError(code: 409, body: #"{"message":"login is already used"}"#)
        )
        let viewModel = makeViewModel(service: service)

        viewModel.createRental(
            payload: CreateRentalPayload(
                clientId: "client-001",
                bikeId: "bike-001",
                login: "client2",
                password: "client123",
                periodStart: "2026-05-10",
                periodEnd: nil,
                videoUrl: nil,
                contractUrl: nil,
                comment: nil
            )
        )
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(viewModel.operationErrorMessage, "Логин уже занят. Укажите другой логин.")
        XCTAssertNil(viewModel.operationSuccessMessage)
        XCTAssertEqual(service.createRentalCallsCount, 1)
    }

    @MainActor
    func testViewModelCreatesRentalAndRefreshesDetails() async {
        let service = MockAdminBackendService()
        service.fetchClientsResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([service.sampleBike])
        service.fetchClientDetailsResult = .success(service.sampleDetails)
        service.createRentalResult = .success(
            AdminRentalHistoryItem(
                id: "rental-100",
                bikeId: "bike-001",
                bikeAvatarUrl: "",
                periodStart: "2026-05-05",
                periodEnd: "2026-05-20",
                bikeModel: "Монстер",
                videoUrl: "https://youtube.com/watch?v=rental-100",
                contractUrl: "https://drive.google.com/file/d/rental-100/view",
                comment: "Тест аренды",
                weeklyRateRub: 3000,
                totalPaidRub: 0,
                debtRub: 0,
                totalAdjustmentRub: 0
            )
        )
        let viewModel = makeViewModel(service: service)

        viewModel.createRental(
            payload: CreateRentalPayload(
                clientId: "client-001",
                bikeId: "bike-001",
                login: "client2",
                password: "client123",
                periodStart: "2026-05-05",
                periodEnd: "2026-05-20",
                videoUrl: "https://youtube.com/watch?v=rental-100",
                contractUrl: "https://drive.google.com/file/d/rental-100/view",
                comment: "Тест аренды"
            )
        )
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(viewModel.operationSuccessMessage, "Аренда создана: 2026-05-05")
        XCTAssertNil(viewModel.operationErrorMessage)
        XCTAssertEqual(service.createRentalCallsCount, 1)
        XCTAssertFalse(viewModel.isOperationInProgress)
    }

    @MainActor
    func testViewModelUpdatesRentalAndRefreshesDetails() async {
        let service = MockAdminBackendService()
        service.fetchClientsResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([service.sampleBike])
        service.fetchClientDetailsResult = .success(service.sampleDetails)
        service.updateRentalResult = .success(
            AdminRentalHistoryItem(
                id: "rental-100",
                bikeId: "bike-001",
                bikeAvatarUrl: "",
                periodStart: "2026-05-15",
                periodEnd: "2026-06-01",
                bikeModel: "Монстер",
                videoUrl: nil,
                contractUrl: nil,
                comment: nil,
                weeklyRateRub: 3000,
                totalPaidRub: 0,
                debtRub: 0,
                totalAdjustmentRub: 0
            )
        )
        let viewModel = makeViewModel(service: service)

        viewModel.updateRental(
            payload: UpdateRentalPayload(
                clientId: "client-001",
                rentalId: "rental-100",
                bikeId: "bike-001",
                periodStart: "2026-05-15",
                periodEnd: "2026-06-01"
            )
        )
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(viewModel.operationSuccessMessage, "Аренда обновлена: 2026-05-15")
        XCTAssertNil(viewModel.operationErrorMessage)
        XCTAssertEqual(service.updateRentalCallsCount, 1)
    }

    @MainActor
    func testViewModelDeletesRentalAndRefreshesDetails() async {
        let service = MockAdminBackendService()
        service.fetchClientsResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([service.sampleBike])
        service.fetchClientDetailsResult = .success(service.sampleDetails)
        service.deleteRentalResult = .success(DeleteRentalResult(rentalId: "rental-100", deleted: true))
        let viewModel = makeViewModel(service: service)

        viewModel.deleteRental(clientId: "client-001", rentalId: "rental-100")
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(viewModel.operationSuccessMessage, "Аренда удалена: rental-100")
        XCTAssertNil(viewModel.operationErrorMessage)
        XCTAssertEqual(service.deleteRentalCallsCount, 1)
    }

    @MainActor
    func testViewModelDeleteRentalShowsReadableError() async {
        let service = MockAdminBackendService()
        service.deleteRentalResult = .failure(
            BackendError.httpError(code: 404, body: #"{"message":"rental not found"}"#)
        )
        let viewModel = makeViewModel(service: service)

        viewModel.deleteRental(clientId: "client-001", rentalId: "rental-404")
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(viewModel.operationErrorMessage, "Аренда не найдена.")
        XCTAssertNil(viewModel.operationSuccessMessage)
        XCTAssertEqual(service.deleteRentalCallsCount, 1)
    }

    @MainActor
    func testViewModelCreateBikeShowsReadableDuplicateSerialError() async {
        let service = MockAdminBackendService()
        service.createBikeResult = .failure(
            BackendError.httpError(code: 409, body: #"{"message":"bike serial numbers are already used"}"#)
        )
        let viewModel = makeViewModel(service: service)

        viewModel.createBike(
            payload: CreateBikePayload(
                photoUrl: nil,
                bikeModel: "Монстер",
                weeklyRateRub: 3000,
                frameSerialNumber: "FRAME-1",
                motorSerialNumber: "MOTOR-1",
                batterySerialNumber1: "BAT-1",
                batterySerialNumber2: "BAT-2"
            )
        )
        await waitUntilOperationCompletes(viewModel)

        XCTAssertEqual(
            viewModel.operationErrorMessage,
            "Серийные номера уже используются в другом велосипеде."
        )
        XCTAssertNil(viewModel.operationSuccessMessage)
    }

    @MainActor
    func testViewModelCreatingBikeDoesNotPopulateRentList() async {
        let service = MockAdminBackendService()
        service.fetchRentsResult = .success([])
        service.fetchClientCatalogResult = .success([service.sampleSummary])
        service.fetchBikesResult = .success([])
        service.createBikeResult = .success(service.sampleBike)
        let viewModel = makeViewModel(service: service)

        viewModel.load()
        await waitUntilAdminHomeLoads(viewModel)
        service.fetchRentsResult = .success([service.sampleSummary])

        viewModel.createBike(
            payload: CreateBikePayload(
                photoUrl: nil,
                bikeModel: "Монстер",
                weeklyRateRub: 3000,
                frameSerialNumber: "FRAME-1",
                motorSerialNumber: "MOTOR-1",
                batterySerialNumber1: "BAT-1",
                batterySerialNumber2: "BAT-2"
            )
        )
        await waitUntilOperationCompletes(viewModel)

        if case let .loaded(rents) = viewModel.state {
            XCTAssertTrue(rents.isEmpty)
        } else {
            XCTFail("Expected .loaded state")
        }
        XCTAssertEqual(viewModel.bikes.count, 1)
        XCTAssertEqual(viewModel.bikes.first?.bikeId, service.sampleBike.bikeId)
        XCTAssertEqual(service.fetchAdminRentsCallsCount, 1)
    }

    func testBackendErrorParserMapsRentalIdRequired() {
        let error = BackendError.httpError(code: 400, body: #"{"message":"rentalId is required"}"#)
        XCTAssertEqual(error.localizedDescription, "Не указан идентификатор аренды.")
    }

    @MainActor
    func testClientPaymentCreateStoresYooKassaResult() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        service.createPaymentResult = .success(service.samplePayment)
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilClientDashboardLoads(viewModel)
        viewModel.createPayment(type: .week)
        await waitUntilPaymentCreateCompletes(viewModel)

        XCTAssertEqual(viewModel.paymentResult?.paymentId, "payment-001")
        XCTAssertEqual(viewModel.paymentResult?.confirmationUrl, "https://example.test/pay/payment-001")
        XCTAssertNil(viewModel.paymentErrorMessage)
        XCTAssertFalse(viewModel.isCreatingPayment)
    }

    @MainActor
    func testClientPaymentCreateWithReceiptEmailUpdatesEmailBeforePayment() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        service.createPaymentResult = .success(service.samplePayment)
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilClientDashboardLoads(viewModel)
        viewModel.createPayment(type: .week, receiptEmail: "client@example.com")
        await waitUntilPaymentCreateCompletes(viewModel)

        XCTAssertEqual(service.updateReceiptEmailCallsCount, 1)
        XCTAssertEqual(service.lastUpdatedReceiptEmail, "client@example.com")
        XCTAssertEqual(viewModel.paymentResult?.paymentId, "payment-001")
    }

    @MainActor
    func testIpPaymentWithYooKassaReceiptPendingShowsSuccessMessage() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        service.createPaymentResult = .success(
            PaymentCreationResponse(
                paymentId: "payment-ip-001",
                amountRub: 3000,
                confirmationUrl: "https://example.test/pay/payment-ip-001",
                idempotenceKey: "idem-ip-001",
                status: "pending",
                taxMode: "individual_entrepreneur",
                fiscalizationStatus: "yookassa_receipt_pending"
            )
        )
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilClientDashboardLoads(viewModel)
        viewModel.createPayment(type: .week)
        await waitUntilPaymentCreateCompletes(viewModel)

        XCTAssertNil(viewModel.paymentErrorMessage)
        XCTAssertEqual(
            viewModel.paymentStatusMessage,
            "Платеж создан. Чек будет отправлен на email после обработки ЮKassa."
        )
    }

    @MainActor
    func testIpPaymentWithoutReceiptRegistrationShowsFiscalizationWarning() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        service.createPaymentResult = .success(
            PaymentCreationResponse(
                paymentId: "payment-ip-002",
                amountRub: 3000,
                confirmationUrl: "https://example.test/pay/payment-ip-002",
                idempotenceKey: "idem-ip-002",
                status: "pending",
                taxMode: "individual_entrepreneur",
                fiscalizationStatus: "fiscalization_not_configured"
            )
        )
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilClientDashboardLoads(viewModel)
        viewModel.createPayment(type: .week)
        await waitUntilPaymentCreateCompletes(viewModel)

        XCTAssertNil(viewModel.paymentErrorMessage)
        XCTAssertEqual(
            viewModel.paymentStatusMessage,
            "Платеж создан. Чек 54-ФЗ не будет отправлен, пока в ЮKassa не настроена фискализация магазина."
        )
    }

    @MainActor
    func testClientHomeUpdateReceiptEmailCallsBackend() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.load()
        await waitUntilClientDashboardLoads(viewModel)
        viewModel.updateReceiptEmail("saved@example.com")
        await waitUntilReceiptEmailUpdateCalls(service, expected: 1)

        XCTAssertEqual(service.lastUpdatedReceiptEmail, "saved@example.com")
        XCTAssertEqual(viewModel.paymentStatusMessage, "Email для чека сохранен.")
        XCTAssertNil(viewModel.paymentErrorMessage)
    }

    @MainActor
    func testClientPaymentStatusSuccessRefreshesDashboard() async {
        let service = MockAdminBackendService()
        service.fetchClientDashboardResult = .success(service.sampleDashboard)
        service.paymentStatusResult = .success(
            PaymentStatusResponse(
                paymentId: "payment-001",
                amountRub: 3000,
                confirmationUrl: "https://example.test/pay/payment-001",
                providerPaymentId: "provider-payment-001",
                status: "succeeded",
                debtRub: 0
            )
        )
        let viewModel = ClientHomeViewModel(
            session: AuthSession(accessToken: "token", role: .client, userId: "user-client-001"),
            apiService: service
        )

        viewModel.paymentResult = service.samplePayment
        viewModel.refreshPaymentStatus(paymentId: "payment-001")
        await waitUntilPaymentStatusRefreshCompletes(viewModel)

        XCTAssertEqual(viewModel.paymentResult?.status, "succeeded")
        XCTAssertEqual(viewModel.paymentStatusMessage, "Платеж успешно прошел. Данные аренды обновлены.")
        XCTAssertNil(viewModel.paymentErrorMessage)
    }

    @MainActor
    private func makeViewModel(service: MockAdminBackendService) -> AdminHomeViewModel {
        AdminHomeViewModel(
            session: AuthSession(accessToken: "token", role: .admin, userId: "admin-001"),
            apiService: service
        )
    }

    @MainActor
    private func waitUntilOperationCompletes(_ viewModel: AdminHomeViewModel) async {
        for _ in 0 ..< 200 {
            if !viewModel.isOperationInProgress {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Operation did not complete in time")
    }

    @MainActor
    private func waitUntilAdminHomeLoads(_ viewModel: AdminHomeViewModel) async {
        for _ in 0 ..< 200 {
            if case .loaded = viewModel.state {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Admin home did not load in time")
    }

    @MainActor
    private func waitUntilClientDashboardLoads(_ viewModel: ClientHomeViewModel) async {
        for _ in 0 ..< 200 {
            if case .loaded = viewModel.state {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Client dashboard did not load in time")
    }

    @MainActor
    private func waitUntilPaymentCreateCompletes(_ viewModel: ClientHomeViewModel) async {
        for _ in 0 ..< 200 {
            if !viewModel.isCreatingPayment {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Payment create did not complete in time")
    }

    @MainActor
    private func waitUntilPaymentStatusRefreshCompletes(_ viewModel: ClientHomeViewModel) async {
        for _ in 0 ..< 200 {
            if !viewModel.isRefreshingPaymentStatus {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Payment status refresh did not complete in time")
    }

    @MainActor
    private func waitUntilReceiptEmailUpdateCalls(_ service: MockAdminBackendService, expected: Int) async {
        for _ in 0 ..< 200 {
            if service.updateReceiptEmailCallsCount >= expected {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Receipt email update did not complete in time")
    }
}

final class ClientDashboardPresentationLogicTests: XCTestCase {
    func testDebtDisplayShowsBalanceWhenDebtIsZeroForClosedRental() {
        let dashboard = makeDashboard(
            rentalIsActive: false,
            debtRub: 0,
            balanceRub: 3500
        )

        let display = ClientDashboardPresentationLogic.debtDisplay(for: dashboard)

        XCTAssertEqual(display.title, "ОСТАТОК")
        XCTAssertEqual(display.amountRub, 3500)
        XCTAssertFalse(display.isDebt)
    }

    private func makeDashboard(
        rentalIsActive: Bool,
        debtRub: Int,
        balanceRub: Int?
    ) -> ClientDashboardResponse {
        ClientDashboardResponse(
            clientId: "client-test",
            bikeModel: "Test bike",
            bikeAvatarUrl: nil,
            rentalStart: "2026-05-01",
            paidUntil: "2026-05-19",
            completedAt: rentalIsActive ? nil : "2026-05-10",
            rentalIsActive: rentalIsActive,
            debtRub: debtRub,
            balanceRub: balanceRub,
            totalAdjustmentRub: 0,
            presets: ClientPaymentPresets(dayRub: 500, weekRub: 3500, twoWeeksRub: 7000, monthRub: 14000, debtExactRub: max(0, debtRub)),
            taxMode: "self_employed",
            requiresReceiptEmail: false
        )
    }
}

private final class MockAdminBackendService: BackendServicing {
    var fetchClientsResult: Result<[AdminClientSummaryResponse], Error> = .success([])
    var fetchRentsResult: Result<[AdminClientSummaryResponse], Error> = .success([])
    var fetchClientCatalogResult: Result<[AdminClientSummaryResponse], Error> = .success([])
    var fetchBikesResult: Result<[AdminBikeResponse], Error> = .success([])
    var fetchRentalDetailsResult: Result<AdminRentalDetailsResponse, Error> = .failure(BackendError.invalidResponse)
    var fetchClientDashboardResult: Result<ClientDashboardResponse, Error> = .failure(BackendError.invalidResponse)
    var createClientResult: Result<AdminClientDetailsResponse, Error> = .failure(BackendError.invalidResponse)
    var fetchClientDetailsResult: Result<AdminClientDetailsResponse, Error> = .failure(BackendError.invalidResponse)
    var createBikeResult: Result<AdminBikeResponse, Error> = .failure(BackendError.invalidResponse)
    var createRentalResult: Result<AdminRentalHistoryItem, Error> = .failure(BackendError.invalidResponse)
    var updateBikeResult: Result<AdminBikeResponse, Error> = .failure(BackendError.invalidResponse)
    var updateRentalResult: Result<AdminRentalHistoryItem, Error> = .failure(BackendError.invalidResponse)
    var deleteRentalResult: Result<DeleteRentalResult, Error> = .failure(BackendError.invalidResponse)
    var createPaymentResult: Result<PaymentCreationResponse, Error> = .failure(BackendError.invalidResponse)
    var paymentStatusResult: Result<PaymentStatusResponse, Error> = .failure(BackendError.invalidResponse)
    var updateReceiptEmailCallsCount = 0
    var lastUpdatedReceiptEmail: String?
    var fetchAdminRentsCallsCount = 0
    var createRentalCallsCount = 0
    var updateRentalCallsCount = 0
    var deleteRentalCallsCount = 0

    let sampleSummary = AdminClientSummaryResponse(
        clientId: "client-001",
        clientLogin: "client1",
        fullName: "Roman Sergeev",
        bikeModel: "Монстер",
        bikeAvatarUrl: "",
        statusText: "Оплачено еще на 7 дн.",
        debtRub: 0,
        profitRub: 3000,
        totalAdjustmentRub: 0
    )

    let sampleBike = AdminBikeResponse(
        bikeId: "bike-001",
        photoUrl: nil,
        bikeModel: "Монстер",
        weeklyRateRub: 3000,
        frameSerialNumber: "FRAME-001",
        motorSerialNumber: "MOTOR-001",
        batterySerialNumber1: "BAT-001",
        batterySerialNumber2: "BAT-002"
    )

    let sampleDetails = AdminClientDetailsResponse(
        clientId: "client-001",
        fullName: "Roman Sergeev",
        address: "Moscow, 123",
        passportData: "1234 567890",
        weeklyRateRub: 3000,
        bikeModel: "Монстер",
        bikeAvatarUrl: "",
        rentalStart: "2026-05-03",
        paidUntil: "2026-05-10",
        totalPaidRub: 3000,
        debtRub: 0,
        totalAdjustmentRub: 0,
        phones: [],
        rentals: []
    )

    let sampleRentalDetails = AdminRentalDetailsResponse(
        rentalId: "rental-001",
        clientId: "client-001",
        clientFullName: "Roman Sergeev",
        clientLogin: "client1",
        clientPassword: "client123",
        bikeId: "bike-001",
        bikeModel: "Монстер",
        bikeAvatarUrl: "",
        weeklyRateRub: 3000,
        rentalStart: "2026-05-03",
        completedAt: nil,
        paidUntil: "2026-05-10",
        totalPaidRub: 3000,
        debtRub: 0,
        totalAdjustmentRub: 0,
        rentalPipelineStatus: "long_term",
        rentalIsActive: true,
        journalEntries: []
    )

    let sampleDashboard = ClientDashboardResponse(
        clientId: "client-001",
        bikeModel: "Монстер",
        bikeAvatarUrl: "",
        rentalStart: "2026-05-01",
        paidUntil: "2026-05-08",
        debtRub: 0,
        balanceRub: 3000,
        totalAdjustmentRub: 0,
        presets: ClientPaymentPresets(
            dayRub: 430,
            weekRub: 3000,
            twoWeeksRub: 6000,
            monthRub: 12000,
            debtExactRub: 0
        ),
        taxMode: nil,
        requiresReceiptEmail: false
    )

    let samplePayment = PaymentCreationResponse(
        paymentId: "payment-001",
        amountRub: 3000,
        confirmationUrl: "https://example.test/pay/payment-001",
        idempotenceKey: "idem-001",
        status: "pending"
    )

    func isServerReachable() async -> Bool { true }

    func login(login _: String, password _: String) async throws -> AuthSession {
        AuthSession(accessToken: "token", role: .admin, userId: "admin-001")
    }

    func fetchClientDashboard(accessToken _: String) async throws -> ClientDashboardResponse {
        try fetchClientDashboardResult.get()
    }

    func fetchAdminClients(accessToken _: String) async throws -> [AdminClientSummaryResponse] {
        try fetchClientsResult.get()
    }

    func fetchAdminRents(accessToken _: String) async throws -> [AdminClientSummaryResponse] {
        fetchAdminRentsCallsCount += 1
        return try fetchRentsResult.get()
    }

    func fetchAdminClientCatalog(accessToken _: String) async throws -> [AdminClientSummaryResponse] {
        try fetchClientCatalogResult.get()
    }

    func fetchAdminBikes(accessToken _: String) async throws -> [AdminBikeResponse] {
        try fetchBikesResult.get()
    }

    func fetchAdminRentalDetails(accessToken _: String, rentalId _: String) async throws -> AdminRentalDetailsResponse {
        try fetchRentalDetailsResult.get()
    }

    func updateClientReceiptEmail(accessToken _: String, email: String) async throws {
        updateReceiptEmailCallsCount += 1
        lastUpdatedReceiptEmail = email
    }

    func createPayment(accessToken _: String, paymentType _: ClientPaymentType) async throws -> PaymentCreationResponse {
        try createPaymentResult.get()
    }

    func fetchPaymentStatus(accessToken _: String, paymentId _: String) async throws -> PaymentStatusResponse {
        try paymentStatusResult.get()
    }

    func fetchAdminClientDetails(accessToken _: String, clientId _: String) async throws -> AdminClientDetailsResponse {
        try fetchClientDetailsResult.get()
    }

    func createAdminClient(accessToken _: String, payload _: CreateClientPayload) async throws -> AdminClientDetailsResponse {
        try createClientResult.get()
    }

    func createAdminBike(accessToken _: String, payload _: CreateBikePayload) async throws -> AdminBikeResponse {
        try createBikeResult.get()
    }

    func updateAdminBike(accessToken _: String, payload _: UpdateBikePayload) async throws -> AdminBikeResponse {
        try updateBikeResult.get()
    }

    func deleteAdminBike(accessToken _: String, bikeId: String) async throws -> DeleteBikeResult {
        DeleteBikeResult(bikeId: bikeId, deleted: true)
    }

    func updateAdminClientProfile(
        accessToken _: String,
        clientId _: String,
        payload _: UpdateClientProfilePayload
    ) async throws -> AdminClientDetailsResponse {
        throw BackendError.invalidResponse
    }

    func deleteAdminClient(accessToken _: String, clientId: String) async throws -> DeleteClientResult {
        DeleteClientResult(clientId: clientId, deleted: true)
    }

    func createAdminRental(
        accessToken _: String,
        payload _: CreateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        createRentalCallsCount += 1
        return try createRentalResult.get()
    }

    func updateAdminRental(
        accessToken _: String,
        payload _: UpdateRentalPayload
    ) async throws -> AdminRentalHistoryItem {
        updateRentalCallsCount += 1
        return try updateRentalResult.get()
    }

    func updateAdminRentalPipelineStatus(
        accessToken _: String,
        rentalId _: String,
        pipelineStatus _: String
    ) async throws {}

    func finishAdminRental(accessToken _: String, rentalId _: String) async throws {}

    func startAdminClientRentalInExisting(
        accessToken _: String,
        rentalId _: String,
        clientId _: String,
        login _: String,
        password _: String,
        periodStart _: String
    ) async throws {}

    func deleteAdminRental(accessToken _: String, rentalId _: String) async throws -> DeleteRentalResult {
        deleteRentalCallsCount += 1
        return try deleteRentalResult.get()
    }

    func adjustAdminClientDebt(
        accessToken _: String,
        clientId _: String,
        amountRub _: Int,
        sign _: DebtAdjustmentSign,
        comment _: String?
    ) async throws -> DebtAdjustmentResult {
        throw BackendError.invalidResponse
    }

    func updateAdminRentalComment(
        accessToken _: String,
        rentalId _: String,
        comment _: String
    ) async throws -> String {
        throw BackendError.invalidResponse
    }

    func updateAdminRentalLinks(
        accessToken _: String,
        rentalId _: String,
        videoUrl _: String?,
        contractUrl _: String?
    ) async throws -> AdminRentalLinksUpdateResult {
        throw BackendError.invalidResponse
    }
}

private extension Result {
    var failureValue: Failure? {
        guard case let .failure(error) = self else { return nil }
        return error
    }
}
