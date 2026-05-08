import SwiftUI
import SafariServices

struct ClientHomeView: View {
    @ObservedObject var viewModel: ClientHomeViewModel
    let onLogout: () -> Void

    @Environment(\.scenePhase) private var scenePhase

    @State private var isTariffSheetPresented = false
    @State private var selectedPaymentType: ClientPaymentType = .week
    @State private var paymentSafariUrl: URL?
    @State private var activePaymentId: String?
    @State private var isSafariPresented = false
    @State private var isReceiptEmailDialogPresented = false
    @State private var pendingPaymentType: ClientPaymentType?
    @State private var receiptEmail = ""

    private let baseWidth: CGFloat = 414
    private let baseHeight: CGFloat = 896

    private var shouldAutoOpenPaymentSafari: Bool {
        !ProcessInfo.processInfo.arguments.contains("-ATOMGO_DISABLE_PAYMENT_SAFARI_AUTOPEN")
    }

    private var tariffTypes: [ClientPaymentType] {
        [.day, .week, .twoWeeks, .month]
    }

    var body: some View {
        GeometryReader { geometry in
            let safeTop = geometry.safeAreaInsets.top
            let scale = min(geometry.size.width / baseWidth, geometry.size.height / baseHeight)

            ZStack(alignment: .bottom) {
                AppDesign.pageBackground.ignoresSafeArea()

                switch viewModel.state {
                case .idle, .loading:
                    loadingView(scale: scale, safeTop: safeTop)

                case let .failed(message):
                    failedView(message: message, scale: scale, safeTop: safeTop)

                case let .loaded(dashboard):
                    loadedView(dashboard: dashboard, scale: scale, safeTop: safeTop, totalHeight: geometry.size.height)
                }
            }
        }
        .task {
            if case .idle = viewModel.state {
                viewModel.load()
            }
        }
        .onReceive(viewModel.$paymentResult.compactMap { $0 }) { payment in
            guard shouldAutoOpenPaymentSafari,
                  payment.status != "succeeded",
                  payment.status != "canceled",
                  payment.status != "failed",
                  !(payment.taxMode == "individual_entrepreneur" && payment.fiscalizationStatus == "fiscalization_not_configured"),
                  let url = URL(string: payment.confirmationUrl),
                  activePaymentId != payment.paymentId
            else { return }

            activePaymentId = payment.paymentId
            paymentSafariUrl = url
            isSafariPresented = true
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                viewModel.load()
                if let activePaymentId {
                    viewModel.refreshPaymentStatus(paymentId: activePaymentId)
                }
            }
        }
        .sheet(
            isPresented: $isSafariPresented,
            onDismiss: {
                if let activePaymentId {
                    viewModel.refreshPaymentStatus(paymentId: activePaymentId)
                }
            }
        ) {
            if let paymentSafariUrl {
                SafariPaymentView(url: paymentSafariUrl)
            }
        }
        .alert("Email для чека", isPresented: $isReceiptEmailDialogPresented) {
            TextField("email@example.com", text: $receiptEmail)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier("client.receiptEmailField")

            Button("Продолжить") {
                if let pendingPaymentType {
                    viewModel.createPayment(type: pendingPaymentType, receiptEmail: receiptEmail)
                }
                pendingPaymentType = nil
                receiptEmail = ""
            }
            .accessibilityIdentifier("client.receiptEmailSubmitButton")

            Button("Отмена", role: .cancel) {
                pendingPaymentType = nil
                receiptEmail = ""
            }
        } message: {
            Text("Укажите email, куда ЮKassa отправит чек.")
        }
    }

    private func loadingView(scale: CGFloat, safeTop: CGFloat) -> some View {
        VStack(spacing: 18 * scale) {
            topBar(scale: scale, safeTop: safeTop)

            Spacer(minLength: 0)

            ProgressView("Загружаем аренду...")
                .font(.system(size: 14 * scale, weight: .semibold))
                .foregroundStyle(ClientColors.subtleText)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 23 * scale)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func failedView(message: String, scale: CGFloat, safeTop: CGFloat) -> some View {
        VStack(spacing: 18 * scale) {
            topBar(scale: scale, safeTop: safeTop)

            Spacer(minLength: 0)

            VStack(spacing: 10 * scale) {
                Text("Не удалось загрузить данные")
                    .font(.system(size: 18 * scale, weight: .bold))
                    .foregroundStyle(ClientColors.mainText)

                Text(message)
                    .font(.system(size: 13 * scale, weight: .medium))
                    .foregroundStyle(ClientColors.subtleText)
                    .multilineTextAlignment(.center)

                Button("Повторить") {
                    viewModel.load()
                }
                .buttonStyle(.borderedProminent)
                .tint(ClientColors.primaryButton)
            }
            .padding(20 * scale)
            .frame(maxWidth: .infinity)
            .background(ClientColors.card)
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 23 * scale)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func loadedView(
        dashboard: ClientDashboardResponse,
        scale: CGFloat,
        safeTop: CGFloat,
        totalHeight: CGFloat
    ) -> some View {
        let bottomPadding = isTariffSheetPresented ? min(390 * scale, totalHeight * 0.48) + 16 * scale : 0

        return VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    topBar(scale: scale, safeTop: safeTop)

                    bikeCard(dashboard: dashboard, scale: scale)
                        .padding(.top, 16 * scale)

                    quickPaymentSection(dashboard: dashboard, scale: scale)
                        .padding(.top, 22 * scale)

                    if viewModel.isRefreshingPaymentStatus {
                        HStack(spacing: 8 * scale) {
                            ProgressView()
                                .controlSize(.small)
                            Text("Проверяем статус платежа...")
                                .font(.system(size: 12 * scale, weight: .medium))
                                .foregroundStyle(ClientColors.subtleText)
                        }
                        .padding(.top, 14 * scale)
                    }

                    if let paymentErrorMessage = viewModel.paymentErrorMessage {
                        Text(paymentErrorMessage)
                            .font(.system(size: 12 * scale, weight: .medium))
                            .foregroundStyle(ClientColors.debt)
                            .padding(.top, 14 * scale)
                            .accessibilityIdentifier("client.paymentErrorMessage")
                    }

                    if let paymentStatusMessage = viewModel.paymentStatusMessage {
                        Text(paymentStatusMessage)
                            .font(.system(size: 12 * scale, weight: .medium))
                            .foregroundStyle(ClientColors.success)
                            .padding(.top, 12 * scale)
                            .accessibilityIdentifier("client.paymentStatusMessage")
                    }

                    if let payment = viewModel.paymentResult {
                        Text(
                            [
                                payment.confirmationUrl,
                                payment.taxMode ?? "",
                                payment.fiscalizationStatus ?? ""
                            ].joined(separator: "|")
                        )
                        .font(.caption2)
                        .foregroundStyle(.clear)
                        .frame(width: 1, height: 1)
                        .accessibilityIdentifier("client.paymentMetadata")
                        .accessibilityValue(
                            [
                                payment.confirmationUrl,
                                payment.taxMode ?? "",
                                payment.fiscalizationStatus ?? ""
                            ].joined(separator: "|")
                        )
                    }
                }
                .padding(.horizontal, 23 * scale)
                .padding(.bottom, 36 * scale + bottomPadding)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .overlay(alignment: .bottom) {
            if isTariffSheetPresented {
                tariffSheet(dashboard: dashboard, scale: scale)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(10)
            }
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.92), value: isTariffSheetPresented)
    }

    private func topBar(scale: CGFloat, safeTop: CGFloat) -> some View {
        HStack {
            Button {
                onLogout()
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                                .stroke(ClientColors.mainText, lineWidth: 1)
                        )

                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 16 * scale, weight: .medium))
                        .foregroundStyle(ClientColors.mainText)
                }
                .frame(width: 47 * scale, height: 47 * scale)
            }
            .buttonStyle(.plain)

            Spacer(minLength: 0)

            Text("Моя аренда")
                .font(.system(size: 31 * scale / 1.7, weight: .bold))
                .foregroundStyle(ClientColors.mainText)

            Spacer(minLength: 0)

            Color.clear
                .frame(width: 47 * scale, height: 47 * scale)
        }
        .padding(.top, 10 * scale)
    }

    private func bikeCard(dashboard: ClientDashboardResponse, scale: CGFloat) -> some View {
        let debtDisplay = debtDisplay(for: dashboard)

        return VStack(spacing: 16 * scale) {
            HStack(spacing: 16 * scale) {
                BikePlaceholderView(scale: scale)

                VStack(alignment: .leading, spacing: 4 * scale) {
                    Text(dashboard.bikeModel)
                        .font(.system(size: 16 * scale, weight: .bold))
                        .foregroundStyle(ClientColors.mainText)
                        .lineLimit(2)

                    Text("\(moneyText(dashboard.presets.weekRub))/нед")
                        .font(.system(size: 12 * scale, weight: .medium))
                        .foregroundStyle(ClientColors.subtleText)
                }

                Spacer(minLength: 0)
            }

            Rectangle()
                .fill(ClientColors.borderSoft)
                .frame(height: 1)

            HStack(alignment: .top, spacing: 10 * scale) {
                statItem(
                    title: debtDisplay.title,
                    value: moneyText(debtDisplay.amountRub),
                    valueColor: debtDisplay.color,
                    scale: scale
                )

                statItem(
                    title: "КОРРЕКТ.",
                    value: moneyText(dashboard.totalAdjustmentRub),
                    valueColor: ClientColors.mainText,
                    scale: scale
                )

                statItem(
                    title: "ОПЛАЧ. ДО",
                    value: paidUntilText(dashboard.paidUntil),
                    valueColor: ClientColors.mainText,
                    scale: scale
                )
            }
        }
        .padding(.horizontal, 23 * scale)
        .padding(.vertical, 21 * scale)
        .background(ClientColors.card)
        .overlay(
            RoundedRectangle(cornerRadius: 15 * scale, style: .continuous)
                .stroke(ClientColors.borderSoft, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 15 * scale, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 15 * scale, x: 0, y: 20 * scale)
    }

    private func debtDisplay(for dashboard: ClientDashboardResponse) -> (title: String, amountRub: Int, color: Color) {
        if dashboard.debtRub > 0 {
            return ("ДОЛГ", dashboard.debtRub, ClientColors.debt)
        }

        let daysLeft = max(0, remainingPaidDays(until: dashboard.paidUntil))
        let perDay = Double(dashboard.presets.monthRub) / 28.0
        let rawBalance = perDay * Double(daysLeft)
        let roundedToTens = Int((rawBalance / 10.0).rounded() * 10.0)
        return ("ОСТАТОК", max(0, roundedToTens), ClientColors.success)
    }

    private func remainingPaidDays(until rawDate: String) -> Int {
        guard let paidUntilDate = Self.apiDateFormatter.date(from: rawDate) else {
            return 0
        }
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: Date())
        let target = calendar.startOfDay(for: paidUntilDate)
        return calendar.dateComponents([.day], from: today, to: target).day ?? 0
    }

    private func statItem(
        title: String,
        value: String,
        valueColor: Color,
        scale: CGFloat
    ) -> some View {
        VStack(alignment: .leading, spacing: 3 * scale) {
            Text(title)
                .font(.system(size: 9 * scale, weight: .medium))
                .foregroundStyle(ClientColors.subtleText)
                .tracking(0.36 * scale)

            Text(value)
                .font(.system(size: 13 * scale, weight: .bold))
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func quickPaymentSection(dashboard: ClientDashboardResponse, scale: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 14 * scale) {
            Text("БЫСТРАЯ ОПЛАТА")
                .font(.system(size: 11 * scale, weight: .bold))
                .foregroundStyle(ClientColors.subtleText)
                .tracking(0.88 * scale)

            Button {
                startPayment(type: .debtExact)
            } label: {
                if viewModel.isCreatingPayment {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50 * scale)
                } else {
                    Text("Оплатить весь долг · \(moneyText(dashboard.debtRub))")
                        .font(.system(size: 14 * scale, weight: .bold))
                        .tracking(0.28 * scale)
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50 * scale)
                }
            }
            .buttonStyle(.plain)
            .background(ClientColors.primaryButton)
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
            .disabled(viewModel.isCreatingPayment || dashboard.presets.debtExactRub <= 0)
            .accessibilityIdentifier("client.quickPayDebtButton")

            Button {
                selectedPaymentType = .week
                withAnimation(.spring(response: 0.26, dampingFraction: 0.9)) {
                    isTariffSheetPresented = true
                }
            } label: {
                Text("Выбрать тариф ↑")
                    .font(.system(size: 14 * scale, weight: .bold))
                    .tracking(0.28 * scale)
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50 * scale)
            }
            .buttonStyle(.plain)
            .background(ClientColors.primaryButton)
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
            .accessibilityIdentifier("client.paymentButton")
        }
    }

    private func tariffSheet(dashboard: ClientDashboardResponse, scale: CGFloat) -> some View {
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 2 * scale)
                .fill(ClientColors.sheetHandle)
                .frame(width: 40 * scale, height: 4 * scale)
                .padding(.top, 14 * scale)

            HStack {
                Text("Оплата аренды")
                    .font(.system(size: 31 * scale / 2.0, weight: .bold))
                    .foregroundStyle(ClientColors.mainText)

                Spacer(minLength: 0)

                Button {
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
                        isTariffSheetPresented = false
                    }
                } label: {
                    Text("Закрыть ✕")
                        .font(.system(size: 12 * scale, weight: .medium))
                        .foregroundStyle(ClientColors.subtleText)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 23 * scale)
            .padding(.top, 12 * scale)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10 * scale), GridItem(.flexible(), spacing: 10 * scale)], spacing: 10 * scale) {
                ForEach(tariffTypes, id: \.self) { paymentType in
                    tariffCard(
                        paymentType: paymentType,
                        amount: amountFor(paymentType, presets: dashboard.presets),
                        isSelected: paymentType == selectedPaymentType,
                        scale: scale
                    )
                    .onTapGesture {
                        selectedPaymentType = paymentType
                    }
                }
            }
            .padding(.horizontal, 23 * scale)
            .padding(.top, 14 * scale)

            Button {
                withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
                    isTariffSheetPresented = false
                }
                startPayment(type: selectedPaymentType)
            } label: {
                if viewModel.isCreatingPayment {
                    ProgressView()
                        .tint(ClientColors.mainText)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52 * scale)
                } else {
                    Text("Оплатить выбранный · \(moneyText(amountFor(selectedPaymentType, presets: dashboard.presets)))")
                        .font(.system(size: 14 * scale, weight: .bold))
                        .tracking(0.28 * scale)
                        .foregroundStyle(ClientColors.mainText)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52 * scale)
                }
            }
            .buttonStyle(.plain)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                    .stroke(ClientColors.mainText, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
            .padding(.horizontal, 23 * scale)
            .padding(.top, 16 * scale)
            .padding(.bottom, 24 * scale)
            .disabled(viewModel.isCreatingPayment)
        }
        .frame(maxWidth: .infinity)
        .background(Color.white)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(ClientColors.mainText)
                .frame(height: 1)
                .opacity(0.2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
        .shadow(color: Color.black.opacity(0.12), radius: 15 * scale, x: 0, y: -10 * scale)
        .padding(.horizontal, 0)
        .offset(y: 26 * scale)
    }

    private func tariffCard(
        paymentType: ClientPaymentType,
        amount: Int,
        isSelected: Bool,
        scale: CGFloat
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(paymentType.title)
                .font(.system(size: 11 * scale, weight: .medium))
                .foregroundStyle(ClientColors.subtleText)

            Spacer(minLength: 0)

            Text(moneyText(amount))
                .font(.system(size: 13 * scale, weight: .bold))
                .foregroundStyle(ClientColors.mainText)
        }
        .padding(.horizontal, isSelected ? 16 * scale : 15 * scale)
        .padding(.vertical, isSelected ? 14 * scale : 13 * scale)
        .frame(maxWidth: .infinity)
        .frame(height: isSelected ? 124 * scale : 122 * scale)
        .background(ClientColors.card)
        .overlay(alignment: .bottomTrailing) {
            TariffIllustrationView(paymentType: paymentType, scale: scale)
                .opacity(0.9)
                .padding(.trailing, -6 * scale)
                .padding(.bottom, -6 * scale)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                .stroke(
                    isSelected ? ClientColors.mainText : ClientColors.borderSoft,
                    lineWidth: isSelected ? 2 : 1
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
    }

    private func startPayment(type: ClientPaymentType) {
        guard case let .loaded(dashboard) = viewModel.state else { return }

        if type == .debtExact, dashboard.presets.debtExactRub <= 0 {
            return
        }

        if dashboard.requiresReceiptEmail {
            pendingPaymentType = type
            receiptEmail = ""
            isReceiptEmailDialogPresented = true
            return
        }

        viewModel.createPayment(type: type)
    }

    private func amountFor(_ type: ClientPaymentType, presets: ClientPaymentPresets) -> Int {
        switch type {
        case .day:
            return presets.dayRub
        case .week:
            return presets.weekRub
        case .twoWeeks:
            return presets.twoWeeksRub
        case .month:
            return presets.monthRub
        case .debtExact:
            return presets.debtExactRub
        }
    }

    private func paidUntilText(_ rawDate: String) -> String {
        guard let date = Self.apiDateFormatter.date(from: rawDate) else {
            return rawDate
        }

        let day = Self.dayFormatter.string(from: date)
        let year = Self.yearFormatter.string(from: date)
        let monthIndex = Calendar(identifier: .gregorian).component(.month, from: date) - 1
        let month = Self.ruShortMonths[safe: monthIndex] ?? ""
        return "\(day) \(month) \(year)"
    }

    private func moneyText(_ value: Int) -> String {
        let number = NSNumber(value: value)
        let formatted = Self.moneyFormatter.string(from: number) ?? "\(value)"
        return "\(formatted.replacingOccurrences(of: "\u{00A0}", with: " ")) ₽"
    }

    private static let apiDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "dd"
        return formatter
    }()

    private static let yearFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "yyyy"
        return formatter
    }()

    private static let moneyFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.maximumFractionDigits = 0
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()

    private static let ruShortMonths = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]
}

