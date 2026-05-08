import SwiftUI
import PhotosUI
import UIKit

private struct DebtAdjustmentContext: Identifiable {
    let clientId: String
    let clientName: String
    let currentDebtRub: Int

    var id: String { clientId }
}

private struct CreateClientPhoneDraft: Identifiable {
    let id: UUID = .init()
    var label: String
    var number: String
}

struct AdminHomeView: View {
    @ObservedObject var viewModel: AdminHomeViewModel
    let onLogout: () -> Void

    @Environment(\.scenePhase) private var scenePhase

    @State private var isCreateRentalSheetPresented = false
    @State private var isServiceSheetPresented = false
    @State private var isClientCatalogPresented = false
    @State private var isBikeCatalogPresented = false
    @State private var isDetailsSheetPresented = false
    @State private var detailsClientId: String?
    @State private var debtAdjustmentContext: DebtAdjustmentContext?
    @State private var ignoredNextTapClientId: String?

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .idle, .loading:
                    ProgressView("Загружаем список аренд...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                case let .failed(message):
                    VStack(spacing: 12) {
                        Text("Не удалось загрузить аренды")
                            .font(.headline)
                            .foregroundStyle(AppDesign.titleText)
                        Text(message)
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                            .multilineTextAlignment(.center)
                        Button("Повторить") {
                            viewModel.load()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                case let .loaded(clients):
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            if let errorText = viewModel.operationErrorMessage {
                                messageBanner(
                                    title: "Ошибка операции",
                                    text: errorText,
                                    color: AppDesign.danger
                                )
                            }

                            if let successText = viewModel.operationSuccessMessage {
                                messageBanner(
                                    title: "Успешно",
                                    text: successText,
                                    color: AppDesign.success
                                )
                            }

                            if clients.isEmpty {
                                emptyRentalsView
                            } else {
                                ForEach(clients) { client in
                                    clientCard(client)
                                }
                            }
                        }
                        .padding(16)
                    }
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("Admin")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Выйти") {
                        onLogout()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Сервис") {
                        isServiceSheetPresented = true
                    }
                    .accessibilityIdentifier("admin.openServiceButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Новая аренда") {
                        isCreateRentalSheetPresented = true
                    }
                    .accessibilityIdentifier("admin.addRentalButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Обновить") { viewModel.load() }
                }
            }
        }
        .task {
            if case .idle = viewModel.state {
                viewModel.load()
            }
        }
        .sheet(isPresented: $isServiceSheetPresented) {
            AdminServiceSheet(
                onOpenClientsCatalog: {
                    isServiceSheetPresented = false
                    viewModel.refreshClientCatalog {
                        isClientCatalogPresented = true
                    }
                },
                onOpenBikeCatalog: {
                    isServiceSheetPresented = false
                    isBikeCatalogPresented = true
                }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $isCreateRentalSheetPresented) {
            CreateRentalSheet(
                clients: viewModel.clientCatalog,
                bikes: viewModel.bikes,
                isSaving: viewModel.isOperationInProgress,
                onCancel: { isCreateRentalSheetPresented = false },
                onCreate: { payload in
                    viewModel.createRental(payload: payload)
                    isCreateRentalSheetPresented = false
                }
            )
            .presentationDetents([.large])
        }
        .sheet(isPresented: $isClientCatalogPresented) {
            ClientCatalogSheet(
                clients: viewModel.clientCatalog,
                isSaving: viewModel.isOperationInProgress,
                apiErrorMessage: viewModel.operationErrorMessage,
                onCancel: { isClientCatalogPresented = false },
                onCreate: { payload, onSuccess in
                    viewModel.createClient(payload: payload, onSuccess: onSuccess)
                },
                onOpenClient: { client in
                    isClientCatalogPresented = false
                    DispatchQueue.main.async {
                        detailsClientId = client.clientId
                        isDetailsSheetPresented = true
                        viewModel.openClientDetails(clientId: client.clientId)
                    }
                }
            )
            .presentationDetents([.large])
        }
        .sheet(isPresented: $isBikeCatalogPresented) {
            BikeCatalogSheet(
                bikes: viewModel.bikes,
                isSaving: viewModel.isOperationInProgress,
                apiErrorMessage: viewModel.operationErrorMessage,
                onCancel: { isBikeCatalogPresented = false },
                onCreate: { payload, onSuccess in
                    viewModel.createBike(payload: payload, onSuccess: onSuccess)
                },
                onSave: { payload in
                    viewModel.updateBike(payload: payload)
                },
                onDelete: { bikeId in
                    viewModel.deleteBike(bikeId: bikeId)
                }
            )
            .presentationDetents([.large])
        }
        .sheet(isPresented: $isDetailsSheetPresented, onDismiss: {
            viewModel.closeClientDetails()
            detailsClientId = nil
        }) {
            AdminClientDetailsSheet(
                details: viewModel.selectedClientDetails,
                isLoading: viewModel.isDetailsLoading,
                errorMessage: viewModel.detailsErrorMessage,
                operationErrorMessage: viewModel.operationErrorMessage,
                operationSuccessMessage: viewModel.operationSuccessMessage,
                isOperationInProgress: viewModel.isOperationInProgress,
                clients: viewModel.clientCatalog,
                bikes: viewModel.bikes,
                onRetry: {
                    if let clientId = detailsClientId {
                        viewModel.openClientDetails(clientId: clientId)
                    }
                },
                onAdjustDebtTap: { details in
                    isDetailsSheetPresented = false
                    debtAdjustmentContext = DebtAdjustmentContext(
                        clientId: details.clientId,
                        clientName: details.fullName,
                        currentDebtRub: details.debtRub
                    )
                },
                onSaveRentalComment: { clientId, rentalId, comment in
                    viewModel.updateRentalComment(
                        clientId: clientId,
                        rentalId: rentalId,
                        comment: comment
                    )
                },
                onSaveRentalLinks: { clientId, rentalId, videoUrl, contractUrl in
                    viewModel.updateRentalLinks(
                        clientId: clientId,
                        rentalId: rentalId,
                        videoUrl: videoUrl,
                        contractUrl: contractUrl
                    )
                },
                onSaveClientProfile: { clientId, payload in
                    viewModel.updateClientProfile(clientId: clientId, payload: payload)
                },
                onDeleteClient: { clientId in
                    viewModel.deleteClient(clientId: clientId) {
                        isDetailsSheetPresented = false
                        detailsClientId = nil
                    }
                },
                onCreateRental: { payload in
                    viewModel.createRental(payload: payload)
                },
                onUpdateRental: { payload in
                    viewModel.updateRental(payload: payload)
                },
                onDeleteRental: { clientId, rentalId in
                    viewModel.deleteRental(clientId: clientId, rentalId: rentalId)
                }
            )
            .presentationDetents([.large])
        }
        .sheet(item: $debtAdjustmentContext) { context in
            DebtAdjustmentSheet(
                context: context,
                isSaving: viewModel.isOperationInProgress,
                onCancel: {
                    debtAdjustmentContext = nil
                },
                onApply: { amountRub, sign, comment in
                    viewModel.adjustDebt(
                        clientId: context.clientId,
                        amountRub: amountRub,
                        sign: sign,
                        comment: comment
                    )
                    debtAdjustmentContext = nil
                }
            )
            .presentationDetents([.medium])
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                viewModel.load()
            }
        }
    }

    private var emptyRentalsView: some View {
        VStack(spacing: 14) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)

            VStack(spacing: 4) {
                Text("Аренд пока нет")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Text("Клиентов в каталоге: \(viewModel.clientCatalog.count)")
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            }

            HStack(spacing: 10) {
                Button("Каталог клиентов") {
                    isClientCatalogPresented = true
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("admin.emptyOpenClientCatalogButton")

                Button("Новая аренда") {
                    isCreateRentalSheetPresented = true
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.clientCatalog.isEmpty || viewModel.bikes.isEmpty)
                .accessibilityIdentifier("admin.emptyCreateRentalButton")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 260)
        .padding(20)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func clientCard(_ client: AdminClientSummaryResponse) -> some View {
        HStack(alignment: .top, spacing: 12) {
            bikeAvatar(urlString: client.bikeAvatarUrl)

            VStack(alignment: .leading, spacing: 6) {
                Text(client.fullName)
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Text(client.statusText)
                    .font(.subheadline)
                    .foregroundStyle(client.debtRub > 0 ? AppDesign.danger : AppDesign.subtleText)
                Text(client.bikeModel)
                    .font(.caption)
                    .foregroundStyle(AppDesign.subtleText)
                Text("Корректировка: \(client.totalAdjustmentRub) ₽")
                    .font(.caption)
                    .foregroundStyle(AppDesign.subtleText)
            }

            Spacer(minLength: 8)

            Button {
                ignoredNextTapClientId = client.clientId
                debtAdjustmentContext = DebtAdjustmentContext(
                    clientId: client.clientId,
                    clientName: client.fullName,
                    currentDebtRub: client.debtRub
                )
            } label: {
                VStack(spacing: 4) {
                    Text(client.debtRub > 0 ? "Долг" : "Прибыль")
                        .font(.caption2.weight(.semibold))
                    Text("\(client.debtRub > 0 ? client.debtRub : client.profitRub) ₽")
                        .font(.subheadline.weight(.bold))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(client.debtRub > 0 ? AppDesign.danger : AppDesign.success)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture {
            if ignoredNextTapClientId == client.clientId {
                ignoredNextTapClientId = nil
                return
            }
            detailsClientId = client.clientId
            isDetailsSheetPresented = true
            viewModel.openClientDetails(clientId: client.clientId)
        }
    }

    private func bikeAvatar(urlString: String) -> some View {
        BikePhotoView(source: urlString) {
            placeholderBikeAvatar
        }
        .frame(width: 58, height: 58)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var placeholderBikeAvatar: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(14)
            .foregroundStyle(AppDesign.iconSoft)
    }

    private func messageBanner(title: String, text: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(color)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(AppDesign.titleText)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct AdminServiceSheet: View {
    let onOpenClientsCatalog: () -> Void
    let onOpenBikeCatalog: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                serviceActionCard(
                    title: "Клиенты",
                    subtitle: "Список всех клиентов и редактирование",
                    icon: "person.2.fill",
                    action: onOpenClientsCatalog
                )
                .accessibilityIdentifier("admin.service.clientsCatalogButton")

                serviceActionCard(
                    title: "Велосипеды",
                    subtitle: "Список всех велосипедов и базовое редактирование",
                    icon: "list.bullet.rectangle",
                    action: onOpenBikeCatalog
                )
                .accessibilityIdentifier("admin.service.bikesCatalogButton")
                Spacer()
            }
            .padding(16)
            .background(AppDesign.pageBackground.ignoresSafeArea())
            .navigationTitle("Сервис")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Закрыть") { dismiss() }
                }
            }
        }
    }

    private func serviceActionCard(
        title: String,
        subtitle: String,
        icon: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(AppDesign.accent)
                    .frame(width: 40, height: 40)
                    .background(AppDesign.surfaceBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(AppDesign.subtleText)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppDesign.subtleText)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppDesign.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct CreateClientSheet: View {
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload) -> Void

    @State private var fullName = ""
    @State private var address = ""
    @State private var passportData = ""
    @State private var phones: [CreateClientPhoneDraft] = [
        CreateClientPhoneDraft(label: "Рабочий (TG)", number: "")
    ]
    @State private var validationError: String?

    var body: some View {
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createClient.validationError")
                    }
                }

                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createClient.apiError")
                    }
                }

                Section("Профиль") {
                    TextField("ФИО", text: $fullName)
                        .accessibilityIdentifier("createClient.fullNameField")
                    TextField("Адрес", text: $address)
                        .accessibilityIdentifier("createClient.addressField")
                    TextField("Паспортные данные", text: $passportData)
                        .accessibilityIdentifier("createClient.passportField")
                }

                Section("Телефоны") {
                    ForEach(Array(phones.indices), id: \.self) { index in
                        TextField("Подпись", text: $phones[index].label)
                            .accessibilityIdentifier(
                                index == 0
                                    ? "createClient.phoneLabel1Field"
                                    : "createClient.phoneLabelField.\(index)"
                            )
                        TextField("Телефон", text: $phones[index].number)
                            .keyboardType(.phonePad)
                            .accessibilityIdentifier(
                                index == 0
                                    ? "createClient.phoneNumber1Field"
                                    : "createClient.phoneNumberField.\(index)"
                            )
                    }
                    Button("Добавить телефон") {
                        phones.append(CreateClientPhoneDraft(label: "", number: ""))
                    }
                    .accessibilityIdentifier("createClient.addPhoneButton")
                }

            }
            .navigationTitle("Новый клиент")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createClient.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createClient.submitButton")
                }
            }
        }
    }

    private func submit() {
        validationError = nil
        let input = CreateClientFormInput(
            fullName: fullName,
            address: address,
            passportData: passportData,
            phones: phones.map { CreateClientPhoneInput(label: $0.label, number: $0.number) }
        )

        switch CreateClientFormValidator.buildPayload(from: input) {
        case let .success(payload):
            onCreate(payload)
        case let .failure(error):
            validationError = error.localizedDescription
        }
    }
}

