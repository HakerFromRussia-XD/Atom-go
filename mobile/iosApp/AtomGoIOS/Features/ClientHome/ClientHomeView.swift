import SwiftUI
import SafariServices

enum ClientDashboardPresentationLogic {
    static func debtDisplay(for dashboard: ClientDashboardResponse) -> (title: String, amountRub: Int, isDebt: Bool) {
        if dashboard.debtRub > 0 {
            return ("ДОЛГ", dashboard.debtRub, true)
        }
        return ("ОСТАТОК", dashboard.balanceRub ?? 0, false)
    }
}

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
    @State private var isCustomAmountDialogPresented = false
    @State private var pendingPaymentType: ClientPaymentType?
    @State private var pendingCustomAmountRub: Int?
    @State private var receiptEmail = ""
    @State private var customAmountText = ""
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

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
        .onChange(of: viewModel.paymentErrorMessage) { newValue in
            presentToast(newValue)
        }
        .onChange(of: viewModel.paymentStatusMessage) { newValue in
            presentToast(newValue)
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

            Button(pendingPaymentType == nil ? "Сохранить" : "Продолжить") {
                submitReceiptEmail()
            }
            .accessibilityIdentifier("client.receiptEmailSubmitButton")

            Button("Отмена", role: .cancel) {
                pendingPaymentType = nil
                pendingCustomAmountRub = nil
                receiptEmail = ""
            }
        } message: {
            Text("Укажите email, куда ЮKassa отправит чек.")
        }
        .alert("Другая сумма", isPresented: $isCustomAmountDialogPresented) {
            TextField("Сумма, ₽", text: $customAmountText)
                .keyboardType(.numberPad)
                .accessibilityIdentifier("client.customAmountField")

            Button("Продолжить") {
                submitCustomAmount()
            }
            .accessibilityIdentifier("client.customAmountSubmitButton")

            Button("Отмена", role: .cancel) {
                customAmountText = ""
            }
        } message: {
            Text("Введите сумму в рублях. После подтверждения откроется страница оплаты ЮKassa на указанную сумму.")
        }
        .appToast(message: $toastMessage, bottomPadding: 86)
    }

    private func loadingView(scale: CGFloat, safeTop: CGFloat) -> some View {
        VStack(spacing: 18 * scale) {
            topBar(scale: scale, safeTop: safeTop, canEditReceiptEmail: false)

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
            topBar(scale: scale, safeTop: safeTop, canEditReceiptEmail: false)

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
        let bottomPadding = isTariffSheetPresented ? min(502 * scale, totalHeight * 0.60) + 16 * scale : 0

        return VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    topBar(
                        scale: scale,
                        safeTop: safeTop,
                        canEditReceiptEmail: shouldShowReceiptEmailRow(for: dashboard),
                        onEditReceiptEmail: {
                            openReceiptEmailEditor(dashboard: dashboard)
                        }
                    )

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

                    if let payment = viewModel.paymentResult {
                        Text(
                            [
                                payment.confirmationUrl,
                                payment.taxMode ?? "",
                                payment.fiscalizationStatus ?? ""
                            ].joined(separator: "|")
                        )
                        .font(.caption2)
                        .foregroundStyle(AppDesign.clear)
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

    private func topBar(
        scale: CGFloat,
        safeTop: CGFloat,
        canEditReceiptEmail: Bool,
        onEditReceiptEmail: (() -> Void)? = nil
    ) -> some View {
        HStack {
            Button {
                onLogout()
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                        .fill(AppDesign.surfaceBackground)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                                .stroke(ClientColors.mainText, lineWidth: 1)
                        )

                    Image("exit")
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16 * scale, height: 16 * scale)
                }
                .frame(width: 47 * scale, height: 47 * scale)
            }
            .buttonStyle(.plain)

            Spacer(minLength: 0)

            Text("Моя аренда")
                .font(.system(size: 31 * scale / 1.7, weight: .bold))
                .foregroundStyle(ClientColors.mainText)

            Spacer(minLength: 0)

            if canEditReceiptEmail, let onEditReceiptEmail {
                Button {
                    onEditReceiptEmail()
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                            .fill(AppDesign.surfaceBackground)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                                    .stroke(ClientColors.mainText, lineWidth: 1)
                            )

                        Image("refaktoring")
                            .renderingMode(.original)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 16 * scale, height: 16 * scale)
                    }
                    .frame(width: 47 * scale, height: 47 * scale)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("client.receiptEmailEditButton")
            } else {
                AppDesign.clear
                    .frame(width: 47 * scale, height: 47 * scale)
            }
        }
        .padding(.top, 10 * scale)
    }

    private func presentToast(_ message: String?) {
        guard let message = message?.trimmingCharacters(in: .whitespacesAndNewlines),
              !message.isEmpty else { return }
        toastDismissTask?.cancel()
        toastMessage = message
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            toastMessage = nil
        }
    }

    private func bikeCard(dashboard: ClientDashboardResponse, scale: CGFloat) -> some View {
        let debtDisplay = debtDisplay(for: dashboard)
        let completedAt = completedAtText(for: dashboard)
        return VStack(spacing: 16 * scale) {
                HStack(spacing: 16 * scale) {
                    ClientBikePhotoView(source: dashboard.bikeAvatarUrl) {
                        BikePlaceholderView(scale: scale)
                    }
                    .frame(width: 84 * scale, height: 84 * scale)
                    .clipShape(RoundedRectangle(cornerRadius: 18 * scale, style: .continuous))

                    VStack(alignment: .leading, spacing: 4 * scale) {
                        Text(dashboard.bikeModel)
                            .font(.system(size: 16 * scale, weight: .bold))
                            .foregroundStyle(ClientColors.mainText)
                            .lineLimit(2)

                        Text("\(moneyText(dashboard.presets.weekRub))/нед")
                            .font(.system(size: 12 * scale, weight: .medium))
                            .foregroundStyle(ClientColors.subtleText)

                        if shouldShowReceiptEmailRow(for: dashboard) {
                            Text(receiptEmailText(for: dashboard))
                                .font(.system(size: 10 * scale, weight: .medium))
                                .foregroundStyle(receiptEmailColor(for: dashboard))
                                .lineLimit(1)
                                .truncationMode(.tail)
                                .padding(.top, 1 * scale)
                        }
                    }

                    Spacer(minLength: 0)
                }

                Rectangle()
                    .fill(ClientColors.borderSoft)
                    .frame(height: 1)

                HStack(alignment: .top, spacing: 0) {
                    statItem(
                        title: debtDisplay.title,
                        value: moneyText(debtDisplay.amountRub),
                        valueColor: debtDisplay.color,
                        scale: scale
                    )

                    Spacer(minLength: 12 * scale)

                    statItem(
                        title: "КОРРЕКТ.",
                        value: moneyText(dashboard.totalAdjustmentRub),
                        valueColor: ClientColors.mainText,
                        scale: scale
                    )

                    Spacer(minLength: 12 * scale)

                    statItem(
                        title: "ОПЛАЧЕН ДО",
                        value: paidUntilText(for: dashboard),
                        valueColor: ClientColors.mainText,
                        scale: scale
                    )

                    if let completedAt {
                        Spacer(minLength: 12 * scale)

                        statItem(
                            title: "ЗАВЕРШЕНА",
                            value: completedAt,
                            valueColor: ClientColors.mainText,
                            scale: scale
                        )
                    }
                }
        }
        .padding(.horizontal, 23 * scale)
        .padding(.vertical, 21 * scale)
        .background(ClientColors.card)
        .overlay(
            RoundedRectangle(cornerRadius: 15 * scale, style: .continuous)
                .stroke(ClientColors.mainText, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 15 * scale, style: .continuous))
        .shadow(color: AppDesign.black.opacity(0.08), radius: 15 * scale, x: 0, y: 20 * scale)
    }

    private func debtDisplay(for dashboard: ClientDashboardResponse) -> (title: String, amountRub: Int, color: Color) {
        let display = ClientDashboardPresentationLogic.debtDisplay(for: dashboard)
        return (display.title, display.amountRub, display.isDebt ? ClientColors.debt : ClientColors.success)
    }

    private func paidUntilText(for dashboard: ClientDashboardResponse) -> String {
        guard let paidUntilDate = Self.apiDateFormatter.date(from: dashboard.paidUntil) else {
            return dashboard.paidUntil
        }
        return displayDateText(from: paidUntilDate)
    }

    private func completedAtText(for dashboard: ClientDashboardResponse) -> String? {
        guard let completedAt = dashboard.completedAt, !completedAt.isEmpty else {
            return dashboard.rentalIsActive ? nil : "—"
        }
        guard let completedAtDate = Self.apiDateFormatter.date(from: completedAt) else {
            return completedAt
        }
        return displayDateText(from: completedAtDate)
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
                ZStack {
                    RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                        .fill(ClientColors.primaryButton)

                    if viewModel.isCreatingPayment {
                        ProgressView()
                            .tint(AppDesign.surfaceBackground)
                    } else {
                        Text("Оплатить весь долг · \(moneyText(max(0, dashboard.debtRub)))")
                            .font(.system(size: 14 * scale, weight: .bold))
                            .tracking(0.28 * scale)
                            .foregroundStyle(AppDesign.surfaceBackground)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 63 * scale)
                .contentShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isCreatingPayment || dashboard.debtRub <= 0)
            .accessibilityIdentifier("client.quickPayDebtButton")

            Button {
                selectedPaymentType = .week
                withAnimation(.spring(response: 0.26, dampingFraction: 0.9)) {
                    isTariffSheetPresented = true
                }
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                        .fill(ClientColors.primaryButton)

                    Text("Выбрать тариф ↑")
                        .font(.system(size: 14 * scale, weight: .bold))
                        .tracking(0.28 * scale)
                        .foregroundStyle(AppDesign.surfaceBackground)
                }
                .contentShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
                .frame(maxWidth: .infinity)
                .frame(height: 63 * scale)
            }
            .buttonStyle(.plain)
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
                openCustomAmountDialog()
            } label: {
                tariffActionLabel(
                    title: "Другая сумма",
                    isLoading: false,
                    scale: scale
                )
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isCreatingPayment)
            .accessibilityIdentifier("client.customAmountButton")
            .padding(.horizontal, 23 * scale)
            .padding(.top, 12 * scale)

            Button {
                withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
                    isTariffSheetPresented = false
                }
                startPayment(type: selectedPaymentType)
            } label: {
                tariffActionLabel(
                    title: "Оплатить выбранный · \(moneyText(amountFor(selectedPaymentType, presets: dashboard.presets)))",
                    isLoading: viewModel.isCreatingPayment,
                    scale: scale
                )
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 23 * scale)
            .padding(.top, 10 * scale)
            .padding(.bottom, 18 * scale)
            .disabled(viewModel.isCreatingPayment)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 502 * scale, alignment: .top)
        .background(AppDesign.surfaceBackground)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(ClientColors.mainText)
                .frame(height: 1)
                .opacity(0.2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
        .shadow(color: AppDesign.black.opacity(0.12), radius: 15 * scale, x: 0, y: -10 * scale)
        .padding(.horizontal, 0)
    }

    private func tariffCard(
        paymentType: ClientPaymentType,
        amount: Int,
        isSelected: Bool,
        scale: CGFloat
    ) -> some View {
        let horizontalInset = (isSelected ? 16.0 : 15.0) * scale
        let topInset = (isSelected ? 14.0 : 13.0) * scale
        let bottomInset = (isSelected ? 14.0 : 13.0) * scale

        return ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                Text(paymentType.title)
                    .font(.system(size: 11 * scale, weight: .medium))
                    .foregroundStyle(ClientColors.subtleText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, topInset)
                    .padding(.leading, horizontalInset)

                Spacer(minLength: 0)

                Text(moneyText(amount))
                    .font(.system(size: 13 * scale, weight: .bold))
                    .foregroundStyle(ClientColors.mainText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.bottom, bottomInset)
                    .padding(.leading, horizontalInset)
            }

            TariffIllustrationView(paymentType: paymentType, scale: scale)
                .opacity(0.95)
                .padding(.trailing, -8 * scale)
                .padding(.bottom, -8 * scale)
        }
        .frame(maxWidth: .infinity)
        .frame(height: isSelected ? 124 * scale : 122 * scale)
        .background(ClientColors.card)
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

    private func tariffActionLabel(title: String, isLoading: Bool, scale: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                .fill(AppDesign.surfaceBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                        .stroke(ClientColors.mainText, lineWidth: 1)
                )

            if isLoading {
                ProgressView()
                    .tint(ClientColors.mainText)
            } else {
                Text(title)
                    .font(.system(size: 14 * scale, weight: .bold))
                    .tracking(0.28 * scale)
                    .foregroundStyle(ClientColors.mainText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                    .padding(.horizontal, 12 * scale)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 63 * scale)
        .contentShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
    }

    private func startPayment(type: ClientPaymentType, amountRub: Int? = nil) {
        guard case let .loaded(dashboard) = viewModel.state else { return }

        if type == .debtExact, dashboard.presets.debtExactRub <= 0 {
            return
        }
        if type == .custom, (amountRub ?? 0) <= 0 {
            viewModel.paymentErrorMessage = "Введите сумму больше 0 ₽."
            return
        }

        if dashboard.requiresReceiptEmail {
            pendingPaymentType = type
            pendingCustomAmountRub = amountRub
            receiptEmail = dashboard.receiptEmail ?? ""
            isReceiptEmailDialogPresented = true
            return
        }

        viewModel.createPayment(type: type, amountRub: amountRub)
    }

    private func openCustomAmountDialog() {
        customAmountText = ""
        isCustomAmountDialogPresented = true
    }

    private func submitCustomAmount() {
        let digits = customAmountText.filter(\.isNumber)
        guard let amount = Int(digits), amount > 0 else {
            viewModel.paymentErrorMessage = "Введите сумму больше 0 ₽."
            customAmountText = ""
            return
        }

        customAmountText = ""
        withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) {
            isTariffSheetPresented = false
        }
        startPayment(type: .custom, amountRub: amount)
    }

    private func submitReceiptEmail() {
        let trimmedEmail = receiptEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty else {
            viewModel.paymentErrorMessage = "Укажите email для чека."
            return
        }

        if let pendingPaymentType {
            viewModel.createPayment(
                type: pendingPaymentType,
                amountRub: pendingCustomAmountRub,
                receiptEmail: trimmedEmail
            )
        } else {
            viewModel.updateReceiptEmail(trimmedEmail)
        }

        self.pendingPaymentType = nil
        pendingCustomAmountRub = nil
        receiptEmail = ""
    }

    private func openReceiptEmailEditor(dashboard: ClientDashboardResponse) {
        pendingPaymentType = nil
        pendingCustomAmountRub = nil
        receiptEmail = dashboard.receiptEmail ?? ""
        isReceiptEmailDialogPresented = true
    }

    private func shouldShowReceiptEmailRow(for dashboard: ClientDashboardResponse) -> Bool {
        dashboard.taxMode == "individual_entrepreneur"
    }

    private func receiptEmailText(for dashboard: ClientDashboardResponse) -> String {
        let value = dashboard.receiptEmail?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? "Email для чека не указан" : value
    }

    private func receiptEmailColor(for dashboard: ClientDashboardResponse) -> Color {
        let hasEmail = !(dashboard.receiptEmail?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
        return hasEmail ? ClientColors.mainText : ClientColors.subtleText
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
        case .custom:
            return 0
        }
    }

    private func displayDateText(from date: Date) -> String {
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
    static let mainText = AppDesign.darkControl
    static let subtleText = AppDesign.paleSky
    static let card = AppDesign.blackHaze
    static let borderSoft = AppDesign.athensGray
    static let placeholder = AppDesign.placeholder
    static let placeholderStroke = AppDesign.placeholderStroke
    static let primaryButton = AppDesign.darkControl
    static let debt = AppDesign.danger
    static let success = AppDesign.success
    static let sheetHandle = AppDesign.sheetHandle
    static let iconFill = AppDesign.iconFill
    static let iconStroke = AppDesign.iconStroke
    static let iconCanvas = AppDesign.iconCanvas
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

private struct ClientBikePhotoView<Placeholder: View>: View {
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
                    image
                        .resizable()
                        .scaledToFill()
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

private struct TariffIllustrationView: View {
    let paymentType: ClientPaymentType
    let scale: CGFloat

    var body: some View {
        switch paymentType {
        case .day:
            ZStack {
                Circle()
                    .fill(ClientColors.iconFill)
                    .frame(width: 30 * scale, height: 30 * scale)
                ForEach(0..<8, id: \.self) { idx in
                    RoundedRectangle(cornerRadius: 2 * scale, style: .continuous)
                        .fill(ClientColors.iconFill)
                        .frame(width: 4 * scale, height: 11 * scale)
                        .offset(y: -25 * scale)
                        .rotationEffect(.degrees(Double(idx) * 45))
                }
            }
            .frame(width: 66 * scale, height: 66 * scale)

        case .week:
            CalendarMiniIcon(scale: scale, accentClock: false)
                .frame(width: 84 * scale, height: 84 * scale)

        case .twoWeeks:
            HStack(spacing: 4 * scale) {
                Image(systemName: "circle")
                Image(systemName: "circle.fill")
                Image(systemName: "circle")
            }
            .font(.system(size: 18 * scale, weight: .regular))
            .foregroundStyle(AppDesign.disabledText)

        case .month:
            CalendarMiniIcon(scale: scale, accentClock: true)
                .frame(width: 86 * scale, height: 86 * scale)

        case .debtExact:
            EmptyView()
        case .custom:
            EmptyView()
        }
    }
}

private struct CalendarMiniIcon: View {
    let scale: CGFloat
    let accentClock: Bool

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                .fill(ClientColors.iconFill)
                .frame(width: 74 * scale, height: 74 * scale)

            VStack(spacing: 5 * scale) {
                HStack(spacing: 8 * scale) {
                    RoundedRectangle(cornerRadius: 1.5 * scale, style: .continuous)
                        .fill(ClientColors.iconStroke)
                        .frame(width: 2.6 * scale, height: 9 * scale)
                    Spacer(minLength: 0)
                    RoundedRectangle(cornerRadius: 1.5 * scale, style: .continuous)
                        .fill(ClientColors.iconStroke)
                        .frame(width: 2.6 * scale, height: 9 * scale)
                }
                .padding(.horizontal, 16 * scale)
                .padding(.top, 6 * scale)

                RoundedRectangle(cornerRadius: 5 * scale, style: .continuous)
                    .fill(ClientColors.iconCanvas)
                    .frame(width: 58 * scale, height: 48 * scale)
                    .overlay {
                        VStack(spacing: 6 * scale) {
                            ForEach(0..<3, id: \.self) { _ in
                                HStack(spacing: 6 * scale) {
                                    ForEach(0..<3, id: \.self) { _ in
                                        RoundedRectangle(cornerRadius: 1.5 * scale, style: .continuous)
                                            .fill(ClientColors.iconStroke)
                                            .frame(width: 7 * scale, height: 7 * scale)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.bottom, 6 * scale)
            }

            if accentClock {
                Circle()
                    .fill(ClientColors.iconFill)
                    .frame(width: 34 * scale, height: 34 * scale)
                    .overlay {
                        Image(systemName: "clock.fill")
                            .font(.system(size: 14 * scale, weight: .semibold))
                            .foregroundStyle(ClientColors.iconCanvas)
                    }
                    .offset(x: 5 * scale, y: 5 * scale)
            }
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
