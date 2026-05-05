import XCTest

final class CreateClientUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        try loginAsAdminIfNeeded()
    }

    func testCreateClientValidationErrorForEmptyRequiredFields() {
        tapAddClient()
        XCTAssertTrue(app.buttons["createClient.submitButton"].waitForExistence(timeout: 5))

        app.buttons["createClient.submitButton"].tap()

        XCTAssertTrue(waitForText("Укажите ФИО клиента", timeout: 5))
    }

    func testCreateClientSuccess() {
        tapAddClient()
        XCTAssertTrue(app.buttons["createClient.submitButton"].waitForExistence(timeout: 5))

        app.textFields["createClient.fullNameField"].enterText("Roman Sergeev")
        app.textFields["createClient.addressField"].enterText("Moscow, 123")
        app.textFields["createClient.passportField"].enterText("1234 567890")
        app.textFields["createClient.phoneLabel1Field"].replaceText("Рабочий (TG)")
        app.textFields["createClient.phoneNumber1Field"].replaceText("89859325907")

        app.buttons["createClient.submitButton"].tap()

        closeCurrentCatalogIfNeeded()
        XCTAssertTrue(app.buttons["admin.openServiceButton"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Roman Sergeev"].waitForExistence(timeout: 6))
    }

    private func tapAddClient() {
        let serviceButton = app.buttons["admin.openServiceButton"]
        XCTAssertTrue(serviceButton.waitForExistence(timeout: 8))
        serviceButton.tap()

        let clientsCatalog = app.buttons["admin.service.clientsCatalogButton"]
        XCTAssertTrue(clientsCatalog.waitForExistence(timeout: 5))
        clientsCatalog.tap()

        let addClient = app.buttons["clientCatalog.addClientButton"]
        XCTAssertTrue(addClient.waitForExistence(timeout: 5))
        addClient.tap()
    }

    private func closeCurrentCatalogIfNeeded() {
        let closeCatalog = app.buttons["Закрыть"].firstMatch
        if closeCatalog.waitForExistence(timeout: 10) {
            closeCatalog.tap()
        }
    }

    private func waitForText(_ fragment: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label CONTAINS %@", fragment)
        let match = app.staticTexts.containing(predicate).firstMatch
        return match.waitForExistence(timeout: timeout)
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
}

final class ClientPaymentUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-ATOMGO_DISABLE_PAYMENT_SAFARI_AUTOPEN"]
        app.launchEnvironment["ATOMGO_BACKEND_URL"] = "http://192.168.1.234:8080/api/v1"
        app.launch()
    }

    func testClient2CreatesOneWeekPaymentWithRealYooKassaConfirmationUrl() throws {
        app.terminate()
        app.launchEnvironment["ATOMGO_TEST_LOGIN"] = "client2"
        app.launchEnvironment["ATOMGO_TEST_PASSWORD"] = "client234"
        app.launch()

        try loginAsClient2IfNeeded()

        let paymentButton = app.buttons["client.paymentButton"]
        XCTAssertTrue(
            waitForHittableElementWithScroll(paymentButton, timeout: 10),
            "Client payment button is unavailable"
        )
        paymentButton.tap()

        let weekButton = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "1 неделя")).firstMatch
        XCTAssertTrue(weekButton.waitForExistence(timeout: 5), "One-week payment option is unavailable")
        let expectedAmount = lastNumber(in: weekButton.label)
        weekButton.tap()

        let paymentCard = app.descendants(matching: .any)["client.paymentCard"]
        if !paymentCard.waitForExistence(timeout: 12) {
            let paymentError = findElementWithScroll(identifier: "client.paymentErrorMessage", maxScrolls: 4)
            let errorText = paymentError.exists
                ? paymentError.label
                : "payment error text is absent"
            if errorText.contains("ЮKassa не настроена")
                || errorText.contains("YooKassa is not configured") {
                throw XCTSkip("Backend ЮKassa не настроен: нужен YOOKASSA_SECRET_KEY и YOOKASSA_PUBLIC_BASE_URL")
            }
            XCTFail("One-week payment was not created for client2. UI error: \(errorText)")
            return
        }

        if let expectedAmount {
            XCTAssertTrue(
                waitForText("Сумма: \(expectedAmount) ₽", timeout: 3),
                "Created payment amount does not match selected one-week preset"
            )
        }

        let confirmationUrl = (paymentCard.value as? String) ?? ""
        XCTAssertFalse(
            confirmationUrl.contains("https://yookassa.ru/pay/"),
            "Backend returned invalid mock URL that opens YooKassa 404: \(confirmationUrl)"
        )
        XCTAssertTrue(
            confirmationUrl.contains("yoomoney.ru") || confirmationUrl.contains("checkout"),
            "Expected real YooKassa confirmation URL, got: \(confirmationUrl)"
        )
    }

    func testWrongClientPasswordShowsReadableErrorWithoutCrash() throws {
        app.terminate()
        app.launchEnvironment["ATOMGO_TEST_LOGIN"] = "client2"
        app.launchEnvironment["ATOMGO_TEST_PASSWORD"] = "wrong-password"
        app.launch()

        XCTAssertFalse(app.buttons["client.paymentButton"].waitForExistence(timeout: 2))
        app.buttons["login.submitButton"].tap()

        let status = app.descendants(matching: .any)["login.statusText"]
        XCTAssertTrue(status.waitForExistence(timeout: 8), "Login error text is absent")
        XCTAssertTrue(
            status.label.contains("Неверный логин или пароль"),
            "Wrong credentials must show readable login error, got: \(status.label)"
        )
        XCTAssertTrue(
            app.buttons["login.submitButton"].exists,
            "App should stay on login screen after wrong credentials instead of crashing"
        )
    }

    private func loginAsClient2IfNeeded() throws {
        if app.buttons["client.paymentButton"].waitForExistence(timeout: 2) {
            return
        }

        let loginField = app.textFields["login.loginField"]
        guard loginField.waitForExistence(timeout: 8) else {
            XCTFail("Backend/login screen is unavailable for client payment UI test")
            return
        }

        let submit = app.buttons["login.submitButton"]
        guard submit.exists else {
            XCTFail("Login submit button is unavailable")
            return
        }
        submit.tap()

        guard app.buttons["client.paymentButton"].waitForExistence(timeout: 12) else {
            let loginStatus = app.descendants(matching: .any)["login.statusText"]
            let status = loginStatus.exists
                ? loginStatus.label
                : "login status text is absent"
            XCTFail("Client2 screen did not open. Status: \(status)")
            return
        }
    }

    private func fillLoginForm(login: String, password: String) {
        let loginField = app.textFields["login.loginField"]
        XCTAssertTrue(loginField.waitForExistence(timeout: 5), "Login field is unavailable")
        loginField.replaceText(login)

        let passwordField = app.secureTextFields["login.passwordField"].exists
            ? app.secureTextFields["login.passwordField"]
            : app.textFields["login.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5), "Password field is unavailable")
        passwordField.replaceText(password)
    }

    private func waitForText(_ fragment: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label CONTAINS %@", fragment)
        let match = app.staticTexts.containing(predicate).firstMatch
        return match.waitForExistence(timeout: timeout)
    }

    private func waitForHittableElementWithScroll(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        if element.waitForExistence(timeout: timeout), element.isHittable {
            return true
        }

        for _ in 0..<8 {
            app.swipeUp()
            if element.exists, element.isHittable {
                return true
            }
        }

        for _ in 0..<8 {
            app.swipeDown()
            if element.exists, element.isHittable {
                return true
            }
        }

        return element.exists && element.isHittable
    }

    private func findElementWithScroll(identifier: String, maxScrolls: Int) -> XCUIElement {
        let element = app.descendants(matching: .any)[identifier]
        if element.exists {
            return element
        }

        for _ in 0..<maxScrolls {
            app.swipeUp()
            if element.exists {
                return element
            }
        }

        for _ in 0..<maxScrolls {
            app.swipeDown()
            if element.exists {
                return element
            }
        }

        return element
    }

    private func lastNumber(in text: String) -> String? {
        var runs: [String] = []
        var current = ""

        for character in text {
            if character.isNumber {
                current.append(character)
            } else if !current.isEmpty {
                runs.append(current)
                current = ""
            }
        }

        if !current.isEmpty {
            runs.append(current)
        }

        return runs.last
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