private struct CreateBikeSheet: View {
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateBikePayload) -> Void

    @State private var bikeModel = ""
    @State private var weeklyRateRub = "3000"
    @State private var frameSerialNumber = ""
    @State private var motorSerialNumber = ""
    @State private var batterySerialNumber1 = ""
    @State private var batterySerialNumber2 = ""
    @State private var validationError: String?
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoPreview: Image?
    @State private var encodedPhotoDataUrl: String?

    var body: some View {
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                    }
                }

                Section("Фото велосипеда") {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Text("Выбрать фото из галереи")
                    }
                    .accessibilityIdentifier("createBike.photoPicker")
                    if let selectedPhotoPreview {
                        selectedPhotoPreview
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Section("Параметры велосипеда") {
                    TextField("Модель велосипеда", text: $bikeModel)
                        .accessibilityIdentifier("createBike.modelField")
                    TextField("Стоимость недели аренды, ₽", text: $weeklyRateRub)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("createBike.weeklyRateField")
                    TextField("Серийный номер рамы", text: $frameSerialNumber)
                        .accessibilityIdentifier("createBike.frameSerialField")
                    TextField("Серийный номер мотора", text: $motorSerialNumber)
                        .accessibilityIdentifier("createBike.motorSerialField")
                    TextField("Серийный номер аккумулятора 1", text: $batterySerialNumber1)
                        .accessibilityIdentifier("createBike.battery1Field")
                    TextField("Серийный номер аккумулятора 2", text: $batterySerialNumber2)
                        .accessibilityIdentifier("createBike.battery2Field")
                }
            }
            .navigationTitle("Новый велосипед")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена", action: onCancel)
                        .disabled(isSaving)
                        .accessibilityIdentifier("createBike.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") { submit() }
                        .disabled(isSaving)
                        .accessibilityIdentifier("createBike.submitButton")
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
    }

    private func submit() {
        validationError = nil
        let model = bikeModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let frame = frameSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let motor = motorSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery1 = batterySerialNumber1.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery2 = batterySerialNumber2.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !model.isEmpty else {
            validationError = "Укажите модель велосипеда"
            return
        }
        guard let weeklyRate = Int(weeklyRateRub.trimmingCharacters(in: .whitespacesAndNewlines)), weeklyRate > 0 else {
            validationError = "Стоимость недели должна быть положительным числом"
            return
        }
        guard !frame.isEmpty else {
            validationError = "Укажите серийный номер рамы"
            return
        }
        guard !motor.isEmpty else {
            validationError = "Укажите серийный номер мотора"
            return
        }
        guard !battery1.isEmpty else {
            validationError = "Укажите серийный номер аккумулятора 1"
            return
        }

        let duplicateValidation = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: bikes,
            bikeIdToIgnore: nil,
            frameSerial: frame,
            motorSerial: motor,
            batterySerialNumber1: battery1,
            batterySerialNumber2: battery2.isEmpty ? nil : battery2
        )
        if let duplicateValidation {
            validationError = duplicateValidation
            return
        }

        onCreate(
            CreateBikePayload(
                photoUrl: encodedPhotoDataUrl,
                bikeModel: model,
                weeklyRateRub: weeklyRate,
                frameSerialNumber: frame,
                motorSerialNumber: motor,
                batterySerialNumber1: battery1,
                batterySerialNumber2: battery2.isEmpty ? nil : battery2
            )
        )
    }

    private func loadPhoto(item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            guard let uiImage = UIImage(data: data) else { return }
            let jpeg = uiImage.jpegData(compressionQuality: 0.82) ?? data
            await MainActor.run {
                selectedPhotoPreview = Image(uiImage: uiImage)
                encodedPhotoDataUrl = "data:image/jpeg;base64,\(jpeg.base64EncodedString())"
            }
        } catch {
            await MainActor.run {
                validationError = "Не удалось загрузить фото"
            }
        }
    }
}

