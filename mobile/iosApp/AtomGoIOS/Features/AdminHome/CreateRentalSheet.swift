import SwiftUI
import PhotosUI
import UIKit

struct RentalEditorInitialValues {
    let rentalId: String
    let clientId: String
    let bikeId: String
    let login: String
    let password: String
    let periodStart: String
    let periodEnd: String?
    let videoUrl: String?
    let contractUrl: String?
    let comment: String?
}

struct CreateRentalSheet: View {
    enum Mode {
        case create
        case edit(rentalId: String)

        var title: String {
            switch self {
            case .create:
                return "Новая аренда"
            case .edit:
                return "Редактировать аренду"
            }
        }

        var isEdit: Bool {
            if case .edit = self { return true }
            return false
        }
    }

    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let preselectedClientId: String?
    let isSaving: Bool
    let onCancel: () -> Void
    let onCreate: ((CreateRentalPayload) -> Void)?
    let onUpdate: ((UpdateRentalPayload) -> Void)?
    let mode: Mode

    @State private var selectedClientId: String
    @State private var selectedBikeId: String
    @State private var login: String
    @State private var password: String
    @State private var periodStart: String
    @State private var periodEnd: String
    @State private var videoUrl: String
    @State private var contractUrl: String
    @State private var comment: String
    @State private var validationError: String?
    @State private var isClientPickerPresented = false
    @State private var isBikePickerPresented = false
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    init(
        clients: [AdminClientSummaryResponse],
        bikes: [AdminBikeResponse],
        preselectedClientId: String? = nil,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onCreate: @escaping (CreateRentalPayload) -> Void
    ) {
        self.clients = clients
        self.bikes = bikes
        self.preselectedClientId = preselectedClientId
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onCreate = onCreate
        self.onUpdate = nil
        self.mode = .create
        let initialClientId = preselectedClientId ?? ""
        let initialBikeId = ""
        let initialClientLogin = clients.first(where: { $0.clientId == initialClientId })?.clientLogin ?? ""
        _selectedClientId = State(initialValue: initialClientId)
        _selectedBikeId = State(initialValue: initialBikeId)
        _login = State(initialValue: initialClientLogin)
        _password = State(initialValue: "")
        _periodStart = State(initialValue: DateFormatter.apiDate.string(from: Date()))
        _periodEnd = State(initialValue: "")
        _videoUrl = State(initialValue: "")
        _contractUrl = State(initialValue: "")
        _comment = State(initialValue: "")
    }

    init(
        clients: [AdminClientSummaryResponse],
        bikes: [AdminBikeResponse],
        initialValues: RentalEditorInitialValues,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onUpdate: @escaping (UpdateRentalPayload) -> Void
    ) {
        self.clients = clients
        self.bikes = bikes
        self.preselectedClientId = initialValues.clientId
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onCreate = nil
        self.onUpdate = onUpdate
        self.mode = .edit(rentalId: initialValues.rentalId)
        _selectedClientId = State(initialValue: initialValues.clientId)
        _selectedBikeId = State(initialValue: initialValues.bikeId)
        _login = State(initialValue: initialValues.login)
        _password = State(initialValue: initialValues.password)
        _periodStart = State(initialValue: initialValues.periodStart)
        _periodEnd = State(initialValue: initialValues.periodEnd ?? "")
        _videoUrl = State(initialValue: initialValues.videoUrl ?? "")
        _contractUrl = State(initialValue: initialValues.contractUrl ?? "")
        _comment = State(initialValue: initialValues.comment ?? "")
    }

