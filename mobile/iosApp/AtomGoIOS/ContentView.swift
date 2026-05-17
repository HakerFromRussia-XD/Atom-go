import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: LoginViewModel
    @StateObject private var keyboardState = KeyboardState()
    @State private var isPasswordVisible = false
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?
    @FocusState private var focusedField: LoginInputField?
    private let showQuickFillButtons = false

    var body: some View {
        GeometryReader { geometry in
            let screenBounds = UIScreen.main.bounds
            let layoutWidth = max(geometry.size.width, screenBounds.width)
            let layoutHeight = max(geometry.size.height, screenBounds.height)
            let safeTop = geometry.safeAreaInsets.top
            let xScale = layoutWidth / 414
            let yScale = layoutHeight / 896
            let textScale = min(xScale, yScale)
            let fieldWidth = 481 * xScale
            let loginButtonBottom = safeTop + (687 + 63) * yScale
            let keyboardGap: CGFloat = 16
            let keyboardTop = min(keyboardState.topY, layoutHeight)
            let keyboardLift = max(0, loginButtonBottom + keyboardGap - keyboardTop)

            ZStack(alignment: .topLeading) {
                AppDesign.pageBackground.ignoresSafeArea()

                Image("icon")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 154 * xScale, height: 184 * yScale)
                    .offset(x: 130 * xScale, y: 121 * yScale)

                VStack(spacing: 0) {
                    Text(" Welcome to ")
                        .font(AppDesign.urbanistBold(size: 40 * textScale))
                        .foregroundStyle(Color(red: 0.13, green: 0.13, blue: 0.13))
                        .lineSpacing(0)
                    Text("AtomGo")
                        .font(AppDesign.urbanistBold(size: 40 * textScale))
                        .foregroundStyle(Color(red: 0.13, green: 0.13, blue: 0.13))
                        .lineSpacing(0)
                }
                .frame(width: 382 * xScale)
                .offset(x: 16 * xScale, y: 328 * yScale)

                loginField(
                    xScale: xScale,
                    yScale: yScale,
                    textScale: textScale,
                    iconName: "User Icon",
                    placeholder: "Enter Your Email",
                    text: $viewModel.login,
                    accessibilityIdentifier: "login.loginField"
                )
                .frame(width: 343 * xScale, height: 64 * yScale)
                .offset(x: 35 * xScale, y: 477 * yScale)

                passwordField(xScale: xScale, yScale: yScale, textScale: textScale)
                .frame(width: 343 * xScale, height: 64 * yScale)
                .offset(x: 35 * xScale, y: 562 * yScale)

                HStack {
                    Button {
                        viewModel.setRememberMe(!viewModel.rememberMe)
                    } label: {
                        HStack(spacing: 8 * xScale) {
                            Image(systemName: viewModel.rememberMe ? "checkmark.square.fill" : "square")
                                .font(.system(size: 17 * textScale, weight: .semibold))
                                .foregroundStyle(AppDesign.accent)
                            Text("Запомнить меня")
                                .font(AppDesign.poppinsMedium(size: 13 * textScale))
                                .foregroundStyle(AppDesign.subtleText)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("login.rememberMeToggle")

                    Spacer(minLength: 0)

                    Text("Forget Password ?")
                        .font(AppDesign.poppinsMedium(size: 14 * textScale))
                        .foregroundStyle(AppDesign.subtleText)
                }
                .frame(width: 343 * xScale, alignment: .leading)
                .offset(x: 35 * xScale, y: 642 * yScale)

                if showQuickFillButtons {
                    HStack(spacing: 6 * xScale) {
                        quickFillButton(
                            title: "к 1",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillClientSelfEmployed",
                            action: viewModel.fillClientSelfEmployedCredentials
                        )
                        quickFillButton(
                            title: "к 2",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillClientIp",
                            action: viewModel.fillClientIpCredentials
                        )
                        quickFillButton(
                            title: "а 1",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillAdminSelfEmployed",
                            action: viewModel.fillAdminCredentials
                        )
                        quickFillButton(
                            title: "а 2",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillAdminIp",
                            action: viewModel.fillAdminIpCredentials
                        )
                    }
                    .frame(width: 343 * xScale)
                    .offset(x: 35 * xScale, y: 758 * yScale)
                }

                Button(action: viewModel.signIn) {
                    Text(viewModel.isLoading ? "Getting started..." : "Get Started")
                        .font(AppDesign.poppinsMedium(size: 15 * textScale))
                        .tracking(0.45 * textScale)
                        .frame(width: 343 * xScale, height: 63 * yScale)
                        .contentShape(RoundedRectangle(cornerRadius: 16 * textScale, style: .continuous))
                }
                .buttonStyle(.plain)
                .foregroundStyle(.white)
                .background(AppDesign.accent)
                .clipShape(RoundedRectangle(cornerRadius: 16 * textScale, style: .continuous))
                .disabled(viewModel.isLoading)
                .accessibilityIdentifier("login.submitButton")
                .offset(x: 35 * xScale, y: 687 * yScale)

                Text(viewModel.statusText)
                    .font(.caption2)
                    .foregroundStyle(.clear)
                    .frame(width: 1, height: 1)
                    .accessibilityIdentifier("login.statusText")
                    .accessibilityValue(viewModel.statusText)
            }
            .offset(y: -keyboardLift)
            .animation(.easeOut(duration: 0.2), value: keyboardTop)
            .onChange(of: viewModel.statusText) { newValue in
                presentLoginToastIfNeeded(newValue)
            }
            .appToast(message: $toastMessage, bottomPadding: 86)
        }
    }

    private func presentLoginToastIfNeeded(_ statusText: String) {
        let normalized = statusText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty, normalized != LoginViewModel.waitingStatusText else {
            return
        }
        let message: String
        if let statusPrefixRange = normalized.range(of: "Статус: ") {
            message = String(normalized[statusPrefixRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            message = normalized
        }
        guard !message.isEmpty else { return }
        toastDismissTask?.cancel()
        toastMessage = message
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            toastMessage = nil
        }
    }

    private func loginField(
        xScale: CGFloat,
        yScale: CGFloat,
        textScale: CGFloat,
        iconName: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String
    ) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous)
                .fill(AppDesign.cardBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous)
                        .stroke(.black, lineWidth: 1 * textScale)
                )

            HStack(spacing: 20 * xScale) {
                Image(iconName)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 20 * textScale)

                TextField(
                    "",
                    text: text,
                    prompt: Text(placeholder)
                        .foregroundColor(AppDesign.iconSoft)
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                    .font(AppDesign.poppinsMedium(size: 24 * textScale / 1.7))
                    .foregroundStyle(AppDesign.titleText)
                .focused($focusedField, equals: .login)
                .accessibilityIdentifier(accessibilityIdentifier)

                Spacer(minLength: 0)
            }
            .padding(.leading, 22 * xScale)
            .padding(.trailing, 16 * xScale)
        }
        .contentShape(RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous))
        .onTapGesture {
            focusedField = .login
        }
    }

    private func passwordField(xScale: CGFloat, yScale: CGFloat, textScale: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous)
                .fill(AppDesign.cardBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous)
                        .stroke(.black, lineWidth: 1 * textScale)
                )

            HStack(spacing: 20 * xScale) {
                Image("Lock Icon")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 20 * textScale)

                passwordInput(textScale: textScale)

                Spacer(minLength: 0)

                Button {
                    isPasswordVisible.toggle()
                    focusedField = .password
                } label: {
                    Image(isPasswordVisible ? "View_light" : "View_hide_light")
                        .resizable()
                        .renderingMode(.template)
                        .scaledToFit()
                        .foregroundStyle(.black)
                        .frame(width: 20 * textScale, height: 20 * textScale)
                        .frame(width: 40 * xScale, height: 64 * yScale)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("login.passwordVisibilityButton")
            }
            .padding(.leading, 22 * xScale)
            .padding(.trailing, 18 * xScale)
        }
        .contentShape(RoundedRectangle(cornerRadius: 12.84 * textScale, style: .continuous))
        .onTapGesture {
            focusedField = .password
        }
    }

    @ViewBuilder
    private func passwordInput(textScale: CGFloat) -> some View {
        if isPasswordVisible {
            TextField(
                "",
                text: $viewModel.password,
                prompt: Text("Password")
                    .foregroundColor(AppDesign.iconSoft)
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .font(AppDesign.poppinsMedium(size: 14 * textScale))
            .foregroundStyle(AppDesign.titleText)
            .focused($focusedField, equals: .password)
            .accessibilityIdentifier("login.passwordField")
        } else {
            SecureField(
                "",
                text: $viewModel.password,
                prompt: Text("Password")
                    .foregroundColor(AppDesign.iconSoft)
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .font(AppDesign.poppinsMedium(size: 14 * textScale))
            .foregroundStyle(AppDesign.titleText)
            .focused($focusedField, equals: .password)
            .accessibilityIdentifier("login.passwordField")
        }
    }

    private func quickFillButton(
        title: String,
        xScale: CGFloat,
        yScale: CGFloat,
        textScale: CGFloat,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(AppDesign.poppinsMedium(size: 12 * textScale))
                .foregroundStyle(AppDesign.titleText)
                .frame(width: 80 * xScale, height: 24 * yScale)
                .background(AppDesign.cardBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 8 * textScale, style: .continuous)
                        .stroke(AppDesign.iconSoft.opacity(0.35), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8 * textScale, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }
}

private enum LoginInputField: Hashable {
    case login
    case password
}

#Preview {
    ContentView(viewModel: LoginViewModel(apiService: BackendService()))
}

private final class KeyboardState: ObservableObject {
    private static let hiddenY = CGFloat.greatestFiniteMagnitude
    private static let hiddenThreshold = CGFloat.greatestFiniteMagnitude / 2

    @Published var topY: CGFloat = .greatestFiniteMagnitude

    private var observers: [NSObjectProtocol] = []

    init() {
        let names: [Notification.Name] = [
            UIResponder.keyboardWillShowNotification,
            UIResponder.keyboardDidShowNotification,
            UIResponder.keyboardWillChangeFrameNotification,
            UIResponder.keyboardDidChangeFrameNotification,
            UIResponder.keyboardWillHideNotification,
            UIResponder.keyboardDidHideNotification
        ]

        observers = names.map { name in
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] notification in
                self?.handle(notification: notification)
            }
        }
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    private func handle(notification: Notification) {
        guard
            let userInfo = notification.userInfo,
            let keyboardFrame = userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect
        else {
            return
        }

        let nextTopY: CGFloat
        if notification.name == UIResponder.keyboardWillHideNotification || notification.name == UIResponder.keyboardDidHideNotification {
            nextTopY = Self.hiddenY
        } else {
            nextTopY = keyboardFrame.minY
        }

        if shouldPublishTopYChange(from: topY, to: nextTopY) {
            topY = nextTopY
        }
    }

    private func shouldPublishTopYChange(from oldValue: CGFloat, to newValue: CGFloat) -> Bool {
        let oldHidden = oldValue > Self.hiddenThreshold
        let newHidden = newValue > Self.hiddenThreshold
        if oldHidden && newHidden {
            return false
        }
        if oldHidden != newHidden {
            return true
        }
        return abs(oldValue - newValue) > 0.5
    }
}