private struct ClientCatalogSheet: View {
    let clients: [AdminClientSummaryResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload, @escaping () -> Void) -> Void
    let onOpenClient: (AdminClientSummaryResponse) -> Void

    @State private var isCreateClientPresented = false

    var body: some View {
        NavigationStack {
            Group {
                if clients.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(AppDesign.iconSoft)
                        Text("Список клиентов пуст")
                            .font(.headline)
                            .foregroundStyle(AppDesign.titleText)
                        Text("Создайте первого клиента внутри этого списка.")
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                            .multilineTextAlignment(.center)
                        Button("Создать клиента") {
                            isCreateClientPresented = true
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("clientCatalog.emptyCreateClientButton")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
                } else {
                    List {
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Section {
                                Text(apiErrorMessage)
                                    .foregroundStyle(AppDesign.danger)
                            }
                        }

                        ForEach(clients) { client in
                            Button {
                                onOpenClient(client)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "person.crop.circle")
                                        .font(.system(size: 28, weight: .semibold))
                                        .foregroundStyle(AppDesign.iconSoft)
                                        .frame(width: 48, height: 48)
                                        .background(AppDesign.surfaceBackground)
                                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(client.fullName)
                                            .font(.headline)
                                            .foregroundStyle(AppDesign.titleText)
                                        if let login = client.clientLogin, !login.isEmpty {
                                            Text("Логин: \(login)")
                                                .font(.subheadline)
                                                .foregroundStyle(AppDesign.subtleText)
                                        }
                                        Text(client.bikeModel)
                                            .font(.caption)
                                            .foregroundStyle(AppDesign.subtleText)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(AppDesign.subtleText)
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("clientCatalog.open.\(client.fullName)")
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("Клиенты")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { onCancel() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Новый клиент") {
                        isCreateClientPresented = true
                    }
                    .accessibilityIdentifier("clientCatalog.addClientButton")
                }
            }
        }
        .sheet(isPresented: $isCreateClientPresented) {
            CreateClientSheet(
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { isCreateClientPresented = false },
                onCreate: { payload in
                    onCreate(payload) {
                        isCreateClientPresented = false
                    }
                }
            )
            .presentationDetents([.large])
        }
    }
}

private struct BikeCatalogSheet: View {
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateBikePayload, @escaping () -> Void) -> Void
    let onSave: (UpdateBikePayload) -> Void
    let onDelete: (String) -> Void

