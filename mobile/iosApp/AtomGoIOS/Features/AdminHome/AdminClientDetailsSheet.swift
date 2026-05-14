import SwiftUI
import PhotosUI
import UIKit

private struct ClientRentalDetailsContext: Identifiable, Equatable {
    let clientId: String
    let rentalId: String
    let completedAtFallback: String?

    var id: String { "\(clientId)-\(rentalId)" }
}

struct AdminClientDetailsSheet: View {
    let details: AdminClientDetailsResponse?
    let isLoading: Bool
    let errorMessage: String?
    let operationErrorMessage: String?
    let operationSuccessMessage: String?
    let isOperationInProgress: Bool
    let selectedRentalDetails: AdminRentalDetailsResponse?
    let isRentalDetailsLoading: Bool
    let rentalDetailsErrorMessage: String?
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let fallbackSummaryForRental: (_ clientId: String, _ rentalId: String) -> AdminClientSummaryResponse?
    let onClose: () -> Void
    let onRetry: () -> Void
    let onAdjustDebtTap: (AdminClientDetailsResponse) -> Void
    /// Открыть модал admin-операции над carriedDebt с заранее выбранным типом
    /// (payment по «Принять оплату», writeoff по «Списать»).
    let onOpenCarriedDebtSheet: (AdminClientDetailsResponse, CarriedDebtOperationKind) -> Void
    let onSaveRentalComment: (String, String, String) -> Void
    let onSaveRentalLinks: (String, String, String?, String?) -> Void
    let onSaveClientProfile: (String, UpdateClientProfilePayload) -> Void
    let onDeleteClient: (String) -> Void
    let onCreateRental: (CreateRentalPayload) -> Void
    let onUpdateRental: (UpdateRentalPayload) -> Void
    let onDeleteRental: (String, String) -> Void
    let onOpenRental: (String, String, String?) -> Void
    let onRequestOpenRentalDetails: (String) -> Void
    let onRequestCloseRentalDetails: () -> Void
    let onAdjustDebtFromRental: (_ clientId: String, _ clientName: String, _ currentDebtRub: Int) -> Void
    let onFinishRental: (_ clientId: String, _ rentalId: String) -> Void
    let onStartRental: (_ rentalId: String, _ payload: CreateRentalPayload) -> Void

    @Environment(\.openURL) private var openURL
    @State private var isProfileEditorPresented = false
    @State private var isCreateRentalPresented = false
    @State private var isDeleteClientConfirmationPresented = false
    @State private var rentalDetailsContext: ClientRentalDetailsContext?
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            AppDesign.pageBackground.ignoresSafeArea()

