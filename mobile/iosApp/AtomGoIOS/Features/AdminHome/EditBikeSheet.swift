import SwiftUI
import PhotosUI
import UIKit

struct EditBikeSheet: View {
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
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalPadding: CGFloat = 8

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
        GeometryReader { proxy in
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    topBar
                        .padding(.horizontal, horizontalPadding)
                        .padding(.top, 8)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 14) {
                            photoPickerCard(width: fieldWidth)

                            createBikeSectionTitle("Обязательные")

                            createBikeInput(
                                label: "Название/модель",
                                placeholder: "введите...",
                                text: $bikeModel,
                                accessibilityIdentifier: "editBike.modelField",
                                textInputAutocapitalization: .words
                            )
                            createBikeInput(
                                label: "Серийный номер / VIN",
                                placeholder: "введите...",
                                text: $frameSerialNumber,
                                accessibilityIdentifier: "editBike.frameSerialField"
                            )
                            createBikeInput(
                                label: "Серийный номер мотора",
                                placeholder: "введите...",
                                text: $motorSerialNumber,
                                accessibilityIdentifier: "editBike.motorSerialField"
                            )
                            createBikeInput(
                                label: "Недельная ставка W (₽)",
                                placeholder: "введите...",
                                text: $weeklyRateRub,
                                accessibilityIdentifier: "editBike.weeklyRateField",
                                keyboardType: .numberPad
                            )

                            createBikeSectionTitle("Опционально", topPadding: 4)

                            createBikeInput(
                                label: "Серийный номер АКБ 1",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber1,
                                accessibilityIdentifier: "editBike.battery1Field",
                                isDashed: true
                            )
                            createBikeInput(
                                label: "Серийный номер АКБ 2",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber2,
                                accessibilityIdentifier: "editBike.battery2Field",
                                isDashed: true
                            )
                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 14)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, horizontalPadding)
                    .scrollDismissesKeyboard(.interactively)
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
        .onChange(of: validationError) { newValue in
            presentToast(newValue)
        }
        .onChange(of: apiErrorMessage) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            createBikeTopButton(
                assetName: "back",
                assetSize: 14,
                isDark: false,
                accessibilityIdentifier: "editBike.cancelButton",
                action: onCancel
            )
            .disabled(isSaving)

            Spacer(minLength: 12)

            Text("Ред. велосипед")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer(minLength: 12)

            createBikeTopButton(
                assetName: "ok",
                assetSize: 16,
                isDark: true,
                accessibilityIdentifier: "editBike.submitButton",
                action: submit
            )
            .disabled(isSaving)
            .opacity(isSaving ? 0.45 : 1)
        }
        .frame(height: 47)
    }

    private func createBikeTopButton(
        imageName: String,
        isDark: Bool,
        showsProgress: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? ebonyClay : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                )
                .overlay(
                    Group {
                        if isDark && showsProgress && isSaving {
                            ProgressView()
                                .tint(Color.white)
                        } else {
                            Image(systemName: imageName)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(isDark ? Color.white : ebonyClay)
                        }
                    }
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func createBikeTopButton(
        assetName: String,
        assetSize: CGFloat,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? ebonyClay : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
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

    private var normalizedCurrentPhotoSource: String? {
        let source = (overridePhotoDataUrl ?? bike.photoUrl)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return source.isEmpty ? nil : source
    }

    private func photoPickerCard(width: CGFloat) -> some View {
        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 238 / 255, green: 240 / 255, blue: 243 / 255))

                if let selectedPhotoPreview {
                    selectedPhotoPreview
                        .resizable()
                        .scaledToFill()
                        .frame(width: width, height: 202)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay {
                            Color.black.opacity(0.2)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                } else if let source = normalizedCurrentPhotoSource {
                    BikePhotoView(source: source) {
                        PlaceholderBikeAvatar(cornerRadius: 14)
                    }
                    .frame(width: width, height: 202)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        Color.black.opacity(0.2)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                }

                VStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(ebonyClay, lineWidth: 1)
                        )
                        .overlay(
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 24, weight: .regular))
                                .foregroundStyle(ebonyClay)
                        )
                        .frame(width: 58, height: 58)

                    Text("Заменить фото")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(ebonyClay)

                    Text("Нажмите, чтобы выбрать из галереи")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(paleSky)
                }
            }
            .frame(width: width, height: 202)
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(ebonyClay, style: StrokeStyle(lineWidth: 1, dash: [3, 2.5]))
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("editBike.photoPicker")
    }

    private func createBikeSectionTitle(_ text: String, topPadding: CGFloat = 0) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .padding(.top, topPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func createBikeInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        isDashed: Bool = false,
        keyboardType: UIKeyboardType = .default,
        textInputAutocapitalization: TextInputAutocapitalization = .never
    ) -> some View {
        AtomGoInputField(
            label: label,
            placeholder: placeholder,
            text: text,
            isDashed: isDashed,
            keyboardType: keyboardType,
            textInputAutocapitalization: textInputAutocapitalization,
            accessibilityIdentifier: accessibilityIdentifier
        )
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