    @State private var editingBike: AdminBikeResponse?
    @State private var bikePendingDeletion: AdminBikeResponse?
    @State private var isCreateBikePresented = false

    var body: some View {
        NavigationStack {
            Group {
                if bikes.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bicycle")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(AppDesign.iconSoft)
                        Text("Список велосипедов пуст")
                            .font(.headline)
                            .foregroundStyle(AppDesign.titleText)
                        Text("Создайте первый велосипед внутри этого списка.")
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                            .multilineTextAlignment(.center)
                        Button("Создать велосипед") {
                            isCreateBikePresented = true
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("bikeCatalog.emptyCreateBikeButton")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
                } else {
                    List {
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Section {
                                Text(apiErrorMessage)
                                    .foregroundStyle(AppDesign.danger)
                            }
                        }

                        ForEach(bikes) { bike in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(alignment: .top, spacing: 12) {
                                    bikePreview(urlString: bike.photoUrl)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(bike.bikeModel)
                                            .font(.headline)
                                            .foregroundStyle(AppDesign.titleText)
                                        Text("\(bike.weeklyRateRub) ₽ / неделя")
                                            .font(.subheadline)
                                            .foregroundStyle(AppDesign.subtleText)
                                    }
                                    Spacer()
                                    Button("Редактировать") {
                                        editingBike = bike
                                    }
                                    .buttonStyle(.bordered)
                                    .accessibilityIdentifier("bikeCatalog.edit.\(bike.bikeModel)")

                                    Button("Удалить", role: .destructive) {
                                        bikePendingDeletion = bike
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(isSaving)
                                    .accessibilityIdentifier("bikeCatalog.delete.\(bike.bikeModel)")
                                }

                                Text("Рама: \(bike.frameSerialNumber)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                Text("Мотор: \(bike.motorSerialNumber)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                Text("АКБ1: \(bike.batterySerialNumber1)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                if let battery2 = bike.batterySerialNumber2, !battery2.isEmpty {
                                    Text("АКБ2: \(battery2)")
                                        .font(.caption)
                                        .foregroundStyle(AppDesign.subtleText)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("Велосипеды")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { onCancel() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Новый велосипед") {
                        isCreateBikePresented = true
                    }
                    .accessibilityIdentifier("bikeCatalog.addBikeButton")
                }
            }
        }
        .sheet(isPresented: $isCreateBikePresented) {
            CreateBikeSheet(
                bikes: bikes,
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { isCreateBikePresented = false },
                onCreate: { payload in
                    onCreate(payload) {
                        isCreateBikePresented = false
                    }
                }
            )
            .presentationDetents([.large])
        }
        .sheet(item: $editingBike) { bike in
            EditBikeSheet(
                bike: bike,
                bikes: bikes,
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { editingBike = nil },
                onSave: { payload in
                    onSave(payload)
                    editingBike = nil
                }
            )
            .presentationDetents([.large])
        }
        .confirmationDialog(
            "Удалить велосипед?",
            isPresented: Binding(
                get: { bikePendingDeletion != nil },
                set: { isPresented in
                    if !isPresented {
                        bikePendingDeletion = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                if let bike = bikePendingDeletion {
                    onDelete(bike.bikeId)
                    bikePendingDeletion = nil
                }
            }
            Button("Отмена", role: .cancel) {
                bikePendingDeletion = nil
            }
        } message: {
            Text("Велосипед без истории аренд будет удален из каталога.")
        }
    }

    private func bikePreview(urlString: String?) -> some View {
        BikePhotoView(source: urlString) {
            placeholder
        }
        .frame(width: 58, height: 58)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var placeholder: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(14)
            .foregroundStyle(AppDesign.iconSoft)
    }
}

private struct EditBikeSheet: View {
    let bike: AdminBikeResponse
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onSave: (UpdateBikePayload) -> Void

    @State private var bikeModel: String
    @State private var weeklyRateRub: String
    @State private var frameSerialNumber: String
    @State private var motorSerialNumber: String
    @State private var batterySerialNumber1: String
    @State private var batterySerialNumber2: String
    @State private var validationError: String?
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoPreview: Image?
    @State private var overridePhotoDataUrl: String?

    init(
        bike: AdminBikeResponse,
        bikes: [AdminBikeResponse],
        isSaving: Bool,
        apiErrorMessage: String?,
        onCancel: @escaping () -> Void,
        onSave: @escaping (UpdateBikePayload) -> Void
    ) {
        self.bike = bike
        self.bikes = bikes
        self.isSaving = isSaving
        self.apiErrorMessage = apiErrorMessage
        self.onCancel = onCancel
        self.onSave = onSave
        _bikeModel = State(initialValue: bike.bikeModel)
        _weeklyRateRub = State(initialValue: "\(bike.weeklyRateRub)")
        _frameSerialNumber = State(initialValue: bike.frameSerialNumber)
        _motorSerialNumber = State(initialValue: bike.motorSerialNumber)
        _batterySerialNumber1 = State(initialValue: bike.batterySerialNumber1)
        _batterySerialNumber2 = State(initialValue: bike.batterySerialNumber2 ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                    }
                }

                Section("Фото велосипеда") {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Text("Заменить фото")
                    }
                    .accessibilityIdentifier("editBike.photoPicker")
                    if let selectedPhotoPreview {
                        selectedPhotoPreview
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Section("Параметры велосипеда") {
                    TextField("Модель велосипеда", text: $bikeModel)
                        .accessibilityIdentifier("editBike.modelField")
                    TextField("Стоимость недели аренды, ₽", text: $weeklyRateRub)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("editBike.weeklyRateField")
                    TextField("Серийный номер рамы", text: $frameSerialNumber)
                        .accessibilityIdentifier("editBike.frameSerialField")
                    TextField("Серийный номер мотора", text: $motorSerialNumber)
                        .accessibilityIdentifier("editBike.motorSerialField")
                    TextField("Серийный номер аккумулятора 1", text: $batterySerialNumber1)
                        .accessibilityIdentifier("editBike.battery1Field")
                    TextField("Серийный номер аккумулятора 2", text: $batterySerialNumber2)
                        .accessibilityIdentifier("editBike.battery2Field")
                }
            }
            .navigationTitle("Редактировать велосипед")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена", action: onCancel)
                        .disabled(isSaving)
                        .accessibilityIdentifier("editBike.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("editBike.submitButton")
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
    }

    private func submit() {
        validationError = nil

        let model = bikeModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let frame = frameSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let motor = motorSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery1 = batterySerialNumber1.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery2 = batterySerialNumber2.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !model.isEmpty else {
            validationError = "Укажите модель велосипеда"
            return
        }
        guard let weeklyRate = Int(weeklyRateRub.trimmingCharacters(in: .whitespacesAndNewlines)), weeklyRate > 0 else {
            validationError = "Стоимость недели должна быть положительным числом"
            return
        }
        guard !frame.isEmpty else {
            validationError = "Укажите серийный номер рамы"
            return
        }
        guard !motor.isEmpty else {
            validationError = "Укажите серийный номер мотора"
            return
        }
        guard !battery1.isEmpty else {
            validationError = "Укажите серийный номер аккумулятора 1"
            return
        }

        let duplicateValidation = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: bikes,
            bikeIdToIgnore: bike.bikeId,
            frameSerial: frame,
            motorSerial: motor,
            batterySerialNumber1: battery1,
            batterySerialNumber2: battery2.isEmpty ? nil : battery2
        )
        if let duplicateValidation {
            validationError = duplicateValidation
            return
        }

        onSave(
            UpdateBikePayload(
                bikeId: bike.bikeId,
                photoUrl: overridePhotoDataUrl ?? bike.photoUrl,
                bikeModel: model,
                weeklyRateRub: weeklyRate,
                frameSerialNumber: frame,
                motorSerialNumber: motor,
                batterySerialNumber1: battery1,
                batterySerialNumber2: battery2.isEmpty ? nil : battery2
            )
        )
    }

    private func loadPhoto(item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            guard let uiImage = UIImage(data: data) else { return }
            let jpeg = uiImage.jpegData(compressionQuality: 0.82) ?? data
            await MainActor.run {
                selectedPhotoPreview = Image(uiImage: uiImage)
                overridePhotoDataUrl = "data:image/jpeg;base64,\(jpeg.base64EncodedString())"
            }
        } catch {
            await MainActor.run {
                validationError = "Не удалось загрузить фото"
            }
        }
    }
}

private struct DebtAdjustmentSheet: View {
    let context: DebtAdjustmentContext
    let isSaving: Bool
    let onCancel: () -> Void
    let onApply: (Int, DebtAdjustmentSign, String?) -> Void

    @State private var sign: DebtAdjustmentSign = .minus
    @State private var amountRub = ""
    @State private var comment = ""
    @State private var validationError: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Клиент") {
                    Text(context.clientName)
                    Text("Текущий долг: \(context.currentDebtRub) ₽")
                        .foregroundStyle(AppDesign.subtleText)
                }

                Section("Корректировка") {
                    Picker("Тип", selection: $sign) {
                        Text("Уменьшить долг").tag(DebtAdjustmentSign.minus)
                        Text("Увеличить долг").tag(DebtAdjustmentSign.plus)
                    }
                    .pickerStyle(.segmented)

                    TextField("Сумма, ₽", text: $amountRub)
                        .keyboardType(.numberPad)
                    TextField("Комментарий (необязательно)", text: $comment)
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
            }
            .navigationTitle("Корректировка")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() {
        validationError = nil
        guard let amount = Int(amountRub), amount > 0 else {
            validationError = "Введите положительную сумму"
            return
        }

        onApply(amount, sign, comment.trimmedToOptional)
    }
}

private struct AdminClientDetailsSheet: View {
    let details: AdminClientDetailsResponse?
    let isLoading: Bool
    let errorMessage: String?
    let operationErrorMessage: String?
    let operationSuccessMessage: String?
    let isOperationInProgress: Bool
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let onRetry: () -> Void
    let onAdjustDebtTap: (AdminClientDetailsResponse) -> Void
    let onSaveRentalComment: (String, String, String) -> Void
    let onSaveRentalLinks: (String, String, String?, String?) -> Void
    let onSaveClientProfile: (String, UpdateClientProfilePayload) -> Void
    let onDeleteClient: (String) -> Void
    let onCreateRental: (CreateRentalPayload) -> Void
    let onUpdateRental: (UpdateRentalPayload) -> Void
    let onDeleteRental: (String, String) -> Void

    @Environment(\.openURL) private var openURL
    @State private var isProfileEditorPresented = false
    @State private var isCreateRentalPresented = false
    @State private var isDeleteClientConfirmationPresented = false

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && details == nil {
                    ProgressView("Загружаем карточку клиента...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let errorMessage, details == nil {
                    VStack(spacing: 12) {
                        Text("Не удалось загрузить клиента")
                            .font(.headline)
                            .foregroundStyle(AppDesign.titleText)
                        Text(errorMessage)
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                            .multilineTextAlignment(.center)
                        Button("Повторить") {
                            onRetry()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let details {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            header(details)
                            profileSection(details)
                            financialSection(details)
                            rentalsSection(details)
                            if let operationErrorMessage {
                                Text(operationErrorMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.danger)
                            }
                            if let operationSuccessMessage {
                                Text(operationSuccessMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.success)
                            }
                        }
                        .padding(16)
                    }
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                    .sheet(isPresented: $isProfileEditorPresented) {
                        EditClientProfileSheet(
                            details: details,
                            isSaving: isOperationInProgress,
                            onCancel: {
                                isProfileEditorPresented = false
                            },
                            onSave: { payload in
                                onSaveClientProfile(details.clientId, payload)
                                isProfileEditorPresented = false
                            }
                        )
                        .presentationDetents([.large])
                    }
                    .sheet(isPresented: $isCreateRentalPresented) {
                        CreateRentalSheet(
                            clients: clients,
                            bikes: bikes,
                            preselectedClientId: details.clientId,
                            isSaving: isOperationInProgress,
                            onCancel: {
                                isCreateRentalPresented = false
                            },
                            onCreate: { payload in
                                onCreateRental(payload)
                                isCreateRentalPresented = false
                            }
                        )
                        .presentationDetents([.large])
                    }
                    .overlay(alignment: .center) {
                        if isOperationInProgress {
                            ProgressView()
                                .padding(16)
                                .background(.ultraThinMaterial)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                    }
                } else {
                    Color.clear
                }
            }
            .navigationTitle("Клиент")
        }
    }

    private func header(_ details: AdminClientDetailsResponse) -> some View {
        HStack(spacing: 12) {
            BikePhotoView(source: details.bikeAvatarUrl) {
                Image(systemName: "bicycle")
                    .resizable()
                    .scaledToFit()
                    .padding(14)
                    .foregroundStyle(AppDesign.iconSoft)
            }
            .frame(width: 64, height: 64)
            .background(AppDesign.surfaceBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(details.fullName)
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Text(details.bikeModel)
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            }
            Spacer()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func profileSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Профиль")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Spacer()
                Button("Редактировать") {
                    isProfileEditorPresented = true
                }
                .buttonStyle(.bordered)
            }
            detailRow("Адрес", details.address)
            detailRow("Паспорт", details.passportData)
            detailRow("Дата старта аренды", details.rentalStart)
            detailRow("Оплачено до", details.paidUntil)

            if !details.phones.isEmpty {
                Text("Телефоны")
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 4)
                ForEach(details.phones) { phone in
                    detailRow(phone.label, phone.number)
                }
            }

            if details.rentals.isEmpty {
                Button("Удалить клиента", role: .destructive) {
                    isDeleteClientConfirmationPresented = true
                }
                .buttonStyle(.bordered)
                .disabled(isOperationInProgress)
                .padding(.top, 4)
                .accessibilityIdentifier("clientDetails.deleteClientButton")
            } else {
                Text("Клиента с историей аренд нельзя удалить, профиль можно только редактировать.")
                    .font(.caption)
                    .foregroundStyle(AppDesign.subtleText)
                    .padding(.top, 4)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .confirmationDialog(
            "Удалить клиента?",
            isPresented: $isDeleteClientConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                onDeleteClient(details.clientId)
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Клиент без истории аренд будет удален из каталога.")
        }
    }

    private func financialSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Финансы")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)
            detailRow("Тариф за неделю", "\(details.weeklyRateRub) ₽")
            detailRow("Всего оплачено", "\(details.totalPaidRub) ₽")
            detailRow("Текущий долг", "\(details.debtRub) ₽", color: details.debtRub > 0 ? AppDesign.danger : AppDesign.titleText)
            detailRow("Суммарная корректировка", "\(details.totalAdjustmentRub) ₽")

            Button("Скорректировать долг") {
                onAdjustDebtTap(details)
            }
            .buttonStyle(.borderedProminent)
            .tint(details.debtRub > 0 ? AppDesign.danger : AppDesign.accent)
            .padding(.top, 4)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func rentalsSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Аренды")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Spacer()
                Button("Добавить аренду") {
                    isCreateRentalPresented = true
                }
                .buttonStyle(.bordered)
                .font(.subheadline)
                .accessibilityIdentifier("clientDetails.addRentalButton")
            }

            if details.rentals.isEmpty {
                Text("История аренд пока пустая")
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            } else {
                ForEach(details.rentals) { rental in
                    RentalHistoryCard(
                        clientId: details.clientId,
                        rental: rental,
                        bikes: bikes,
                        onOpenVideo: {
                            openOptionalURL(rental.videoUrl)
                        },
                        onOpenContract: {
                            openOptionalURL(rental.contractUrl)
                        },
                        onSaveComment: { clientId, rentalId, comment in
                            onSaveRentalComment(clientId, rentalId, comment)
                        },
                        onSaveLinks: { clientId, rentalId, videoUrl, contractUrl in
                            onSaveRentalLinks(clientId, rentalId, videoUrl, contractUrl)
                        },
                        onSaveRental: { payload in
                            onUpdateRental(payload)
                        },
                        onDeleteRental: { clientId, rentalId in
                            onDeleteRental(clientId, rentalId)
                        }
                    )
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func detailRow(_ title: String, _ value: String, color: Color = AppDesign.titleText) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppDesign.subtleText)
            Text(value.isEmpty ? "-" : value)
                .font(.subheadline)
                .foregroundStyle(color)
        }
    }

