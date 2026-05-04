import SwiftUI

struct ClientHomeView: View {
    @ObservedObject var viewModel: ClientHomeViewModel
    let onLogout: () -> Void
    @Environment(\.openURL) private var openURL
    @State private var isPaymentDialogPresented = false
    @State private var paymentDialogPresets: ClientPaymentPresets?

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
                            }

                            if let payment = viewModel.paymentResult {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text("Платеж создан")
                                        .font(.headline)
                                    Text("Сумма: \(payment.amountRub) ₽")
                                        .font(.subheadline)
                                    Text("Статус: \(payment.status)")
                                        .font(.subheadline)
                                        .foregroundStyle(AppDesign.subtleText)

                                    Button("Открыть ссылку оплаты") {
                                        if let url = URL(string: payment.confirmationUrl) {
                                            openURL(url)
                                        }
                                    }
                                    .buttonStyle(.borderedProminent)
                                }
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(AppDesign.cardBackground)
                                .cornerRadius(14)
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
