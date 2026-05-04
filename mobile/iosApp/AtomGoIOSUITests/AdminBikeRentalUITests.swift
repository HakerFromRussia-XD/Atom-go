import XCTest

final class AdminBikeRentalUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
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

    private func createClient(
        fullName: String,
        address: String,
        passport: String,
        phone: String
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

        let submit = app.buttons["createClient.submitButton"]
        XCTAssertTrue(submit.exists)
        submit.tap()

        closeCurrentCatalogIfNeeded()
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
        let closeCatalog = app.buttons["Закрыть"].firstMatch
        if closeCatalog.waitForExistence(timeout: 10) {
            closeCatalog.tap()
        }
    }

    private func openServiceSheet() {
        let serviceButton = app.buttons["admin.openServiceButton"]
        XCTAssertTrue(serviceButton.waitForExistence(timeout: 8))
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

        guard app.buttons["admin.openServiceButton"].waitForExistence(timeout: 12) else {
            throw XCTSkip("Admin screen did not open (backend may be unavailable)")
        }
    }

    private func uniqueSuffix() -> String {
        String(Int(Date().timeIntervalSince1970))
    }
}

private extension XCUIElement {
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