    private func openOptionalURL(_ raw: String?) {
        guard let raw, let url = URL(string: raw), !raw.isEmpty else {
            return
        }
        openURL(url)
    }
}

private struct CreateRentalSheet: View {
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let preselectedClientId: String?
    let isSaving: Bool
    let onCancel: () -> Void
    let onCreate: (CreateRentalPayload) -> Void

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
        let initialClientId = preselectedClientId ?? clients.first?.clientId ?? ""
        let initialBikeId = bikes.first?.bikeId ?? ""
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

    var body: some View {
        NavigationStack {
            Form {
                Section("Клиент и велосипед") {
                    if clients.isEmpty {
                        Text("Сначала создайте клиента в сервисном экране")
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Клиент", selection: $selectedClientId) {
                            ForEach(clients) { client in
                                Text(client.fullName).tag(client.clientId)
                            }
                        }
                        .accessibilityIdentifier("createRental.clientPicker")
                    }

                    if bikes.isEmpty {
                        Text("Сначала создайте велосипед в сервисном экране")
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Велосипед", selection: $selectedBikeId) {
                            ForEach(bikes) { bike in
                                Text("\(bike.bikeModel) • \(bike.weeklyRateRub) ₽/нед").tag(bike.bikeId)
                            }
                        }
                        .accessibilityIdentifier("createRental.bikePicker")
                    }

                    TextField("Дата начала (YYYY-MM-DD)", text: $periodStart)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.periodStartField")
                    TextField("Дата окончания (необязательно)", text: $periodEnd)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.periodEndField")
                }

                Section("Доступ клиента") {
                    TextField("Логин клиента", text: $login)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.loginField")
                    SecureField("Пароль клиента", text: $password)
                        .accessibilityIdentifier("createRental.passwordField")
                }

                Section("Документы и комментарий") {
                    TextField("Ссылка на видео", text: $videoUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.videoUrlField")
                    TextField("Ссылка на договор", text: $contractUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.contractUrlField")
                    TextField("Комментарий", text: $comment, axis: .vertical)
                        .lineLimit(3, reservesSpace: true)
                        .accessibilityIdentifier("createRental.commentField")
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createRental.validationError")
                    }
                }
            }
            .navigationTitle("Новая аренда")
            .onChange(of: selectedClientId) { newClientId in
                if let suggestedLogin = clients.first(where: { $0.clientId == newClientId })?.clientLogin,
                   !suggestedLogin.isEmpty {
                    login = suggestedLogin
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createRental.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createRental.submitButton")
                }
            }
        }
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