private enum ClientColors {
    static let mainText = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    static let subtleText = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    static let card = Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255)
    static let borderSoft = Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255)
    static let placeholder = Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255)
    static let placeholderStroke = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    static let primaryButton = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    static let debt = Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255)
    static let success = Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255)
    static let sheetHandle = Color(red: 211 / 255, green: 215 / 255, blue: 221 / 255)
}

private struct BikePlaceholderView: View {
    let scale: CGFloat

    var body: some View {
        let size = 84 * scale
        let cornerRadius = 18 * scale
        let inset = 2.5 * scale

        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(ClientColors.placeholder)

            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(
                    style: StrokeStyle(lineWidth: 1, dash: [4 * scale, 3 * scale])
                )
                .foregroundStyle(ClientColors.placeholderStroke)

            Path { path in
                path.move(to: CGPoint(x: inset, y: inset))
                path.addLine(to: CGPoint(x: size - inset, y: size - inset))
                path.move(to: CGPoint(x: size - inset, y: inset))
                path.addLine(to: CGPoint(x: inset, y: size - inset))
            }
            .stroke(
                ClientColors.placeholderStroke.opacity(0.55),
                style: StrokeStyle(lineWidth: 1, lineCap: .butt, dash: [3 * scale, 2.5 * scale])
            )
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

private struct TariffIllustrationView: View {
    let paymentType: ClientPaymentType
    let scale: CGFloat

    var body: some View {
        switch paymentType {
        case .day:
            Image(systemName: "sun.max.fill")
                .font(.system(size: 42 * scale, weight: .regular))
                .foregroundStyle(Color(red: 201 / 255, green: 203 / 255, blue: 208 / 255))

        case .week:
            Image(systemName: "calendar")
                .font(.system(size: 48 * scale, weight: .regular))
                .foregroundStyle(Color(red: 201 / 255, green: 203 / 255, blue: 208 / 255))

        case .twoWeeks:
            HStack(spacing: 4 * scale) {
                Image(systemName: "circle")
                Image(systemName: "circle.fill")
                Image(systemName: "circle")
            }
            .font(.system(size: 18 * scale, weight: .regular))
            .foregroundStyle(Color(red: 201 / 255, green: 203 / 255, blue: 208 / 255))

        case .month:
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 50 * scale, weight: .regular))
                .foregroundStyle(Color(red: 201 / 255, green: 203 / 255, blue: 208 / 255))

        case .debtExact:
            EmptyView()
        }
    }
}

private struct SafariPaymentView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context _: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_: SFSafariViewController, context _: Context) {}
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        guard indices.contains(index) else { return nil }
        return self[index]
    }
}
