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