        onCreate(
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
    }

    private func isValidApiDate(_ value: String) -> Bool {
        guard value.count == 10 else { return false }
        let formatter = DateFormatter.apiDate
        return formatter.date(from: value) != nil
    }
}

private struct EditClientProfilePhone: Identifiable {
    let id: String
    var label: String
    var number: String
}

private struct EditClientProfileSheet: View {
    let details: AdminClientDetailsResponse
    let isSaving: Bool
    let onCancel: () -> Void
    let onSave: (UpdateClientProfilePayload) -> Void

    @State private var fullName: String
    @State private var address: String
    @State private var passportData: String
    @State private var phones: [EditClientProfilePhone]
    @State private var validationError: String?

    init(
        details: AdminClientDetailsResponse,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onSave: @escaping (UpdateClientProfilePayload) -> Void
    ) {
        self.details = details
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onSave = onSave
        _fullName = State(initialValue: details.fullName)
        _address = State(initialValue: details.address)
        _passportData = State(initialValue: details.passportData)
        _phones = State(initialValue: details.phones.map {
            EditClientProfilePhone(id: $0.id, label: $0.label, number: $0.number)
        })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Профиль") {
                    TextField("ФИО", text: $fullName)
                    TextField("Адрес", text: $address)
                    TextField("Паспортные данные", text: $passportData)
                }

