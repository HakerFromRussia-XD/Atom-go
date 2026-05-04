import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: LoginViewModel
    @StateObject private var keyboardState = KeyboardState()

    var body: some View {
        GeometryReader { geometry in
            let screenBounds = UIScreen.main.bounds
            let layoutWidth = max(geometry.size.width, screenBounds.width)
            let layoutHeight = max(geometry.size.height, screenBounds.height)
            let safeTop = geometry.safeAreaInsets.top
            let xScale = layoutWidth / 600
            let yScale = layoutHeight / 1260
            let textScale = min(xScale, yScale)
            let fieldWidth = 481 * xScale
            let loginButtonBottom = safeTop + (730 + 91) * yScale
            let keyboardGap: CGFloat = 16
            let keyboardTop = min(keyboardState.topY, layoutHeight)
            let keyboardLift = max(0, loginButtonBottom + keyboardGap - keyboardTop)

            ZStack(alignment: .topLeading) {
                AppDesign.pageBackground.ignoresSafeArea()

                RoundedRectangle(cornerRadius: 42 * textScale, style: .continuous)
                    .fill(AppDesign.surfaceBackground)
                    .frame(width: 588 * xScale, height: 1244 * yScale)
                    .offset(x: 6 * xScale, y: 8 * yScale)

                Button(action: {}) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 30 * textScale, weight: .semibold))
                        .foregroundStyle(AppDesign.accent)
                        .frame(width: 63 * xScale, height: 62 * yScale)
                        .background(
                            RoundedRectangle(cornerRadius: 18 * textScale, style: .continuous)
                                .fill(AppDesign.cardBackground)
                        )
                }
                .buttonStyle(.plain)
                .offset(x: 54 * xScale, y: 69 * yScale)

                Text("Login Your\nAccount")
                    .font(.system(size: 78 * textScale, weight: .bold, design: .rounded))
                    .foregroundStyle(AppDesign.titleText)
                    .lineSpacing(-9 * textScale)
                    .offset(x: 54 * xScale, y: 245 * yScale)

                loginField(
                    xScale: xScale,
                    yScale: yScale,
                    textScale: textScale,
                    iconName: "User Icon",
                    placeholder: "Enter Your Email",
                    text: $viewModel.login,
                    accessibilityIdentifier: "login.loginField"
                )
                .frame(width: fieldWidth, height: 92 * yScale)
                .offset(x: 54 * xScale, y: 436 * yScale)

                passwordField(xScale: xScale, yScale: yScale, textScale: textScale)
                    .frame(width: fieldWidth, height: 92 * yScale)
                    .offset(x: 54 * xScale, y: 555 * yScale)

                Text("Forget Password ?")
                    .font(.system(size: 16 * textScale, weight: .semibold))
                    .foregroundStyle(AppDesign.subtleText)
                    .frame(width: fieldWidth, alignment: .trailing)
                    .offset(x: 54 * xScale, y: 673 * yScale)

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
                        title: "Админ",
                        xScale: xScale,
                        yScale: yScale,
                        textScale: textScale,
                        accessibilityIdentifier: "login.quickFillAdmin",
                        action: viewModel.fillAdminCredentials
                    )
                }
                .frame(width: fieldWidth)
                .offset(x: 54 * xScale, y: 696 * yScale)

                Button(action: viewModel.signIn) {
                    Text(viewModel.isLoading ? "Logging in..." : "Login")
                        .font(.system(size: 22 * textScale, weight: .semibold, design: .rounded))
                        .frame(maxWidth: .infinity)
                        .frame(height: 91 * yScale)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.white)
                .background(AppDesign.accent)
                .clipShape(RoundedRectangle(cornerRadius: 22 * textScale, style: .continuous))
                .disabled(viewModel.isLoading)
                .accessibilityIdentifier("login.submitButton")
                .frame(width: fieldWidth)
                .offset(x: 54 * xScale, y: 730 * yScale)

                if viewModel.statusText != LoginViewModel.waitingStatusText {
                    Text(viewModel.statusText)
                        .font(.system(size: 14 * textScale, weight: .regular))
                        .foregroundStyle(statusColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(width: fieldWidth, alignment: .leading)
                        .accessibilityIdentifier("login.statusText")
                        .offset(x: 54 * xScale, y: 840 * yScale)
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
            RoundedRectangle(cornerRadius: 18 * textScale, style: .continuous)
                .fill(AppDesign.cardBackground)

            HStack(spacing: 26 * xScale) {
                Image(iconName)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 30 * textScale)

                TextField(
                    "",
                    text: text,
                    prompt: Text(placeholder)
                        .foregroundColor(AppDesign.iconSoft)
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .font(.system(size: 20 * textScale, weight: .semibold))
                .foregroundStyle(AppDesign.titleText)
                .accessibilityIdentifier(accessibilityIdentifier)

                Spacer(minLength: 0)
            }
            .padding(.leading, 31 * xScale)
            .padding(.trailing, 24 * xScale)
        }
    }

    private func passwordField(xScale: CGFloat, yScale: CGFloat, textScale: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18 * textScale, style: .continuous)
                .fill(AppDesign.cardBackground)

            HStack(spacing: 26 * xScale) {
                Image("Lock Icon")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 30 * textScale)

                SecureField(
                    "",
                    text: $viewModel.password,
                    prompt: Text("Password")
                        .foregroundColor(AppDesign.iconSoft)
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .font(.system(size: 20 * textScale, weight: .semibold))
                .foregroundStyle(AppDesign.titleText)
                .accessibilityIdentifier("login.passwordField")

                Spacer(minLength: 0)

                Image("Eye Off Icon")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 30 * textScale)
            }
            .padding(.leading, 31 * xScale)
            .padding(.trailing, 24 * xScale)
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
                .font(.system(size: 14 * textScale, weight: .semibold, design: .rounded))
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
