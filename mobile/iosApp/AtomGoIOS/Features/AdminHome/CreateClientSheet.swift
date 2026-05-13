import SwiftUI
import PhotosUI
import UIKit

struct CreateClientSheet: View {
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload) -> Void

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)

    @State private var fullName = ""
    @State private var address = ""
    @State private var passportData = ""
    @State private var phones: [CreateClientPhoneDraft] = [
        CreateClientPhoneDraft(label: "Рабочий (TG)", number: "")
    ]
    @State private var isCommentVisible = false
    @State private var comment = ""
    @State private var validationError: String?

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding: CGFloat = 8
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack(alignment: .top) {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    createClientTopBar(horizontalPadding: horizontalPadding)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 18) {
                            sectionTitle("Профиль")

                            createClientInput(
                                label: "ФИО",
                                placeholder: "введите...",
                                text: $fullName,
                                accessibilityIdentifier: "createClient.fullNameField"
                            )
                            createClientInput(
                                label: "Адрес",
                                placeholder: "введите...",
                                text: $address,
                                accessibilityIdentifier: "createClient.addressField"
                            )
                            createClientInput(
                                label: "Паспортные данные",
                                placeholder: "введите...",
                                text: $passportData,
                                accessibilityIdentifier: "createClient.passportField"
                            )

                            sectionTitle("Телефоны")
                                .padding(.top, 6)

                            ForEach(Array(phones.indices), id: \.self) { index in
                                createClientInput(
                                    label: "Подпись",
                                    placeholder: "введите...",
                                    text: $phones[index].label,
                                    accessibilityIdentifier: index == 0
                                        ? "createClient.phoneLabel1Field"
                                        : "createClient.phoneLabelField.\(index)",
                                    valueWeight: .bold
                                )
                                createClientInput(
                                    label: "Телефон",
                                    placeholder: "+7 …",
                                    text: $phones[index].number,
                                    accessibilityIdentifier: index == 0
                                        ? "createClient.phoneNumber1Field"
                                        : "createClient.phoneNumberField.\(index)",
                                    keyboardType: .phonePad
                                )
                            }

                            dashedActionButton(
                                title: "+ Добавить телефон",
                                accessibilityIdentifier: "createClient.addPhoneButton"
                            ) {
                                phones.append(CreateClientPhoneDraft(label: "", number: ""))
                            }

                            if isCommentVisible {
                                createClientInput(
                                    label: "Комментарий",
                                    placeholder: "введите...",
                                    text: $comment,
                                    accessibilityIdentifier: "createClient.commentField"
                                )
                            }

                            dashedActionButton(
                                title: "+ Добавить комментарий",
                                accessibilityIdentifier: "createClient.addCommentButton"
                            ) {
                                isCommentVisible = true
                            }

                            createClientErrorBlock
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

    private func createClientTopBar(horizontalPadding: CGFloat) -> some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(ebonyClay)
                    .frame(width: 47, height: 47)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(ebonyClay, lineWidth: 1)
                    }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("createClient.cancelButton")

            Spacer()

            Text("Новый клиент")
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
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(Color.white)
                    }
                }
                .frame(width: 47, height: 47)
                .background(ebonyClay)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("createClient.submitButton")
        }
        .padding(.horizontal, horizontalPadding)
        .frame(height: 62)
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("createClient.section.\(title)")
    }

    private func createClientInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        valueWeight: Font.Weight = .regular,
        keyboardType: UIKeyboardType = .default
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)

            TextField("", text: text, prompt: Text(placeholder).foregroundColor(ghost))
                .font(.system(size: 13, weight: valueWeight))
                .foregroundStyle(ebonyClay)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(label == "ФИО" ? .words : .sentences)
                .autocorrectionDisabled()
                .accessibilityIdentifier(accessibilityIdentifier)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(ebonyClay, lineWidth: 1)
        }
    }

    private func dashedActionButton(
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
                        .stroke(grayChateau, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    @ViewBuilder
    private var createClientErrorBlock: some View {
        if validationError != nil || (apiErrorMessage?.isEmpty == false) {
            VStack(alignment: .leading, spacing: 8) {
                if let validationError {
                    Text(validationError)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(AppDesign.danger)
                        .accessibilityIdentifier("createClient.validationError")
                }

                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Text(apiErrorMessage)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(AppDesign.danger)
                        .accessibilityIdentifier("createClient.apiError")
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

}