                Section("Телефоны") {
                    ForEach($phones) { $phone in
                        VStack(alignment: .leading, spacing: 6) {
                            TextField("Подпись", text: $phone.label)
                            TextField("Телефон", text: $phone.number)
                                .keyboardType(.phonePad)
                        }
                    }

                    Button("Добавить телефон") {
                        phones.append(
                            EditClientProfilePhone(
                                id: UUID().uuidString,
                                label: "",
                                number: ""
                            )
                        )
                    }
                    .buttonStyle(.bordered)
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
            }
            .navigationTitle("Редактировать клиента")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() {
        validationError = nil

        let normalizedFullName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedAddress = address.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassport = passportData.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !normalizedFullName.isEmpty else {
            validationError = "Укажите ФИО клиента"
            return
        }

        let normalizedPhones = phones
            .map { rawPhone in
                AdminClientPhone(
                    id: rawPhone.id,
                    label: rawPhone.label.trimmingCharacters(in: .whitespacesAndNewlines),
                    number: rawPhone.number.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            }
            .filter { !$0.label.isEmpty && !$0.number.isEmpty }

        let payload = UpdateClientProfilePayload(
            fullName: normalizedFullName,
            address: normalizedAddress,
            passportData: normalizedPassport,
            phones: normalizedPhones
        )
        onSave(payload)
    }
}

private struct RentalHistoryCard: View {
    let clientId: String
    let rental: AdminRentalHistoryItem
    let bikes: [AdminBikeResponse]
    let onOpenVideo: () -> Void
    let onOpenContract: () -> Void
    let onSaveComment: (String, String, String) -> Void
    let onSaveLinks: (String, String, String?, String?) -> Void
    let onSaveRental: (UpdateRentalPayload) -> Void
    let onDeleteRental: (String, String) -> Void

    @State private var isEditingComment = false
    @State private var commentDraft: String
    @State private var isEditingVideoLink = false
    @State private var isEditingContractLink = false
    @State private var isEditingRental = false
    @State private var isDeleteConfirmationPresented = false
    @State private var videoUrlDraft: String
    @State private var contractUrlDraft: String
    @State private var selectedBikeId: String
    @State private var periodStartDraft: String
    @State private var periodEndDraft: String
    @State private var rentalValidationError: String?

