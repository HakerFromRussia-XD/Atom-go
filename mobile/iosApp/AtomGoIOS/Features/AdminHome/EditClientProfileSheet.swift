import SwiftUI
import PhotosUI
import UIKit

struct EditClientProfilePhone: Identifiable {
    let id: String
    var label: String
    var number: String
}

struct EditClientProfileSheet: View {
    let details: AdminClientDetailsResponse
    let isSaving: Bool
    let onCancel: () -> Void
    let onSave: (UpdateClientProfilePayload) -> Void

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)

    @State private var fullName: String
    @State private var address: String
    @State private var passportData: String
    @State private var phones: [EditClientProfilePhone]
    @State private var isCommentVisible = false
    @State private var comment = ""
    @State private var validationError: String?
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

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
        let existingComment = details.comment?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        _comment = State(initialValue: existingComment)
        _isCommentVisible = State(initialValue: !existingComment.isEmpty)
    }

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding: CGFloat = 8
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack(alignment: .top) {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    editClientTopBar(horizontalPadding: horizontalPadding)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 18) {
                            editSectionTitle("Профиль")

                            editClientInput(
                                label: "ФИО",
                                placeholder: "введите...",
                                text: $fullName,
                                accessibilityIdentifier: "editClient.fullNameField"
                            )
                            editClientInput(
                                label: "Адрес",
                                placeholder: "введите...",
                                text: $address,
                                accessibilityIdentifier: "editClient.addressField"
                            )
                            editClientInput(
                                label: "Паспортные данные",
                                placeholder: "введите...",
                                text: $passportData,
                                accessibilityIdentifier: "editClient.passportField"
                            )

                            editSectionTitle("Телефоны")
                                .padding(.top, 6)

                            ForEach(Array(phones.indices), id: \.self) { index in
                                editClientInput(
                                    label: "Подпись",
                                    placeholder: "введите...",
                                    text: $phones[index].label,
                                    accessibilityIdentifier: index == 0
                                        ? "editClient.phoneLabel1Field"
                                        : "editClient.phoneLabelField.\(index)",
                                    valueWeight: .bold
                                )
                                editClientInput(
                                    label: "Телефон",
                                    placeholder: "+7 …",
                                    text: $phones[index].number,
                                    accessibilityIdentifier: index == 0
                                        ? "editClient.phoneNumber1Field"
                                        : "editClient.phoneNumberField.\(index)",
                                    keyboardType: .phonePad
                                )
                            }

                            editDashedActionButton(
                                title: "+ Добавить телефон",
                                accessibilityIdentifier: "editClient.addPhoneButton"
                            ) {
                                phones.append(
                                    EditClientProfilePhone(
                                        id: UUID().uuidString,
                                        label: "",
                                        number: ""
                                    )
                                )
                            }

                            if isCommentVisible {
                                editClientInput(
                                    label: "Комментарий",
                                    placeholder: "введите...",
                                    text: $comment,
                                    accessibilityIdentifier: "editClient.commentField"
                                )
                            }

                            editDashedActionButton(
                                title: "+ Добавить комментарий",
                                accessibilityIdentifier: "editClient.addCommentButton"
                            ) {
                                isCommentVisible = true
                            }

                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 16)
                        .padding(.bottom, 24)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .padding(.horizontal, horizontalPadding)
                }
            }
        }
        .onChange(of: validationError) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private func editClientTopBar(horizontalPadding: CGFloat) -> some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image("back")
                    .renderingMode(.original)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 14, height: 14)
                    .frame(width: 47, height: 47)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(ebonyClay, lineWidth: 1.5)
                    }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("editClient.cancelButton")

            Spacer()

            Text("Редактировать")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer()

            Button {
                submit()
            } label: {
                Group {
                    if isSaving {
                        ProgressView()
                            .tint(Color.white)
                    } else {
                        Image("ok")
                            .renderingMode(.original)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 16, height: 16)
                    }
                }
                .frame(width: 47, height: 47)
                .background(ebonyClay)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1.5)
                }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("editClient.submitButton")
        }
        .padding(.horizontal, horizontalPadding)
        .frame(height: 62)
    }

    private func editSectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("editClient.section.\(title)")
    }

    private func editClientInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        valueWeight: Font.Weight = .regular,
        keyboardType: UIKeyboardType = .default
    ) -> some View {
        AtomGoInputField(
            label: label,
            placeholder: placeholder,
            text: text,
            keyboardType: keyboardType,
            textInputAutocapitalization: label == "ФИО" ? .words : .sentences,
            accessibilityIdentifier: accessibilityIdentifier,
            valueWeight: valueWeight
        )
    }

    private func editDashedActionButton(
        title: String,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .tracking(0.28)
                .foregroundStyle(ebonyClay)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(grayChateau, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
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

        let normalizedComment = isCommentVisible ? comment.trimmedToOptional : nil

        let payload = UpdateClientProfilePayload(
            fullName: normalizedFullName,
            address: normalizedAddress,
            passportData: normalizedPassport,
            phones: normalizedPhones,
            comment: normalizedComment
        )
        onSave(payload)
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
