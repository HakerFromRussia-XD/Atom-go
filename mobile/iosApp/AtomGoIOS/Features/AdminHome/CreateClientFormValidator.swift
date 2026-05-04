import Foundation

struct CreateClientPhoneInput: Equatable {
    var label: String
    var number: String
}

struct CreateClientFormInput: Equatable {
    var fullName: String
    var address: String
    var passportData: String
    var phones: [CreateClientPhoneInput]
}

enum CreateClientFormValidationError: LocalizedError, Equatable {
    case missingFullName

    var errorDescription: String? {
        switch self {
        case .missingFullName:
            return "Укажите ФИО клиента"
        }
    }
}

enum CreateClientFormValidator {
    static func buildPayload(from input: CreateClientFormInput) -> Result<CreateClientPayload, CreateClientFormValidationError> {
        let fullName = input.fullName.trimmed
        let address = input.address.trimmed
        let passportData = input.passportData.trimmed

        guard !fullName.isEmpty else {
            return .failure(.missingFullName)
        }

        let phones = input.phones
            .map {
                AdminClientPhone(
                    id: UUID().uuidString,
                    label: $0.label.trimmed,
                    number: $0.number.trimmed
                )
            }
            .filter { !$0.label.isEmpty && !$0.number.isEmpty }

        return .success(
            CreateClientPayload(
                fullName: fullName,
                address: address,
                passportData: passportData,
                phones: phones
            )
        )
    }
}

enum AdminFormValidator {
    static func validateBikeSerialDuplicates(
        allBikes: [AdminBikeResponse],
        bikeIdToIgnore: String?,
        frameSerial: String,
        motorSerial: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    ) -> String? {
        let serialPairs: [(field: String, value: String)] = [
            ("frame", frameSerial.trimmed),
            ("motor", motorSerial.trimmed),
            ("battery1", batterySerialNumber1.trimmed),
            ("battery2", batterySerialNumber2?.trimmed ?? "")
        ]
        .filter { !$0.value.isEmpty }

        let normalizedValues = serialPairs.map { $0.value.lowercased() }
        if normalizedValues.count != Set(normalizedValues).count {
            return "Серийные номера внутри карточки велосипеда должны быть уникальными"
        }

        for pair in serialPairs {
            let duplicateBike = allBikes.first { bike in
                guard bike.bikeId != bikeIdToIgnore else { return false }
                return bike.containsSerial(pair.value)
            }
            if duplicateBike != nil {
                switch pair.field {
                case "frame":
                    return "Серийный номер рамы уже используется в другом велосипеде"
                case "motor":
                    return "Серийный номер мотора уже используется в другом велосипеде"
                case "battery1":
                    return "Серийный номер аккумулятора 1 уже используется в другом велосипеде"
                case "battery2":
                    return "Серийный номер аккумулятора 2 уже используется в другом велосипеде"
                default:
                    return "Серийный номер уже используется в другом велосипеде"
                }
            }
        }

        return nil
    }

    static func validateRentalLoginDuplicate(
        clients: [AdminClientSummaryResponse],
        selectedClientId: String,
        login: String
    ) -> String? {
        let normalizedLogin = login.trimmed.lowercased()
        guard !normalizedLogin.isEmpty else { return nil }

        let duplicateClient = clients.first { client in
            guard client.clientId != selectedClientId else { return false }
            guard let existingLogin = client.clientLogin?.trimmed.lowercased(),
                  !existingLogin.isEmpty else { return false }
            return existingLogin == normalizedLogin
        }

        if duplicateClient != nil {
            return "Логин уже привязан к другому клиенту. Укажите другой логин"
        }
        return nil
    }
}

private extension AdminBikeResponse {
    func containsSerial(_ serial: String) -> Bool {
        let normalized = serial.trimmed.lowercased()
        guard !normalized.isEmpty else { return false }
        return frameSerialNumber.lowercased() == normalized ||
            motorSerialNumber.lowercased() == normalized ||
            batterySerialNumber1.lowercased() == normalized ||
            (batterySerialNumber2?.lowercased() == normalized)
    }
}

private extension String {
    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