    init(
        clientId: String,
        rental: AdminRentalHistoryItem,
        bikes: [AdminBikeResponse],
        onOpenVideo: @escaping () -> Void,
        onOpenContract: @escaping () -> Void,
        onSaveComment: @escaping (String, String, String) -> Void,
        onSaveLinks: @escaping (String, String, String?, String?) -> Void,
        onSaveRental: @escaping (UpdateRentalPayload) -> Void,
        onDeleteRental: @escaping (String, String) -> Void
    ) {
        self.clientId = clientId
        self.rental = rental
        self.bikes = bikes
        self.onOpenVideo = onOpenVideo
        self.onOpenContract = onOpenContract
        self.onSaveComment = onSaveComment
        self.onSaveLinks = onSaveLinks
        self.onSaveRental = onSaveRental
        self.onDeleteRental = onDeleteRental
        _commentDraft = State(initialValue: rental.comment ?? "")
        _videoUrlDraft = State(initialValue: rental.videoUrl ?? "")
        _contractUrlDraft = State(initialValue: rental.contractUrl ?? "")
        _selectedBikeId = State(initialValue: rental.bikeId)
        _periodStartDraft = State(initialValue: rental.periodStart)
        _periodEndDraft = State(initialValue: rental.periodEnd ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                avatar

                VStack(alignment: .leading, spacing: 4) {
                    Text(periodText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppDesign.titleText)
                    Text(rental.bikeModel)
                        .font(.caption)
                        .foregroundStyle(AppDesign.subtleText)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 6) {
                    HStack(spacing: 6) {
                        Button("Видео") { onOpenVideo() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("rentalCard.openVideoButton")
                        Button("Ред.") {
                            isEditingVideoLink.toggle()
                            if isEditingVideoLink {
                                isEditingContractLink = false
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.editVideoLinkButton")
                    }
                    HStack(spacing: 6) {
                        Button("Договор") { onOpenContract() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("rentalCard.openContractButton")
                        Button("Ред.") {
                            isEditingContractLink.toggle()
                            if isEditingContractLink {
                                isEditingVideoLink = false
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.editContractLinkButton")
                    }
                    Button(isEditingComment ? "Скрыть" : "Комментарий") {
                        isEditingComment.toggle()
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("rentalCard.toggleCommentButton")

                    HStack(spacing: 6) {
                        Button(isEditingRental ? "Скрыть" : "Изм.") {
                            isEditingRental.toggle()
                            if isEditingRental {
                                rentalValidationError = nil
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.toggleEditButton")

                        Button("Удалить") {
                            isDeleteConfirmationPresented = true
                        }
                        .buttonStyle(.bordered)
                        .tint(AppDesign.danger)
                        .accessibilityIdentifier("rentalCard.deleteButton")
                    }
                }
                .font(.caption)
            }

            if let comment = rental.comment, !comment.isEmpty {
                Text(comment)
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.titleText)
            }

            if isEditingVideoLink {
                linkEditor(
                    title: "Ссылка на видео",
                    text: $videoUrlDraft,
                    onCancel: {
                        videoUrlDraft = rental.videoUrl ?? ""
                        isEditingVideoLink = false
                    },
                    onSave: {
                        onSaveLinks(clientId, rental.id, videoUrlDraft.trimmedToOptional, contractUrlDraft.trimmedToOptional)
                        isEditingVideoLink = false
                    }
                )
            }

            if isEditingContractLink {
                linkEditor(
                    title: "Ссылка на договор",
                    text: $contractUrlDraft,
                    onCancel: {
                        contractUrlDraft = rental.contractUrl ?? ""
                        isEditingContractLink = false
                    },
                    onSave: {
                        onSaveLinks(clientId, rental.id, videoUrlDraft.trimmedToOptional, contractUrlDraft.trimmedToOptional)
                        isEditingContractLink = false
                    }
                )
            }

            if isEditingComment {
                VStack(alignment: .leading, spacing: 6) {
                    TextField("Комментарий к аренде", text: $commentDraft, axis: .vertical)
                        .lineLimit(3, reservesSpace: true)
                        .padding(8)
                        .background(AppDesign.surfaceBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                    HStack(spacing: 10) {
                        Button("Сохранить") {
                            onSaveComment(clientId, rental.id, commentDraft)
                            isEditingComment = false
                        }
                        .buttonStyle(.borderedProminent)

                        Button("Отмена") {
                            commentDraft = rental.comment ?? ""
                            isEditingComment = false
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            if isEditingRental {
                VStack(alignment: .leading, spacing: 8) {
                    if bikes.isEmpty {
                        Text("Нет доступных велосипедов для выбора")
                            .font(.caption)
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Велосипед", selection: $selectedBikeId) {
                            ForEach(bikes) { bike in
                                Text("\(bike.bikeModel) • \(bike.weeklyRateRub) ₽/нед").tag(bike.bikeId)
                            }
                        }
                        .accessibilityIdentifier("rentalCard.bikePicker")
                    }

                    TextField("Дата начала (YYYY-MM-DD)", text: $periodStartDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("rentalCard.periodStartField")
                    TextField("Дата окончания (необязательно)", text: $periodEndDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("rentalCard.periodEndField")

                    if let rentalValidationError {
                        Text(rentalValidationError)
                            .font(.caption)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("rentalCard.validationError")
                    }

                    HStack(spacing: 10) {
                        Button("Сохранить") {
                            submitRentalUpdate()
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("rentalCard.saveEditButton")

                        Button("Отмена") {
                            resetRentalEditor()
                            isEditingRental = false
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.cancelEditButton")
                    }
                }
            }
        }
        .padding(10)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .confirmationDialog(
            "Удалить аренду?",
            isPresented: $isDeleteConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                onDeleteRental(clientId, rental.id)
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Действие нельзя отменить.")
        }
        .onAppear {
            if !bikes.contains(where: { $0.bikeId == selectedBikeId }) {
                selectedBikeId = bikes.first?.bikeId ?? ""
            }
        }
    }

    @ViewBuilder
    private func linkEditor(
        title: String,
        text: Binding<String>,
        onCancel: @escaping () -> Void,
        onSave: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppDesign.subtleText)
            TextField(title, text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(8)
                .background(AppDesign.surfaceBackground)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .foregroundStyle(AppDesign.titleText)

            HStack(spacing: 10) {
                Button("Сохранить", action: onSave)
                    .buttonStyle(.borderedProminent)
                Button("Отмена", action: onCancel)
                    .buttonStyle(.bordered)
            }
        }
    }

    private var avatar: some View {
        BikePhotoView(source: rental.bikeAvatarUrl) {
            Image(systemName: "bicycle")
                .resizable()
                .scaledToFit()
                .padding(9)
                .foregroundStyle(AppDesign.iconSoft)
        }
        .frame(width: 44, height: 44)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var periodText: String {
        if let end = rental.periodEnd, !end.isEmpty {
            return "\(rental.periodStart) - \(end)"
        }
        return "\(rental.periodStart) - н.в."
    }

    private func submitRentalUpdate() {
        rentalValidationError = nil
        let start = periodStartDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let end = periodEndDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedEnd = end.isEmpty ? nil : end

        guard !selectedBikeId.isEmpty else {
            rentalValidationError = "Выберите велосипед"
            return
        }
        guard isValidApiDate(start) else {
            rentalValidationError = "Дата начала должна быть в формате YYYY-MM-DD"
            return
        }
        if let normalizedEnd {
            guard isValidApiDate(normalizedEnd) else {
                rentalValidationError = "Дата окончания должна быть в формате YYYY-MM-DD"
                return
            }
            if normalizedEnd < start {
                rentalValidationError = "Дата окончания не может быть раньше даты начала"
                return
            }
        }

        onSaveRental(
            UpdateRentalPayload(
                clientId: clientId,
                rentalId: rental.id,
                bikeId: selectedBikeId,
                periodStart: start,
                periodEnd: normalizedEnd
            )
        )
        isEditingRental = false
    }

    private func isValidApiDate(_ value: String) -> Bool {
        guard value.count == 10 else { return false }
        return DateFormatter.apiDate.date(from: value) != nil
    }

    private func resetRentalEditor() {
        selectedBikeId = rental.bikeId
        periodStartDraft = rental.periodStart
        periodEndDraft = rental.periodEnd ?? ""
        rentalValidationError = nil
    }
}

private struct BikePhotoView<Placeholder: View>: View {
    let source: String?
    @ViewBuilder let placeholder: () -> Placeholder

    var body: some View {
        if let decodedImage {
            Image(uiImage: decodedImage)
                .resizable()
                .scaledToFill()
        } else if let remoteURL {
            AsyncImage(url: remoteURL) { phase in
                switch phase {
                case let .success(image):
                    image.resizable().scaledToFill()
                default:
                    placeholder()
                }
            }
        } else {
            placeholder()
        }
    }

    private var normalizedSource: String? {
        let value = source?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? nil : value
    }

    private var remoteURL: URL? {
        guard let normalizedSource, !normalizedSource.lowercased().hasPrefix("data:image") else {
            return nil
        }
        return URL(string: normalizedSource)
    }

    private var decodedImage: UIImage? {
        guard let normalizedSource, normalizedSource.lowercased().hasPrefix("data:image") else {
            return nil
        }
        guard
            let commaIndex = normalizedSource.firstIndex(of: ","),
            normalizedSource[..<commaIndex].lowercased().contains(";base64")
        else {
            return nil
        }
        let encoded = String(normalizedSource[normalizedSource.index(after: commaIndex)...])
        guard let data = Data(base64Encoded: encoded) else {
            return nil
        }
        return UIImage(data: data)
    }
}

private extension String {
    var trimmedToOptional: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}

private extension DateFormatter {
    static let apiDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