            if isLoading && details == nil {
                ProgressView("Загружаем карточку клиента...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let errorMessage, details == nil {
                VStack(spacing: 12) {
                    Text("Не удалось загрузить клиента")
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundStyle(AppDesign.subtleText)
                        .multilineTextAlignment(.center)
                    Button("Повторить") {
                        onRetry()
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let details {
                VStack(spacing: 0) {
                    clientDetailsTopBar(details)
                        .padding(.top, 8)
                        .padding(.horizontal, 8)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 20) {
                            clientStatusCard(details)
                            profileBlock(details)
                            rentalHistoryBlock(details)
                        }
                        .padding(.horizontal, 8)
                        .padding(.top, 8)
                        .padding(.bottom, 126)
                    }
                }
                .fullScreenCover(isPresented: $isProfileEditorPresented) {
                    EditClientProfileSheet(
                        details: details,
                        isSaving: isOperationInProgress,
                        onCancel: {
                            isProfileEditorPresented = false
                        },
                        onSave: { payload in
                            onSaveClientProfile(details.clientId, payload)
                            isProfileEditorPresented = false
                        }
                    )
                }
                .fullScreenCover(isPresented: $isCreateRentalPresented) {
                    CreateRentalSheet(
                        clients: clients,
                        bikes: bikes,
                        preselectedClientId: details.clientId,
                        isSaving: isOperationInProgress,
                        onCancel: {
                            isCreateRentalPresented = false
                        },
                        onCreate: { payload in
                            onCreateRental(payload)
                            isCreateRentalPresented = false
                        }
                    )
                    .presentationDetents([.large])
                }
                .fullScreenCover(item: $rentalDetailsContext, onDismiss: {
                    onRequestCloseRentalDetails()
                }) { context in
                    AdminRentalDetailsScreen(
                        details: selectedRentalDetails,
                        fallbackSummary: fallbackSummaryForRental(context.clientId, context.rentalId),
                        completedAtFallback: context.completedAtFallback,
                        clients: clients,
                        isLoading: isRentalDetailsLoading,
                        errorMessage: rentalDetailsErrorMessage,
                        isOperationInProgress: isOperationInProgress,
                        onClose: {
                            rentalDetailsContext = nil
                        },
                        onRetry: {
                            onRequestOpenRentalDetails(context.rentalId)
                        },
                        onOpenClientCard: {
                            rentalDetailsContext = nil
                        },
                        onAdjustDebt: { clientId, clientName, currentDebtRub in
                            onAdjustDebtFromRental(clientId, clientName, currentDebtRub)
                        },
                        onFinishRental: { clientId, rentalId in
                            onFinishRental(clientId, rentalId)
                        },
                        onStartRental: { payload in
                            onStartRental(context.rentalId, payload)
                        },
                        onDeleteRental: { clientId, rentalId in
                            onDeleteRental(clientId, rentalId)
                            rentalDetailsContext = nil
                        }
                    )
                }
                .confirmationDialog(
                    "Удалить клиента?",
                    isPresented: $isDeleteClientConfirmationPresented,
                    titleVisibility: .visible
                ) {
                    Button("Удалить", role: .destructive) {
                        onDeleteClient(details.clientId)
                    }
                    Button("Отмена", role: .cancel) {}
                } message: {
                    Text("Клиент без истории аренд будет удален из каталога.")
                }
                .overlay(alignment: .center) {
                    if isOperationInProgress {
                        ProgressView()
                            .padding(16)
                            .background(.ultraThinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            } else {
                Color.clear
            }
        }
        .onChange(of: operationErrorMessage) { newValue in
            presentToast(newValue)
        }
        .onChange(of: operationSuccessMessage) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private func clientDetailsTopBar(_ details: AdminClientDetailsResponse) -> some View {
        HStack(spacing: 8) {
            detailsTopButton(systemName: "chevron.left", color: AppDesign.accent, action: onClose)

            Spacer()

            Text("Клиент")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AppDesign.titleText)

            Spacer()

            HStack(spacing: 8) {
                detailsTopButton(systemName: "pencil", color: AppDesign.accent) {
                    isProfileEditorPresented = true
                }
                detailsTopButton(systemName: "trash", color: AppDesign.danger) {
                    isDeleteClientConfirmationPresented = true
                }
                .disabled(!details.rentals.isEmpty || isOperationInProgress)
                .opacity(details.rentals.isEmpty ? 1 : 0.45)
            }
        }
        .frame(height: 62)
    }

    private func detailsTopButton(
        systemName: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(color)
                .frame(width: 47, height: 47)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(color, lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
    }

    private func clientStatusCard(_ details: AdminClientDetailsResponse) -> some View {
        let isActive = hasOpenRental(details)

        return VStack(alignment: .leading, spacing: isActive ? 16 : 18) {
            if isActive {
                HStack(spacing: 14) {
                    clientBikeAvatar(details, size: 80, cornerRadius: 14)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(details.bikeModel.isEmpty ? "—" : details.bikeModel)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(AppDesign.titleText)
                            .lineLimit(2)
                        Text("\(formattedRub(details.weeklyRateRub))/нед")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(AppDesign.subtleText)
                        statusPill(title: "Активный", color: AppDesign.success)
                            .padding(.top, 4)
                    }
                    Spacer(minLength: 0)
                }

                Divider().background(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
            } else {
                statusPill(title: "Неактивный", color: Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
            }

            financeMetrics(details)

            // Перенесённый долг — отдельный визуальный блок под основными метриками.
            // Появляется только когда он есть; в обычной карточке клиента не виден
            // и не занимает место. Здесь же две admin-операции — «Принять оплату»
            // и «Списать» — открывают модал CarriedDebtOperationSheet.
            // См. docs/14_rental_lifecycle.md §7.
            if details.carriedDebtRub > 0 {
                Divider().background(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                carriedDebtBlock(details)
            }

            if let comment = latestComment(details), !comment.isEmpty {
                Divider().background(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Комментарий")
                        .font(.system(size: 9, weight: .medium))
                        .tracking(0.54)
                        .textCase(.uppercase)
                        .foregroundStyle(AppDesign.subtleText)
                    Text(comment)
                        .font(.system(size: 12, weight: .medium))
                        .lineSpacing(4)
                        .foregroundStyle(AppDesign.titleText)
                }
            }
        }
        .padding(isActive ? EdgeInsets(top: 21, leading: 23, bottom: 21, trailing: 23) : EdgeInsets(top: 20, leading: 20, bottom: 20, trailing: 20))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255))
        .overlay {
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255), lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08), radius: 15, x: 0, y: 20)
    }

    private func clientBikeAvatar(_ details: AdminClientDetailsResponse, size: CGFloat, cornerRadius: CGFloat) -> some View {
        BikePhotoView(source: details.bikeAvatarUrl) {
            PlaceholderBikeAvatar(cornerRadius: cornerRadius)
        }
        .frame(width: size, height: size)
        .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(AppDesign.success, lineWidth: 3)
        }
    }

    private func statusPill(title: String, color: Color) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Color.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color)
            .clipShape(Capsule())
    }

    private func financeMetrics(_ details: AdminClientDetailsResponse) -> some View {
        HStack {
            clientMetric(title: "Оплачено", value: "+\(formattedRub(details.totalPaidRub))", color: AppDesign.success)
            Spacer()
            clientMetric(
                title: "Долг",
                value: formattedRub(details.debtRub),
                color: details.debtRub > 0 ? AppDesign.danger : AppDesign.titleText
            )
            Spacer()
            clientMetric(title: "Коррект.", value: formattedRub(details.totalAdjustmentRub), color: AppDesign.titleText)
        }
    }

    /// Блок перенесённого долга в карточке клиента (показывается только при carriedDebtRub > 0).
    /// Сумма как клиентский долг — красным. Две admin-операции:
    /// `payment` (зелёный CTA — наличный/безналичный приём оплаты)
    /// и `writeoff` (вспомогательный bordered — списание без денег).
    /// Колбэк `onAdjustDebtTap` уже есть в Sheet, но это про обычный долг;
    /// для carriedDebt используется отдельный `onOpenCarriedDebtSheet`.
    private func carriedDebtBlock(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Перенесённый долг")
                        .font(.system(size: 9, weight: .medium))
                        .tracking(0.36)
                        .textCase(.uppercase)
                        .foregroundStyle(AppDesign.subtleText)
                    Text(formattedRub(details.carriedDebtRub))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(AppDesign.danger)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }

            HStack(spacing: 8) {
                Button {
                    onOpenCarriedDebtSheet(details, .payment)
                } label: {
                    Text("Принять оплату")
                        .font(.system(size: 12, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppDesign.accent)

                Button {
                    onOpenCarriedDebtSheet(details, .writeoff)
                } label: {
                    Text("Списать")
                        .font(.system(size: 12, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.bordered)
                .tint(AppDesign.subtleText)
            }
        }
    }

    private func clientMetric(title: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .tracking(0.36)
                .textCase(.uppercase)
                .foregroundStyle(AppDesign.subtleText)
            Text(value)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(color)
                .lineLimit(1)
        }
    }

    private func profileBlock(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            detailsSectionTitle("Профиль")
            readonlyInput(label: "ФИО", value: details.fullName)
            readonlyInput(label: "Адрес", value: details.address)
            readonlyInput(label: "Паспорт", value: details.passportData)
            ForEach(details.phones) { phone in
                readonlyInput(label: phone.label, value: phone.number)
            }
        }
    }

    private func readonlyInput(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(AppDesign.subtleText)
            Text(value.isEmpty ? "—" : value)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(AppDesign.titleText)
                .lineLimit(1)
        }
        .padding(.horizontal, 19)
        .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(AppDesign.accent, lineWidth: 1)
        }
    }

    private func rentalHistoryBlock(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            detailsSectionTitle("История аренд")
            if details.rentals.isEmpty {
                Text("История аренд пока пустая")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(AppDesign.subtleText)
                    .padding(.top, 4)
            } else {
                VStack(spacing: 8) {
                    ForEach(details.rentals) { rental in
                        rentalHistoryRow(details: details, rental: rental)
                    }
                }
            }
        }
    }

    private func rentalHistoryRow(details: AdminClientDetailsResponse, rental: AdminRentalHistoryItem) -> some View {
        Button {
            rentalDetailsContext = ClientRentalDetailsContext(
                clientId: details.clientId,
                rentalId: rental.id,
                completedAtFallback: rental.periodEnd
            )
            onOpenRental(details.clientId, rental.id, rental.periodEnd)
            onRequestOpenRentalDetails(rental.id)
        } label: {
            HStack(spacing: 12) {
                historyAvatar(rental)

                VStack(alignment: .leading, spacing: 3) {
                    Text(prettyPeriod(rental))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(AppDesign.titleText)
                        .lineLimit(1)
                    Text(rental.bikeModel)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(AppDesign.subtleText)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                Text(historyAmountText(rental))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(rental.debtRub > 0 ? AppDesign.danger : AppDesign.success)
                    .lineLimit(1)

                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(AppDesign.subtleText)
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity)
            .background(Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255))
            .overlay {
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .stroke(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255), lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
            .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08), radius: 15, x: 0, y: 20)
        }
        .buttonStyle(.plain)
    }

    private func historyAvatar(_ rental: AdminRentalHistoryItem) -> some View {
        BikePhotoView(source: rental.bikeAvatarUrl) {
            PlaceholderBikeAvatar(cornerRadius: 10)
        }
        .frame(width: 36, height: 36)
        .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func detailsSectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(AppDesign.subtleText)
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

    private func hasOpenRental(_ details: AdminClientDetailsResponse) -> Bool {
        details.rentals.contains { rental in
            (rental.periodEnd ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    private func latestComment(_ details: AdminClientDetailsResponse) -> String? {
        details.rentals
            .compactMap { $0.comment?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }

    private func historyAmountText(_ rental: AdminRentalHistoryItem) -> String {
        if rental.debtRub > 0 {
            return "- \(formattedRub(rental.debtRub))"
        }
        return "+\(formattedRub(rental.totalPaidRub))"
    }

    private func prettyPeriod(_ rental: AdminRentalHistoryItem) -> String {
        let start = shortRuDate(rental.periodStart)
        let end = rental.periodEnd
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap { $0.isEmpty ? nil : fullRuDate($0) } ?? "н.в."
        return "\(start) – \(end)"
    }

    private func shortRuDate(_ value: String) -> String {
        guard let date = DateFormatter.apiDate.date(from: value) else { return value }
        return Self.shortRuDateFormatter.string(from: date)
    }

    private func fullRuDate(_ value: String) -> String {
        guard let date = DateFormatter.apiDate.date(from: value) else { return value }
        return Self.fullRuDateFormatter.string(from: date)
    }

    private func formattedRub(_ amount: Int) -> String {
        let formatted = Self.rubFormatter.string(from: NSNumber(value: abs(amount))) ?? "\(abs(amount))"
        return "\(formatted) ₽"
    }

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.usesGroupingSeparator = true
        formatter.maximumFractionDigits = 0
        return formatter
    }()

    private static let shortRuDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "dd MMM"
        return formatter
    }()

    private static let fullRuDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "dd MMM yyyy"
        return formatter
    }()

    private func header(_ details: AdminClientDetailsResponse) -> some View {
        HStack(spacing: 12) {
            BikePhotoView(source: details.bikeAvatarUrl) {
                Image(systemName: "bicycle")
                    .resizable()
                    .scaledToFit()
                    .padding(14)
                    .foregroundStyle(AppDesign.iconSoft)
            }
            .frame(width: 64, height: 64)
            .background(AppDesign.surfaceBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(details.fullName)
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Text(details.bikeModel)
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            }
            Spacer()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func profileSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Профиль")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Spacer()
                Button("Редактировать") {
                    isProfileEditorPresented = true
                }
                .buttonStyle(.bordered)
            }
            detailRow("Адрес", details.address)
            detailRow("Паспорт", details.passportData)
            detailRow("Дата старта аренды", details.rentalStart)
            detailRow("Оплачено до", details.paidUntil)

            if !details.phones.isEmpty {
                Text("Телефоны")
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 4)
                ForEach(details.phones) { phone in
                    detailRow(phone.label, phone.number)
                }
            }

            if details.rentals.isEmpty {
                Button("Удалить клиента", role: .destructive) {
                    isDeleteClientConfirmationPresented = true
                }
                .buttonStyle(.bordered)
                .disabled(isOperationInProgress)
                .padding(.top, 4)
                .accessibilityIdentifier("clientDetails.deleteClientButton")
            } else {
                Text("Клиента с историей аренд нельзя удалить, профиль можно только редактировать.")
                    .font(.caption)
                    .foregroundStyle(AppDesign.subtleText)
                    .padding(.top, 4)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .confirmationDialog(
            "Удалить клиента?",
            isPresented: $isDeleteClientConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                onDeleteClient(details.clientId)
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Клиент без истории аренд будет удален из каталога.")
        }
    }

    private func financialSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Финансы")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)
            detailRow("Тариф за неделю", "\(details.weeklyRateRub) ₽")
            detailRow("Всего оплачено", "\(details.totalPaidRub) ₽")
            detailRow("Текущий долг", "\(details.debtRub) ₽", color: details.debtRub > 0 ? AppDesign.danger : AppDesign.titleText)
            detailRow("Суммарная корректировка", "\(details.totalAdjustmentRub) ₽")
            // Перенесённый долг показываем только если он есть. Это редкое состояние
            // (только после удаления lifecycle-аренды с непогашенным долгом),
            // и пустая строка в обычной карточке клиента не нужна.
            if details.carriedDebtRub > 0 {
                detailRow(
                    "Перенесённый долг",
                    "\(details.carriedDebtRub) ₽",
                    color: AppDesign.danger
                )
            }

            Button("Скорректировать долг") {
                onAdjustDebtTap(details)
            }
            .buttonStyle(.borderedProminent)
            .tint(details.debtRub > 0 ? AppDesign.danger : AppDesign.accent)
            .padding(.top, 4)

            if details.carriedDebtRub > 0 {
                // Две admin-операции над перенесённым долгом
                // (docs/14_rental_lifecycle.md §7, docs/04_api_draft.md
                //  «Admin: carried debt operations»). Принять оплату — приоритетный
                // зелёный CTA, Списать — вспомогательный bordered.
                HStack(spacing: 8) {
                    Button("Принять оплату") {
                        onOpenCarriedDebtSheet(details, .payment)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppDesign.accent)

                    Button("Списать") {
                        onOpenCarriedDebtSheet(details, .writeoff)
                    }
                    .buttonStyle(.bordered)
                    .tint(AppDesign.subtleText)
                }
                .padding(.top, 2)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func rentalsSection(_ details: AdminClientDetailsResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Аренды")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Spacer()
                Button("Добавить аренду") {
                    isCreateRentalPresented = true
                }
                .buttonStyle(.bordered)
                .font(.subheadline)
                .accessibilityIdentifier("clientDetails.addRentalButton")
            }

            if details.rentals.isEmpty {
                Text("История аренд пока пустая")
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            } else {
                ForEach(details.rentals) { rental in
                    RentalHistoryCard(
                        clientId: details.clientId,
                        rental: rental,
                        bikes: bikes,
                        onOpenVideo: {
                            openOptionalURL(rental.videoUrl)
                        },
                        onOpenContract: {
                            openOptionalURL(rental.contractUrl)
                        },
                        onSaveComment: { clientId, rentalId, comment in
                            onSaveRentalComment(clientId, rentalId, comment)
                        },
                        onSaveLinks: { clientId, rentalId, videoUrl, contractUrl in
                            onSaveRentalLinks(clientId, rentalId, videoUrl, contractUrl)
                        },
                        onSaveRental: { payload in
                            onUpdateRental(payload)
                        },
                        onDeleteRental: { clientId, rentalId in
                            onDeleteRental(clientId, rentalId)
                        },
                        onOpenRental: { clientId, rentalId, completedAt in
                            onOpenRental(clientId, rentalId, completedAt)
                        }
                    )
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func detailRow(_ title: String, _ value: String, color: Color = AppDesign.titleText) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppDesign.subtleText)
            Text(value.isEmpty ? "-" : value)
                .font(.subheadline)
                .foregroundStyle(color)
        }
    }

    private func openOptionalURL(_ raw: String?) {
        guard let raw, let url = URL(string: raw), !raw.isEmpty else {
            return
        }
        openURL(url)
    }
}
