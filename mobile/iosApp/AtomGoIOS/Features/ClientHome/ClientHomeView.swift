import SwiftUI
import SafariServices

struct ClientHomeView: View {
    @ObservedObject var viewModel: ClientHomeViewModel
    let onLogout: () -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var isPaymentDialogPresented = false
    @State private var paymentDialogPresets: ClientPaymentPresets?
    @State private var paymentSafariUrl: URL?
    @State private var activePaymentId: String?
    @State private var isSafariPresented = false
    private var shouldAutoOpenPaymentSafari: Bool {
        !ProcessInfo.processInfo.arguments.contains("-ATOMGO_DISABLE_PAYMENT_SAFARI_AUTOPEN")
    }

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .idle, .loading:
                    ProgressView("Загружаем клиентский дашборд...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                case let .failed(message):
                    VStack(spacing: 12) {
                        Text("Не удалось загрузить данные")
                            .font(.headline)
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

                case let .loaded(dashboard):
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            infoCard(title: "Велосипед", value: dashboard.bikeModel)
                            infoCard(title: "Дата начала аренды", value: dashboard.rentalStart)
                            infoCard(title: "Оплачено до", value: dashboard.paidUntil)
                            infoCard(title: "Долг", value: "\(dashboard.debtRub) ₽")
                            infoCard(title: "Суммарная корректировка", value: "\(dashboard.totalAdjustmentRub) ₽")

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Пресеты оплаты")
                                    .font(.headline)
                                presetRow("1 день", dashboard.presets.dayRub)
                                presetRow("1 неделя", dashboard.presets.weekRub)
                                presetRow("2 недели", dashboard.presets.twoWeeksRub)
                                presetRow("1 месяц", dashboard.presets.monthRub)
                                presetRow("Ровно долг", dashboard.presets.debtExactRub)
                            }
                            .padding(16)
                            .background(AppDesign.cardBackground)
                            .cornerRadius(14)

                            paymentActions(dashboard)

                            if let paymentErrorMessage = viewModel.paymentErrorMessage {
                                Text(paymentErrorMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.danger)
                                    .padding(.horizontal, 4)
                                    .accessibilityIdentifier("client.paymentErrorMessage")
                            }

                            if let paymentStatusMessage = viewModel.paymentStatusMessage {
                                Text(paymentStatusMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.success)
                                    .padding(.horizontal, 4)
                                    .accessibilityIdentifier("client.paymentStatusMessage")
                            }

                            if let payment = viewModel.paymentResult {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text("Платеж ЮKassa")
                                        .font(.headline)
                                    Text("Сумма: \(payment.amountRub) ₽")
                                        .font(.subheadline)
                                    Text("Статус: \(payment.status)")
                                        .font(.subheadline)
                                        .foregroundStyle(AppDesign.subtleText)

                                    Button("Открыть оплату повторно") {
                                        if let url = URL(string: payment.confirmationUrl) {
                                            activePaymentId = payment.paymentId
                                            paymentSafariUrl = url
                                            isSafariPresented = true
                                        }
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(payment.confirmationUrl.isEmpty || payment.status == "succeeded")
                                    .accessibilityIdentifier("client.openPaymentAgainButton")

                                    if viewModel.isRefreshingPaymentStatus {
                                        ProgressView("Проверяем статус платежа...")
                                    }
                                }
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(AppDesign.cardBackground)
                                .cornerRadius(14)
                                .accessibilityIdentifier("client.paymentCard")
                                .accessibilityValue(payment.confirmationUrl)
                            }
                        }
                        .padding(16)
                    }
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("ClientHome")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Выйти") {
                        onLogout()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Обновить") {
                        viewModel.load()
                    }
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
            if phase == .active, let activePaymentId {
                viewModel.refreshPaymentStatus(paymentId: activePaymentId)
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
        .confirmationDialog(
            "Выберите период оплаты",
            isPresented: $isPaymentDialogPresented,
            titleVisibility: .visible
        ) {
            if let presets = paymentDialogPresets {
                ForEach(ClientPaymentType.allCases) { type in
                    Button("\(type.title) • \(amountFor(type, presets: presets)) ₽") {
                        viewModel.createPayment(type: type)
                    }
                    .disabled(type == .debtExact && presets.debtExactRub <= 0)
                }
            }
            Button("Отмена", role: .cancel) {}
        }
    }

    private func infoCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(AppDesign.subtleText)
            Text(value)
                .font(.headline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .cornerRadius(14)
    }

    private func presetRow(_ title: String, _ amount: Int) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text("\(amount) ₽")
                .fontWeight(.semibold)
        }
        .font(.subheadline)
    }

    private func paymentActions(_ dashboard: ClientDashboardResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                paymentDialogPresets = dashboard.presets
                isPaymentDialogPresented = true
            } label: {
                if viewModel.isCreatingPayment {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Оплатить")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                }
            }
            .disabled(viewModel.isCreatingPayment)
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("client.paymentButton")

            Text("Оплата создаёт платеж в ЮKassa и возвращает ссылку подтверждения.")
                .font(.caption)
                .foregroundStyle(AppDesign.subtleText)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .cornerRadius(14)
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
}

private struct SafariPaymentView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context _: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_: SFSafariViewController, context _: Context) {}
}
