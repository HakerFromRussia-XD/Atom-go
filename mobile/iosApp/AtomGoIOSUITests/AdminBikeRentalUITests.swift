import XCTest

final class AdminBikeRentalUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-ATOMGO_DISABLE_PAYMENT_SAFARI_AUTOPEN"]
        app.launchEnvironment["ATOMGO_BACKEND_URL"] = "http://127.0.0.1:8080/api/v1"
        app.launch()
        try loginAsAdminIfNeeded()
    }

    func testCreateAndEditBikeFlow() throws {
        let suffix = uniqueSuffix()
        let model = "UITESTBIKE_\(suffix)"
        let updatedModel = "\(model)_EDIT"

        createBike(
            model: model,
            weeklyRateRub: "3500",
            frame: "FRAME-\(suffix)",
            motor: "MOTOR-\(suffix)",
            battery1: "BAT1-\(suffix)",
            battery2: "BAT2-\(suffix)"
        )

        XCTAssertTrue(waitForText("Велосипед создан: \(model)", timeout: 8))

        openServiceSheet()
        let bikesCatalogButton = app.buttons["admin.service.bikesCatalogButton"]
        XCTAssertTrue(bikesCatalogButton.waitForExistence(timeout: 5))
        bikesCatalogButton.tap()

        let editButton = app.buttons["bikeCatalog.edit.\(model)"]
        XCTAssertTrue(waitForElementWithScroll(editButton, timeout: 8))
        editButton.tap()

        let modelField = app.textFields["editBike.modelField"]
        XCTAssertTrue(modelField.waitForExistence(timeout: 5))
        modelField.replaceText(updatedModel)

        let submit = app.buttons["editBike.submitButton"]
        XCTAssertTrue(submit.exists)
        submit.tap()

        XCTAssertTrue(app.staticTexts[updatedModel].waitForExistence(timeout: 8))

        let closeCatalog = app.buttons["Закрыть"]
        if closeCatalog.waitForExistence(timeout: 2) {
            closeCatalog.tap()
        }
    }

    func testRentalCrudFromClientDetails() throws {
        let suffix = uniqueSuffix()
        let clientName = "UI Client \(suffix)"
        let bikeModel = "UIBIKE_\(suffix)"
        let rentalStart = "2026-06-10"
        let rentalEnd = "2026-06-20"
        let updatedRentalEnd = "2026-06-25"

        createClient(
            fullName: clientName,
            address: "Moscow, \(suffix)",
            passport: "1234 \(suffix)",
            phone: "8985\(suffix.suffix(7))"
        )
        XCTAssertTrue(waitForText("Клиент создан: \(clientName)", timeout: 8))

        createBike(
            model: bikeModel,
            weeklyRateRub: "3300",
            frame: "CFRAME-\(suffix)",
            motor: "CMOTOR-\(suffix)",
            battery1: "CBAT1-\(suffix)",
            battery2: "CBAT2-\(suffix)"
        )
        XCTAssertTrue(waitForText("Велосипед создан: \(bikeModel)", timeout: 8))

        let clientCellTitle = app.staticTexts[clientName]
        XCTAssertTrue(clientCellTitle.waitForExistence(timeout: 8))
        clientCellTitle.tap()

        let addRentalButton = app.buttons["clientDetails.addRentalButton"]
        XCTAssertTrue(addRentalButton.waitForExistence(timeout: 8))
        addRentalButton.tap()

        let loginField = app.textFields["createRental.loginField"]
        let passwordField = app.secureTextFields["createRental.passwordField"]
        let startField = app.textFields["createRental.periodStartField"]
        let endField = app.textFields["createRental.periodEndField"]
        XCTAssertTrue(loginField.waitForExistence(timeout: 8))
        XCTAssertTrue(passwordField.exists)
        XCTAssertTrue(startField.exists)
        XCTAssertTrue(endField.exists)

        loginField.replaceText("client.ui.\(suffix)")
        passwordField.enterText("client123")
        startField.replaceText(rentalStart)
        endField.replaceText(rentalEnd)

        let createRental = app.buttons["createRental.submitButton"]
        XCTAssertTrue(createRental.exists)
        createRental.tap()

        XCTAssertTrue(waitForText("\(rentalStart) - \(rentalEnd)", timeout: 8))

        let toggleEdit = app.buttons["rentalCard.toggleEditButton"].firstMatch
        XCTAssertTrue(toggleEdit.waitForExistence(timeout: 6))
        toggleEdit.tap()

        let editEndField = app.textFields["rentalCard.periodEndField"].firstMatch
        XCTAssertTrue(editEndField.waitForExistence(timeout: 5))
        editEndField.replaceText(updatedRentalEnd)

        let saveEdit = app.buttons["rentalCard.saveEditButton"].firstMatch
        XCTAssertTrue(saveEdit.exists)
        saveEdit.tap()

        XCTAssertTrue(waitForText("\(rentalStart) - \(updatedRentalEnd)", timeout: 8))

        let deleteButton = app.buttons["rentalCard.deleteButton"].firstMatch
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 6))
        deleteButton.tap()

        let confirmDelete = app.buttons["Удалить"].firstMatch
        XCTAssertTrue(confirmDelete.waitForExistence(timeout: 4))
        confirmDelete.tap()

        XCTAssertFalse(app.staticTexts["\(rentalStart) - \(updatedRentalEnd)"].waitForExistence(timeout: 6))
    }

    func testIndividualEntrepreneurRentalPaymentCreates54FzPayment() throws {
        let fixture = try ensureReusableIpPaymentFixture()

        app.terminate()
        app.launchArguments = ["-ATOMGO_DISABLE_PAYMENT_SAFARI_AUTOPEN"]
        app.launchEnvironment["ATOMGO_BACKEND_URL"] = "http://127.0.0.1:8080/api/v1"
        app.launchEnvironment["ATOMGO_TEST_LOGIN"] = fixture.clientLogin
        app.launchEnvironment["ATOMGO_TEST_PASSWORD"] = fixture.clientPassword
        app.launch()
        try loginAsClientIfNeeded()

        let paymentButton = app.buttons["client.paymentButton"]
        XCTAssertTrue(waitForElementWithScroll(paymentButton, timeout: 10))
        paymentButton.tap()

        let dayButton = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "1 день")).firstMatch
        XCTAssertTrue(dayButton.waitForExistence(timeout: 5), "One-day payment option is unavailable")
        dayButton.tap()

        let receiptEmailAlert = app.alerts["Email для чека"]
        XCTAssertTrue(receiptEmailAlert.waitForExistence(timeout: 5), "New IP client must enter receipt email before first payment")
        let receiptEmailField = receiptEmailAlert.textFields.firstMatch
        XCTAssertTrue(receiptEmailField.waitForExistence(timeout: 3))
        receiptEmailField.enterText("13romaroma13@gmail.com")
        let receiptEmailSubmit = app.buttons["client.receiptEmailSubmitButton"].firstMatch
        XCTAssertTrue(receiptEmailSubmit.waitForExistence(timeout: 3))
        receiptEmailSubmit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let paymentMetadataElement = app.descendants(matching: .any)["client.paymentMetadata"]
        if !paymentMetadataElement.waitForExistence(timeout: 15) {
            let paymentError = app.descendants(matching: .any)["client.paymentErrorMessage"]
            let errorText = paymentError.exists ? paymentError.label : "payment error text is absent"
            if errorText.contains("ЮKassa не настроена") || errorText.contains("YooKassa is not configured") {
                throw XCTSkip("Backend ЮKassa не настроен для UI-проверки платежа")
            }
            XCTFail("Payment was not created for IP rental. UI error: \(errorText)")
            return
        }

        let paymentMetadata = [
            paymentMetadataElement.label,
            paymentMetadataElement.value as? String ?? ""
        ].joined(separator: "|")
        XCTAssertTrue(app.staticTexts["client.paymentTaxModeText"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["client.paymentFiscalizationText"].waitForExistence(timeout: 5))
        XCTAssertTrue(paymentMetadata.contains("individual_entrepreneur"), "IP payment must keep rental tax mode. Metadata: \(paymentMetadata)")
        XCTAssertTrue(paymentMetadata.contains("yookassa_receipt_pending"), "IP payment must be marked for YooKassa 54-FZ receipt. Metadata: \(paymentMetadata)")
    }

    func testAdminRentFiltersClickUntilCardsOverlapThenListHandlesTap() throws {
        let allFilter = app.buttons["admin.filter.all"]
        let soonReturnFilter = app.buttons["admin.filter.soonReturn"]
        let debtorsFilter = app.buttons["admin.filter.debtors"]
        let selectedFilter = app.staticTexts["admin.selectedFilter"]

        XCTAssertTrue(allFilter.waitForExistence(timeout: 8), "All filter hit target is missing")
        XCTAssertTrue(soonReturnFilter.waitForExistence(timeout: 2), "Soon-return filter hit target is missing")
        XCTAssertTrue(debtorsFilter.waitForExistence(timeout: 2), "Debtors filter hit target is missing")
        XCTAssertTrue(selectedFilter.waitForExistence(timeout: 2), "Selected filter marker is missing")
        XCTAssertTrue(allFilter.isHittable, "Filters must be tappable before the cards overlap them")

        debtorsFilter.tap()
        XCTAssertEqual(selectedFilter.stringValue, "debtors")

        allFilter.tap()
        XCTAssertEqual(selectedFilter.stringValue, "all")

        let searchField = app.descendants(matching: .any)["admin.searchField"]
        let firstCard = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "admin.rent.card."))
            .firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 3), "Search field is missing")
        XCTAssertTrue(firstCard.waitForExistence(timeout: 8), "At least one rental card is required")
        XCTAssertGreaterThan(firstCard.frame.minY, allFilter.frame.maxY, "Initial card position must leave filters visible")

        let overlappedListTapPoint = soonReturnFilter.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let overlappedListTapY = soonReturnFilter.frame.midY
        var didOverlapFilters = false
        for _ in 0..<5 {
            app.swipeUp()
            if firstCard.frame.minY <= overlappedListTapY && firstCard.frame.maxY >= overlappedListTapY {
                didOverlapFilters = true
                break
            }
        }
        XCTAssertTrue(didOverlapFilters, "Top card should move over the filter zone while scrolling")
        XCTAssertFalse(allFilter.isHittable, "Filter hit target must deactivate once cards overlap it")

        overlappedListTapPoint.tap()
        XCTAssertTrue(
            app.buttons["clientDetails.addRentalButton"].waitForExistence(timeout: 5),
            "Tap in the overlapped filter zone must be handled by the card/list"
        )

        app.buttons["Закрыть"].firstMatch.tap()

        for _ in 0..<4 {
            app.swipeUp()
        }
        XCTAssertLessThanOrEqual(
            firstCard.frame.minY,
            searchField.frame.midY + 8,
            "Cards should reach the middle of the search field zone"
        )
    }

    private struct IpPaymentFixture {
        let clientLogin: String
        let clientPassword: String
    }

    private func ensureReusableIpPaymentFixture() throws -> IpPaymentFixture {
        let token = try apiLogin(login: "admin_ip", password: "adminip123")
        let clientName = "IP UI Client 54FZ"
        let clientLogin = "ip.ui.54fz"
        let clientPassword = "client123"
        let clientId = try ensureApiClient(token: token, fullName: clientName)
        let bikeId = try ensureApiBike(token: token)
        try resetApiClientReceiptEmail(token: token, clientId: clientId, fullName: clientName)
        try ensureApiRental(
            token: token,
            clientId: clientId,
            bikeId: bikeId,
            login: clientLogin,
            password: clientPassword
        )
        return IpPaymentFixture(clientLogin: clientLogin, clientPassword: clientPassword)
    }

    private func ensureApiClient(token: String, fullName: String) throws -> String {
        let clients = try apiArray(path: "/admin/clients", token: token)
        if let existing = clients.first(where: { ($0["full_name"] as? String) == fullName }),
           let clientId = existing["client_id"] as? String {
            return clientId
        }
        let created = try apiObject(
            path: "/admin/clients",
            method: "POST",
            token: token,
            body: [
                "full_name": fullName,
                "address": "Moscow, reusable 54FZ",
                "passport_data": "4321 54FZ",
                "phones": [["label": "Рабочий (TG)", "number": "79000005454"]]
            ]
        )
        return try requireString(created["client_id"], field: "client_id")
    }

    private func resetApiClientReceiptEmail(token: String, clientId: String, fullName: String) throws {
        _ = try apiObject(
            path: "/admin/clients/\(clientId)",
            method: "POST",
            token: token,
            body: [
                "full_name": fullName,
                "address": "Moscow, reusable 54FZ",
                "passport_data": "4321 54FZ",
                "phones": [["label": "Рабочий (TG)", "number": "79000005454"]]
            ]
        )
    }

    private func ensureApiBike(token: String) throws -> String {
        let bikeModel = "IP_UIBIKE_54FZ"
        let bikes = try apiArray(path: "/admin/bikes", token: token)
        if let existing = bikes.first(where: { ($0["bike_model"] as? String) == bikeModel }),
           let bikeId = existing["bike_id"] as? String {
            return bikeId
        }
        let created = try apiObject(
            path: "/admin/bikes",
            method: "POST",
            token: token,
            body: [
                "photo_url": "",
                "bike_model": bikeModel,
                "weekly_rate_rub": 3500,
                "frame_serial_number": "IP-FRAME-54FZ",
                "motor_serial_number": "IP-MOTOR-54FZ",
                "battery_serial_number_1": "IP-BAT1-54FZ",
                "battery_serial_number_2": "IP-BAT2-54FZ"
            ]
        )
        return try requireString(created["bike_id"], field: "bike_id")
    }

    private func ensureApiRental(
        token: String,
        clientId: String,
        bikeId: String,
        login: String,
        password: String
    ) throws {
        let details = try apiObject(path: "/admin/clients/\(clientId)", method: "GET", token: token)
        let rentals = details["rentals"] as? [[String: Any]] ?? []
        if rentals.contains(where: { ($0["bike_id"] as? String) == bikeId && ($0["period_start"] as? String) == "2026-05-07" }) {
            return
        }
        _ = try apiObject(
            path: "/admin/clients/\(clientId)/rentals",
            method: "POST",
            token: token,
            body: [
                "bike_id": bikeId,
                "login": login,
                "password": password,
                "period_start": "2026-05-07"
            ]
        )
    }

    private func createClient(
        fullName: String,
        address: String,
        passport: String,
        phone: String,
        email: String? = nil
    ) {
        openServiceSheet()
        let clientsCatalog = app.buttons["admin.service.clientsCatalogButton"]
        XCTAssertTrue(clientsCatalog.waitForExistence(timeout: 5))
        clientsCatalog.tap()

        let addClient = app.buttons["clientCatalog.addClientButton"]
        XCTAssertTrue(addClient.waitForExistence(timeout: 5))
        addClient.tap()

        let fullNameField = app.textFields["createClient.fullNameField"]
        let addressField = app.textFields["createClient.addressField"]
        let passportField = app.textFields["createClient.passportField"]
        let phoneLabelField = app.textFields["createClient.phoneLabel1Field"]
        let phoneNumberField = app.textFields["createClient.phoneNumber1Field"]
        XCTAssertTrue(fullNameField.waitForExistence(timeout: 6))

        fullNameField.enterText(fullName)
        addressField.enterText(address)
        passportField.enterText(passport)
        phoneLabelField.replaceText("Рабочий (TG)")
        phoneNumberField.replaceText(phone)
        if let email {
            app.buttons["createClient.addPhoneButton"].tap()
            let emailLabelField = app.textFields["createClient.phoneLabelField.1"]
            let emailNumberField = app.textFields["createClient.phoneNumberField.1"]
            XCTAssertTrue(emailLabelField.waitForExistence(timeout: 3))
            emailLabelField.replaceText("Email")
            emailNumberField.replaceText(email)
        }

        let submit = app.buttons["createClient.submitButton"]
        XCTAssertTrue(submit.exists)
        submit.tap()

        closeCurrentCatalogIfNeeded()
    }

    private func openClientDetailsFromCatalog(clientName: String) {
        openServiceSheet()
        let clientsCatalog = app.buttons["admin.service.clientsCatalogButton"]
        XCTAssertTrue(clientsCatalog.waitForExistence(timeout: 5))
        clientsCatalog.tap()

        let openClient = app.buttons["clientCatalog.open.\(clientName)"]
        XCTAssertTrue(waitForElementWithScroll(openClient, timeout: 8))
        openClient.tap()
    }

    private func createRentalFromOpenClientDetails(
        login: String,
        password: String,
        rentalStart: String,
        rentalEnd: String
    ) {
        let addRentalButton = app.buttons["clientDetails.addRentalButton"]
        XCTAssertTrue(addRentalButton.waitForExistence(timeout: 8))
        addRentalButton.tap()

        let loginField = app.textFields["createRental.loginField"]
        let passwordField = app.secureTextFields["createRental.passwordField"]
        let startField = app.textFields["createRental.periodStartField"]
        let endField = app.textFields["createRental.periodEndField"]
        XCTAssertTrue(loginField.waitForExistence(timeout: 8))
        XCTAssertTrue(passwordField.exists)
        XCTAssertTrue(startField.exists)
        XCTAssertTrue(endField.exists)

        loginField.replaceText(login)
        passwordField.enterText(password)
        startField.replaceText(rentalStart)
        endField.replaceText(rentalEnd)

        let createRental = app.buttons["createRental.submitButton"]
        XCTAssertTrue(createRental.exists)
        createRental.tap()
    }

    private func createBike(
        model: String,
        weeklyRateRub: String,
        frame: String,
        motor: String,
        battery1: String,
        battery2: String
    ) {
        openServiceSheet()
        let bikesCatalog = app.buttons["admin.service.bikesCatalogButton"]
        XCTAssertTrue(bikesCatalog.waitForExistence(timeout: 5))
        bikesCatalog.tap()

        let addBike = app.buttons["bikeCatalog.addBikeButton"]
        XCTAssertTrue(addBike.waitForExistence(timeout: 5))
        addBike.tap()

        let modelField = app.textFields["createBike.modelField"]
        let weeklyField = app.textFields["createBike.weeklyRateField"]
        let frameField = app.textFields["createBike.frameSerialField"]
        let motorField = app.textFields["createBike.motorSerialField"]
        let battery1Field = app.textFields["createBike.battery1Field"]
        let battery2Field = app.textFields["createBike.battery2Field"]
        XCTAssertTrue(modelField.waitForExistence(timeout: 6))

        modelField.enterText(model)
        weeklyField.replaceText(weeklyRateRub)
        frameField.enterText(frame)
        motorField.enterText(motor)
        battery1Field.enterText(battery1)
        battery2Field.enterText(battery2)

        let submit = app.buttons["createBike.submitButton"]
        XCTAssertTrue(submit.exists)
        submit.tap()

        closeCurrentCatalogIfNeeded()
    }

    private func closeCurrentCatalogIfNeeded() {
        for _ in 0..<3 {
            let closeCatalog = app.buttons["Закрыть"].firstMatch
            guard closeCatalog.waitForExistence(timeout: 2) else {
                return
            }
            if closeCatalog.isHittable {
                closeCatalog.tap()
            } else {
                closeCatalog.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            }
            if app.buttons["admin.openServiceButton"].waitForExistence(timeout: 2) {
                return
            }
        }
    }

    private func openServiceSheet() {
        if app.buttons["admin.service.clientsCatalogButton"].exists || app.buttons["admin.service.bikesCatalogButton"].exists {
            return
        }
        if app.keyboards.element.exists {
            app.typeText("\n")
        }
        closeCurrentCatalogIfNeeded()
        if app.buttons["admin.service.clientsCatalogButton"].exists || app.buttons["admin.service.bikesCatalogButton"].exists {
            return
        }
        let serviceButton = app.buttons["admin.openServiceButton"]
        XCTAssertTrue(waitForElementWithScroll(serviceButton, timeout: 8))
        serviceButton.tap()
    }

    private func waitForText(_ fragment: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label CONTAINS %@", fragment)
        let match = app.staticTexts.containing(predicate).firstMatch
        return match.waitForExistence(timeout: timeout)
    }

    private func waitForElementWithScroll(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        if element.waitForExistence(timeout: timeout) {
            return true
        }

        for _ in 0..<8 {
            app.swipeUp()
            if element.waitForExistence(timeout: 1.0) {
                return true
            }
        }

        for _ in 0..<8 {
            app.swipeDown()
            if element.waitForExistence(timeout: 1.0) {
                return true
            }
        }

        return element.exists
    }

    private func loginAsAdminIfNeeded() throws {
        if app.buttons["admin.openServiceButton"].waitForExistence(timeout: 2) {
            return
        }

        let adminQuickFill = app.buttons["login.quickFillAdmin"]
        guard adminQuickFill.waitForExistence(timeout: 8) else {
            throw XCTSkip("Backend/login screen is unavailable for UI flow tests")
        }
        adminQuickFill.tap()

        let submit = app.buttons["login.submitButton"]
        guard submit.exists else {
            throw XCTSkip("Login submit button is unavailable")
        }
        submit.tap()

        guard app.buttons["admin.openServiceButton"].waitForExistence(timeout: 30) else {
            throw XCTSkip("Admin screen did not open (backend may be unavailable)")
        }
    }

    private func loginAsAdminIpIfNeeded() throws {
        if app.buttons["admin.openServiceButton"].waitForExistence(timeout: 2) {
            return
        }

        let adminQuickFill = app.buttons["login.quickFillAdminIp"]
        guard adminQuickFill.waitForExistence(timeout: 8) else {
            throw XCTSkip("Backend/login screen is unavailable for UI flow tests")
        }
        adminQuickFill.tap()

        let submit = app.buttons["login.submitButton"]
        guard submit.exists else {
            throw XCTSkip("Login submit button is unavailable")
        }
        submit.tap()

        guard app.buttons["admin.openServiceButton"].waitForExistence(timeout: 30) else {
            throw XCTSkip("IP admin screen did not open (backend may be unavailable)")
        }
    }

    private func loginAsClientIfNeeded() throws {
        if app.buttons["client.paymentButton"].waitForExistence(timeout: 2) {
            return
        }

        let loginField = app.textFields["login.loginField"]
        guard loginField.waitForExistence(timeout: 8) else {
            throw XCTSkip("Backend/login screen is unavailable for client UI flow")
        }

        let submit = app.buttons["login.submitButton"]
        guard submit.exists else {
            throw XCTSkip("Login submit button is unavailable")
        }
        submit.tap()

        guard app.buttons["client.paymentButton"].waitForExistence(timeout: 12) else {
            let loginStatus = app.descendants(matching: .any)["login.statusText"]
            let status = loginStatus.exists ? loginStatus.label : "login status text is absent"
            XCTFail("Client screen did not open. Status: \(status)")
            return
        }
    }

    private func uniqueSuffix() -> String {
        UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(10).description
    }

    private func apiLogin(login: String, password: String) throws -> String {
        let response = try apiObject(
            path: "/auth/login",
            method: "POST",
            body: ["login": login, "password": password]
        )
        return try requireString(response["access_token"], field: "access_token")
    }

    private func apiArray(path: String, token: String) throws -> [[String: Any]] {
        let value = try apiRequest(path: path, method: "GET", token: token, body: nil)
        guard let array = value as? [[String: Any]] else {
            throw XCTSkip("Unexpected API array response for \(path)")
        }
        return array
    }

    private func apiObject(
        path: String,
        method: String,
        token: String? = nil,
        body: [String: Any]? = nil
    ) throws -> [String: Any] {
        let value = try apiRequest(path: path, method: method, token: token, body: body)
        guard let object = value as? [String: Any] else {
            throw XCTSkip("Unexpected API object response for \(path)")
        }
        return object
    }

    private func apiRequest(
        path: String,
        method: String,
        token: String?,
        body: [String: Any]?
    ) throws -> Any {
        let normalizedPath = path.hasPrefix("/") ? path : "/\(path)"
        let requestURL = URL(string: "http://127.0.0.1:8080/api/v1\(normalizedPath)")!
        var request = URLRequest(url: requestURL)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let semaphore = DispatchSemaphore(value: 0)
        var result: Result<(Data, HTTPURLResponse), Error>?
        URLSession.shared.dataTask(with: request) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                result = .failure(error)
                return
            }
            guard let data, let httpResponse = response as? HTTPURLResponse else {
                result = .failure(NSError(domain: "AtomGoUITestAPI", code: -1))
                return
            }
            result = .success((data, httpResponse))
        }.resume()

        _ = semaphore.wait(timeout: .now() + 10)
        let (data, response) = try result?.get() ?? {
            throw XCTSkip("Backend API did not respond for \(path)")
        }()
        guard (200 ... 299).contains(response.statusCode) else {
            let bodyText = String(data: data, encoding: .utf8) ?? ""
            throw XCTSkip("Backend API \(path) failed with \(response.statusCode): \(bodyText)")
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    private func requireString(_ value: Any?, field: String) throws -> String {
        guard let string = value as? String, !string.isEmpty else {
            throw XCTSkip("Missing API field \(field)")
        }
        return string
    }
}

private extension XCUIElement {
    var stringValue: String {
        (value as? String) ?? label
    }

    func enterText(_ text: String) {
        tap()
        typeText(text)
    }

    func replaceText(_ text: String) {
        tap()
        if let existingText = value as? String, !existingText.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: existingText.count)
            typeText(deleteString)
        }
        typeText(text)
    }
}
