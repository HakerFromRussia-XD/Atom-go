import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: LoginViewModel
    @StateObject private var keyboardState = KeyboardState()
    @State private var isPasswordVisible = false
    @FocusState private var focusedField: LoginInputField?
    private let showQuickFillButtons = true

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

                Text("Forget Password ?")
                    .font(AppDesign.poppinsMedium(size: 14 * textScale))
                    .foregroundStyle(AppDesign.subtleText)
                .frame(width: 343 * xScale, alignment: .trailing)
                .offset(x: 35 * xScale, y: 642 * yScale)

                if showQuickFillButtons {
                    HStack(spacing: 10 * xScale) {
                        quickFillButton(
                            title: "Клиент",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillClient",
                            action: viewModel.fillClientCredentials
                        )
                        quickFillButton(
                            title: "Админ1",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillAdmin",
                            action: viewModel.fillAdminCredentials
                        )
                        quickFillButton(
                            title: "Админ2",
                            xScale: xScale,
                            yScale: yScale,
                            textScale: textScale,
                            accessibilityIdentifier: "login.quickFillAdminIp",
                            action: viewModel.fillAdminIpCredentials
                        )
                    }
                    .frame(width: fieldWidth)
                    .offset(x: 35 * xScale, y: 760 * yScale)
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

                if viewModel.statusText != LoginViewModel.waitingStatusText {
                    Text(viewModel.statusText)
                        .font(AppDesign.poppinsMedium(size: 12 * textScale))
                        .foregroundStyle(statusColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(width: 343 * xScale, alignment: .leading)
                        .accessibilityIdentifier("login.statusText")
                        .offset(x: 35 * xScale, y: 760 * yScale)
                }
            }
            .offset(y: -keyboardLift)
            .animation(.easeOut(duration: 0.2), value: keyboardTop)
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
                        .renderingMode(.original)
                        .scaledToFit()
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
                .font(AppDesign.poppinsMedium(size: 13 * textScale))
                .foregroundStyle(AppDesign.titleText)
                .frame(maxWidth: .infinity)
                .frame(height: 30 * yScale)
                .background(AppDesign.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12 * textScale, style: .continuous))
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

private extension ContentView {
    var statusColor: Color {
        switch viewModel.statusKind {
        case .error:
            return AppDesign.danger
        case .success:
            return AppDesign.success
        case .info, .idle:
            return .primary
        }
    }
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