    var body: some View {
        ZStack {
            AppDesign.pageBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 23)
                    .padding(.top, 8)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        sectionTitle("КЛИЕНТ И ВЕЛОСИПЕД")

                        selectionField(
                            title: "КЛИЕНТ",
                            value: selectedClientName,
                            placeholder: "выбрать клаента",
                            leadingMarkerColor: Color(red: 211 / 255, green: 215 / 255, blue: 221 / 255),
                            action: { isClientPickerPresented = true }
                        )
                        .accessibilityIdentifier("createRental.clientPicker")

                        selectionField(
                            title: "ВЕЛОСИПЕД",
                            value: selectedBikeName,
                            placeholder: "выбрать · покажет ставку",
                            leadingMarkerColor: Color(red: 205 / 255, green: 209 / 255, blue: 217 / 255),
                            action: { isBikePickerPresented = true }
                        )
                        .accessibilityIdentifier("createRental.bikePicker")

                        inputField(
                            title: "ДАТА НАЧАЛА",
                            placeholder: "YYYY-MM-DD",
                            text: $periodStart,
                            id: "createRental.periodStartField"
                        )
                        inputField(
                            title: "ДАТА ОКОНЧАНИЯ",
                            placeholder: "не обязательно",
                            text: $periodEnd,
                            id: "createRental.periodEndField",
                            isDashed: true
                        )

                        sectionTitle("ДОСТУП КЛИЕНТА", topPadding: 6)

                        inputField(
                            title: "ЛОГИН КЛИЕНТА",
                            placeholder: "введите...",
                            text: $login,
                            id: "createRental.loginField"
                        )
                        inputField(
                            title: "ПАРОЛЬ КЛИЕНТА",
                            placeholder: "введите...",
                            text: $password,
                            id: "createRental.passwordField"
                        )

                        credentialButtonsRow

                        sectionTitle("ДОКУМЕНТЫ И КОММЕНТАРИЙ", topPadding: 6)

                        inputField(
                            title: "ССЫЛКА НА ВИДЕО",
                            placeholder: "не обязательно",
                            text: $videoUrl,
                            id: "createRental.videoUrlField",
                            isDashed: true
                        )
                        inputField(
                            title: "ССЫЛКА НА ДОГОВОР",
                            placeholder: "не обязательно",
                            text: $contractUrl,
                            id: "createRental.contractUrlField",
                            isDashed: true
                        )
                        inputField(
                            title: "КОММЕНТАРИЙ",
                            placeholder: "не обязательно",
                            text: $comment,
                            id: "createRental.commentField",
                            isDashed: true
                        )
                    }
                    .padding(.horizontal, 23)
                    .padding(.top, 14)
                    .padding(.bottom, 26)
                }
            }
        }
        .onChange(of: selectedClientId) { newClientId in
            guard !mode.isEdit else { return }
            if let suggestedLogin = clients.first(where: { $0.clientId == newClientId })?.clientLogin,
               !suggestedLogin.isEmpty {
                login = suggestedLogin
            }
        }
        .fullScreenCover(isPresented: $isClientPickerPresented) {
            RentalStartClientPickerSheet(
                clients: availableClientsForStart,
                selectedClientId: Binding(
                    get: { selectedClientId.isEmpty ? nil : selectedClientId },
                    set: { selectedClientId = $0 ?? "" }
                ),
                onClose: { isClientPickerPresented = false },
                onConfirm: { isClientPickerPresented = false }
            )
        }
        .fullScreenCover(isPresented: $isBikePickerPresented) {
            RentalStartBikePickerSheet(
                bikes: bikes,
                selectedBikeId: Binding(
                    get: { selectedBikeId.isEmpty ? nil : selectedBikeId },
                    set: { selectedBikeId = $0 ?? "" }
                ),
                onClose: { isBikePickerPresented = false },
                onConfirm: { isBikePickerPresented = false }
            )
        }
        .onChange(of: validationError) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private var availableClientsForStart: [AdminClientSummaryResponse] {
        clients.availableForRentalStart()
    }

    private var selectedClientName: String? {
        clients.first(where: { $0.clientId == selectedClientId })?.fullName
    }

    private var selectedBikeName: String? {
        guard let bike = bikes.first(where: { $0.bikeId == selectedBikeId }) else { return nil }
        return "\(bike.bikeModel) · \(bike.weeklyRateRub) ₽/нед"
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            topBarButton(
                assetName: "back",
                assetSize: 14,
                isDark: false,
                accessibilityIdentifier: "createRental.cancelButton",
                action: onCancel
            )
            .disabled(isSaving)

            Spacer(minLength: 12)

            Text(mode.title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AppDesign.titleText)

            Spacer(minLength: 12)

            topBarButton(
                assetName: "ok",
                assetSize: 16,
                isDark: true,
                accessibilityIdentifier: "createRental.submitButton",
                action: submit
            )
            .disabled(isSaving)
            .opacity(isSaving ? 0.45 : 1)
        }
        .frame(height: 47)
    }

    private func topBarButton(
        imageName: String,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? AppDesign.accent : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .overlay(
                    Image(systemName: imageName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(isDark ? Color.white : AppDesign.accent)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func topBarButton(
        assetName: String,
        assetSize: CGFloat,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? AppDesign.accent : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .overlay(
                    Image(assetName)
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: assetSize, height: assetSize)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func sectionTitle(_ text: String, topPadding: CGFloat = 0) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            .padding(.top, topPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func selectionField(
        title: String,
        value: String?,
        placeholder: String,
        leadingMarkerColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 10, weight: .bold))
                        .tracking(0.8)
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    Text(value ?? placeholder)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(value == nil ? Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255) : AppDesign.titleText)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                ZStack {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                }
                .frame(width: 28, height: 28)
            }
            .padding(.leading, 19)
            .padding(.trailing, 15)
            .frame(minHeight: 58)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1)
            )
            .overlay(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(leadingMarkerColor)
                    .frame(width: 4)
                    .padding(.vertical, 8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var credentialButtonsRow: some View {
        HStack(spacing: 8) {
            Button(action: generateCredentials) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 14, weight: .medium))
                    Text("Сгенерировать")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(Color.white)
                .frame(width: 179, height: 44)
                .background(AppDesign.accent)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("createRental.generateCredentialsButton")

            Button(action: copyCredentials) {
                HStack(spacing: 8) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 14, weight: .medium))
                    Text("Скопировать")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(AppDesign.accent)
                .frame(width: 181, height: 46)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("createRental.copyCredentialsButton")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func inputField(
        title: String,
        placeholder: String,
        text: Binding<String>,
        id: String,
        isDashed: Bool = false
    ) -> some View {
        AtomGoInputField(
            label: title,
            placeholder: placeholder,
            text: text,
            isDashed: isDashed,
            textInputAutocapitalization: .never,
            accessibilityIdentifier: id,
            accentBorder: true
        )
    }

    private func generateCredentials() {
        let symbols = Array("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%")
        password = String((0..<12).compactMap { _ in symbols.randomElement() })
        if login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if let selectedClientName {
                login = selectedClientName
                    .lowercased()
                    .replacingOccurrences(of: " ", with: ".")
                    .filter { $0.isLetter || $0.isNumber || $0 == "." }
            }
            if login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                login = "client\(Int.random(in: 1000...9999))"
            }
        }
    }

    private func copyCredentials() {
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedLogin.isEmpty && normalizedPassword.isEmpty {
            validationError = "Заполните логин и пароль"
            return
        }
        if normalizedLogin.isEmpty {
            validationError = "Заполните логин"
            return
        }
        if normalizedPassword.isEmpty {
            validationError = "Заполните пароль"
            return
        }
        validationError = nil
        UIPasteboard.general.string = "Логин: \(normalizedLogin)\nПароль: \(normalizedPassword)"
        presentToast("скопированно")
    }

    private func submit() {
        validationError = nil
        let normalizedStart = periodStart.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedEnd = periodEnd.trimmedToOptional
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !selectedClientId.isEmpty else {
            validationError = "Выберите клиента"
            return
        }
        guard !selectedBikeId.isEmpty else {
            validationError = "Выберите велосипед"
            return
        }
        guard !normalizedLogin.isEmpty, !normalizedPassword.isEmpty else {
            validationError = "Укажите логин и пароль клиента"
            return
        }
        if let duplicateLoginMessage = AdminFormValidator.validateRentalLoginDuplicate(
            clients: clients,
            selectedClientId: selectedClientId,
            login: normalizedLogin
        ) {
            validationError = duplicateLoginMessage
            return
        }
        guard isValidApiDate(normalizedStart) else {
            validationError = "Дата начала должна быть в формате YYYY-MM-DD"
            return
        }
        if let normalizedEnd {
            guard isValidApiDate(normalizedEnd) else {
                validationError = "Дата окончания должна быть в формате YYYY-MM-DD"
                return
            }
            if normalizedEnd < normalizedStart {
                validationError = "Дата окончания не может быть раньше даты начала"
                return
            }
        }

        switch mode {
        case .create:
            onCreate?(
                CreateRentalPayload(
                    clientId: selectedClientId,
                    bikeId: selectedBikeId,
                    login: normalizedLogin,
                    password: normalizedPassword,
                    periodStart: normalizedStart,
                    periodEnd: normalizedEnd,
                    videoUrl: videoUrl.trimmedToOptional,
                    contractUrl: contractUrl.trimmedToOptional,
                    comment: comment.trimmedToOptional
                )
            )
        case let .edit(rentalId):
            onUpdate?(
                UpdateRentalPayload(
                    clientId: selectedClientId,
                    rentalId: rentalId,
                    bikeId: selectedBikeId,
                    periodStart: normalizedStart,
                    periodEnd: normalizedEnd,
                    login: normalizedLogin,
                    password: normalizedPassword,
                    videoUrl: videoUrl.trimmedToOptional,
                    contractUrl: contractUrl.trimmedToOptional,
                    comment: comment.trimmedToOptional
                )
            )
        }
    }

    private func isValidApiDate(_ value: String) -> Bool {
        guard value.count == 10 else { return false }
        let formatter = DateFormatter.apiDate
        return formatter.date(from: value) != nil
    }

    private func presentToast(_ message: String?) {
        guard let message = message?.trimmingCharacters(in: .whitespacesAndNewlines), !message.isEmpty else { return }
        toastDismissTask?.cancel()
        toastMessage = message
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            toastMessage = nil
        }
    }
}
