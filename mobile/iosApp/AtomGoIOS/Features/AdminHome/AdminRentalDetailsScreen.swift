import SwiftUI
import PhotosUI
import UIKit

struct AdminRentalDetailsScreen: View {
    let details: AdminRentalDetailsResponse?
    let fallbackSummary: AdminClientSummaryResponse?
    let completedAtFallback: String?
    let clients: [AdminClientSummaryResponse]
    let isLoading: Bool
    let errorMessage: String?
    let isOperationInProgress: Bool
    let onClose: () -> Void
    let onRetry: () -> Void
    let onOpenClientCard: () -> Void
    let onAdjustDebt: (_ clientId: String, _ clientName: String, _ currentDebtRub: Int) -> Void
    let onFinishRental: (_ clientId: String, _ rentalId: String) -> Void
    let onStartRental: (CreateRentalPayload) -> Void
    let onDeleteRental: (_ clientId: String, _ rentalId: String) -> Void

    @State private var isDeleteDialogPresented = false
    @State private var selectedStartClientId: String?
    @State private var isClientPickerPresented = false
    @State private var editableRentalLogin = ""
    @State private var editableRentalPassword = ""
    @State private var didInitializeCredentialDrafts = false
    @State private var startValidationMessage: String?
    @State private var copyToastMessage: String?

    var body: some View {
        ZStack(alignment: .top) {
            AppDesign.pageBackground.ignoresSafeArea()

            if isLoading && details == nil {
                ProgressView("Загружаем аренду...")
                    .tint(AppDesign.accent)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let errorMessage, details == nil {
                VStack(spacing: 12) {
                    Text("Не удалось загрузить аренду")
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    Text(errorMessage)
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(AppDesign.subtleText)
                    Button("Повторить", action: onRetry)
                        .buttonStyle(.borderedProminent)
                }
                .padding(22)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    topBar
                        .padding(.top, 8)

                    rentalCard

                    if displayPolicy.showsJournalHistory {
                        Text("ЖУРНАЛ")
                            .font(.system(size: 11, weight: .bold))
                            .tracking(0.88)
                            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                            .padding(.horizontal, 1)
                            .padding(.top, 8)

                        ScrollView(showsIndicators: false) {
                            VStack(spacing: 8) {
                                ForEach(journalRows) { row in
                                    journalRow(row)
                                }
                            }
                            .padding(.bottom, 8)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    }
                }
                .padding(.horizontal, 23)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .safeAreaInset(edge: .bottom, spacing: 8) {
                    bottomActions
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 8)
                        .background(AppDesign.pageBackground)
                }
            }
        }
        .confirmationDialog("Удалить аренду?", isPresented: $isDeleteDialogPresented, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) {
                // Для удаления нужен только rentalId — backend работает на уровне
                // lifecycle-аренды. В IN_STOCK состоянии clientId возвращает nil
                // (нет активного клиента), поэтому раньше guard валился и кнопка
                // молча не работала. Теперь пропускаем без клиента — viewModel
                // умеет вызывать refreshAfterMutation без openDetailsFor.
                guard let rentalId else { return }
                onDeleteRental(clientId ?? "", rentalId)
            }
            Button("Отмена", role: .cancel) {}
        }
        .fullScreenCover(isPresented: $isClientPickerPresented) {
            RentalStartClientPickerSheet(
                clients: availableStartClients,
                selectedClientId: $selectedStartClientId,
                onClose: {
                    isClientPickerPresented = false
                },
                onConfirm: {
                    isClientPickerPresented = false
                }
            )
        }
        .onChange(of: rentalId ?? "") { _ in
            selectedStartClientId = nil
            isClientPickerPresented = false
            editableRentalLogin = normalizedCredential(details?.clientLogin)
            editableRentalPassword = normalizedCredential(details?.clientPassword)
            didInitializeCredentialDrafts = true
            startValidationMessage = nil
        }
        .onChange(of: details?.rentalId ?? "") { _ in
            editableRentalLogin = normalizedCredential(details?.clientLogin)
            editableRentalPassword = normalizedCredential(details?.clientPassword)
            didInitializeCredentialDrafts = true
            startValidationMessage = nil
        }
        // Lifecycle перешёл в IN_STOCK (через `Завершить` или delete активной
        // client_rental) — credentials НАДО СБРОСИТЬ, иначе при «Создать новую!»
        // payload пойдёт с предыдущими login/password.
        //
        // ВАЖНО: реагируем ТОЛЬКО когда смотрим саму lifecycle-аренду, а не
        // её закрытую client_rental из истории. Backend для закрытой
        // client_rental возвращает rentalPipelineStatus родительского
        // lifecycle (может быть "in_stock" если велик сейчас не на руках),
        // и тогда сброс editable* вытер бы исторические credentials в UI.
        // Признак «это закрытая client_rental» — непустой completedAt.
        .onChange(of: (details?.rentalPipelineStatus ?? "")) { newStatus in
            let isInStockNow = newStatus == "in_stock" || newStatus == "mine"
            let isViewingClosedClientRental = !(details?.completedAt?.isEmpty ?? true)
            if isInStockNow && !isViewingClosedClientRental {
                selectedStartClientId = nil
                editableRentalLogin = ""
                editableRentalPassword = ""
                startValidationMessage = nil
            }
        }
        .onAppear {
            guard !didInitializeCredentialDrafts else { return }
            editableRentalLogin = normalizedCredential(details?.clientLogin)
            editableRentalPassword = normalizedCredential(details?.clientPassword)
            didInitializeCredentialDrafts = true
            startValidationMessage = nil
        }
        .onChange(of: selectedStartClientId ?? "") { _ in
            startValidationMessage = nil
        }
        .onChange(of: editableRentalLogin) { _ in
            startValidationMessage = nil
        }
        .onChange(of: editableRentalPassword) { _ in
            startValidationMessage = nil
        }
        .appToast(message: $copyToastMessage, bottomPadding: 86)
    }

    private var topBar: some View {
        HStack {
            iconButton(
                systemName: "chevron.left",
                borderColor: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255),
                iconColor: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255),
                action: onClose
            )

            Spacer()

            Text("Аренда")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))

            Spacer()

            iconButton(
                systemName: "trash",
                borderColor: Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255),
                iconColor: Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255),
                action: { isDeleteDialogPresented = true }
            )
        }
        .frame(height: 45)
    }

    private func iconButton(
        systemName: String,
        borderColor: Color,
        iconColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(borderColor, lineWidth: 1)
                )
                .overlay(
                    Image(systemName: systemName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(iconColor)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
    }

    private var rentalCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                BikePhotoView(source: bikeAvatarUrl) {
                    Image(systemName: "bicycle")
                        .resizable()
                        .scaledToFit()
                        .padding(18)
                        .foregroundStyle(Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255))
                }
                .frame(width: 80, height: 80)
                .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(avatarBorderColor, lineWidth: 3)
                )
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(bikeTitle)
                        .font(.system(size: 16, weight: .bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                        .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                    Text("\(formattedRub(weeklyRateRub))/нед")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    Text(displayPolicy.correctionLineText(formattedAdjustment: formattedRub(totalAdjustmentRub)))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255).opacity(0.5))
                }
                .padding(.top, 16)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 19)
            .padding(.top, 21)
            .padding(.bottom, 14)

            Divider()
                .overlay(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                .padding(.horizontal, 18)

            HStack(alignment: .top, spacing: 0) {
                metricColumn(
                    title: "ОПЛАЧЕНО",
                    value: displayPolicy.metricText(activeValue: "+\(formattedRub(totalPaidRub))"),
                    color: displayPolicy.metricColor(activeColor: Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255))
                )
                Spacer(minLength: 0)
                metricColumn(
                    title: "ДОЛГ",
                    value: displayPolicy.metricText(activeValue: formattedRub(debtRub)),
                    color: displayPolicy.metricColor(activeColor: Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255))
                )
                Spacer(minLength: 0)
                metricColumn(
                    title: "КОРРЕКТ.",
                    value: displayPolicy.metricText(activeValue: formattedRub(totalAdjustmentRub)),
                    color: displayPolicy.metricColor(activeColor: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                )
                Spacer(minLength: 0)
                metricColumn(
                    title: runningRentalIsActive ? "ОПЛАЧ. ДО" : "ЗАВЕРШЕНА",
                    value: displayPolicy.metricText(activeValue: runningRentalIsActive ? paidUntilText : completedAtText),
                    color: displayPolicy.metricColor(activeColor: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                )
            }
            .frame(width: 330, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 8)

            Divider()
                .overlay(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                .padding(.horizontal, 18)

            loginPasswordBlock
                .padding(.horizontal, 19)
                .padding(.top, 9)
                .padding(.bottom, 8)

            Divider()
                .overlay(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                .padding(.horizontal, 18)

            if isInStockState {
                Button {
                    guard !isOperationInProgress else { return }
                    isClientPickerPresented = true
                } label: {
                    startClientSelectorControl
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            } else if rentalIsActive {
                Button(action: onOpenClientCard) {
                    HStack {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("АРЕНДАТОР")
                                .font(.system(size: 10, weight: .bold))
                                .tracking(0.6)
                                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                            Text(clientName)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                                .lineLimit(1)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    }
                    .padding(.horizontal, 19)
                    .frame(height: 68, alignment: .center)
                }
                .buttonStyle(.plain)
            } else {
                Button(action: onOpenClientCard) {
                    HStack {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("АРЕНДАТОР")
                                .font(.system(size: 10, weight: .bold))
                                .tracking(0.6)
                                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                            Text(clientName)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                                .lineLimit(1)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    }
                    .padding(.horizontal, 19)
                    .frame(height: 68, alignment: .center)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08), radius: 15, x: 0, y: 20)
        .frame(height: 320)
    }

    private func metricColumn(title: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .tracking(0.36)
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            Text(value)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    private var loginPasswordBlock: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                credentialField(
                    title: "ЛОГИН",
                    text: $editableRentalLogin,
                    // Редактируется ТОЛЬКО в lifecycle-аренде в статусе IN_STOCK
                    // (черновик credentials для следующей client_rental).
                    // В активной аренде и в закрытой client_rental поля
                    // read-only — показывают серверный login/password.
                    // Раньше тут было `!runningRentalIsActive`, что давало
                    // editable также для закрытой client_rental и сбрасывало
                    // её серверные credentials в UI до прочерков.
                    isEditable: isInStockState,
                    readOnlyText: displayPolicy.readOnlyCredentialText(
                        serverValue: details?.clientLogin ?? fallbackSummary?.clientLogin,
                        draftValue: editableRentalLogin
                    ),
                    accessibilityIdentifier: "rentalDetails.loginField"
                )
                credentialField(
                    title: "ПАРОЛЬ",
                    text: $editableRentalPassword,
                    isEditable: isInStockState,
                    readOnlyText: displayPolicy.readOnlyCredentialText(
                        serverValue: details?.clientPassword,
                        draftValue: editableRentalPassword
                    ),
                    accessibilityIdentifier: "rentalDetails.passwordField"
                )
            }
            HStack(spacing: 8) {
                // Согласно docs/14_rental_lifecycle.md §4, кнопка «Сгенерировать»
                // живёт только в lifecycle-аренде в статусе IN_STOCK, где админ
                // готовит черновик credentials под следующую client_rental.
                // В активной аренде credentials редактировать нельзя, в закрытой —
                // тем более; там кнопки быть не должно.
                if isInStockState {
                    Button(action: generateCredentials) {
                        Text("Сгенерировать")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 110, height: 47)
                            .background(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("rentalDetails.generateCredentialsButton")
                }

                Button(action: copyCredentialsToClipboard) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                            .fill(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
                        Image("copy icon")
                            .renderingMode(.original)
                    }
                    .frame(width: 47, height: 47)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: 67, alignment: .center)
    }

    private func credentialField(
        title: String,
        text: Binding<String>,
        isEditable: Bool,
        readOnlyText: String? = nil,
        accessibilityIdentifier: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            if isEditable {
                TextField(
                    "",
                    text: text,
                    prompt: Text("—")
                        .foregroundColor(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                .frame(width: 150, height: 13, alignment: .leading)
                .accessibilityIdentifier(accessibilityIdentifier)
            } else {
                let value = readOnlyText ?? text.wrappedValue
                Text(value.isEmpty ? "—" : value)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                    .lineLimit(1)
                    .frame(width: 150, height: 13, alignment: .leading)
            }
        }
    }

    private func journalRow(_ row: AdminRentalJournalEntry) -> some View {
        HStack(spacing: 12) {
            Text(row.type.uppercased())
                .font(.system(size: 10, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                .frame(width: 90, alignment: .leading)

            Text(signedRub(row.amountRub))
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(journalAmountColor(row.amountRub))
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(journalDateLabel(row.createdAt))
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
        }
        .padding(.horizontal, 15)
        .frame(height: 35)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var bottomActions: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let startValidationMessage, !startValidationMessage.isEmpty {
                Text(startValidationMessage)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255))
                    .padding(.horizontal, 4)
            }

            HStack(spacing: 8) {
                Button {
                    guard let clientId else { return }
                    onAdjustDebt(clientId, clientName, debtRub)
                } label: {
                    Text("+ Корректировка")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(
                            displayPolicy.adjustmentButtonEnabled
                                ? Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
                                : Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255).opacity(0.45)
                        )
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(
                                    displayPolicy.adjustmentButtonEnabled
                                        ? Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
                                        : Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255).opacity(0.35),
                                    lineWidth: 1
                                )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(!displayPolicy.adjustmentButtonEnabled || clientId == nil || isOperationInProgress)
                .opacity((displayPolicy.adjustmentButtonEnabled && clientId != nil) ? 1 : 0.9)

                if runningRentalIsActive {
                    Button {
                        guard let clientId, let rentalId else { return }
                        onFinishRental(clientId, rentalId)
                    } label: {
                        Text("Завершить")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255))
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(clientId == nil || rentalId == nil || isOperationInProgress)
                    .opacity((clientId == nil || rentalId == nil) ? 0.6 : 1)
                } else {
                    Button {
                        startRentalForSelectedClient()
                    } label: {
                        Text(startButtonTitle)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(startButtonColor)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isOperationInProgress)
                    .opacity(canStartRental ? 1 : 0.75)
                }
            }
        }
        .frame(width: max(UIScreen.main.bounds.width - 16, 0))
    }

    private var clientId: String? {
        if isInStockState { return nil }
        return details?.clientId ?? fallbackSummary?.clientId
    }

    private var rentalId: String? {
        details?.rentalId ?? fallbackSummary?.rentalId
    }

    private var clientName: String {
        if isInStockState { return "Клиент не выбран" }
        return details?.clientFullName ?? fallbackSummary?.fullName ?? "Клиент"
    }

    private var bikeTitle: String {
        if let bikeModel = details?.bikeModel, !bikeModel.isEmpty {
            return bikeModel
        }
        return fallbackSummary?.bikeModel ?? "-"
    }

    private var bikeAvatarUrl: String? {
        details?.bikeAvatarUrl ?? fallbackSummary?.bikeAvatarUrl
    }

    private var totalPaidRub: Int { details?.totalPaidRub ?? 0 }
    private var debtRub: Int { details?.debtRub ?? fallbackSummary?.debtRub ?? 0 }
    private var totalAdjustmentRub: Int { details?.totalAdjustmentRub ?? fallbackSummary?.totalAdjustmentRub ?? 0 }
    private var weeklyRateRub: Int { details?.weeklyRateRub ?? 0 }
    private var paidUntilText: String { prettyDate(details?.paidUntil) }
    private var completedAtText: String { prettyDate(details?.completedAt ?? completedAtFallback) }
    private var rentalIsActive: Bool { details?.rentalIsActive ?? fallbackSummary?.rentalIsActive ?? false }
    private var runningRentalIsActive: Bool { rentalIsActive && !isInStockState }
    /// True ⇔ открыта именно lifecycle-аренда в статусе IN_STOCK (велик у админа,
    /// идёт подготовка credentials под следующий цикл). Закрытая client_rental
    /// показывается как историческая запись и НЕ считается in_stock, даже если
    /// её lifecycle сейчас в IN_STOCK (см. docs/14_rental_lifecycle.md §2).
    private var isInStockState: Bool {
        // Признак «это закрытая client_rental» — есть completedAt либо в данных
        // ответа, либо в контексте, переданном при открытии из истории.
        let hasCompletedAt = !(details?.completedAt?.isEmpty ?? true) || completedAtFallback != nil
        if hasCompletedAt {
            return false
        }

        let status = (details?.rentalPipelineStatus ?? fallbackSummary?.rentalPipelineStatus ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if status == "in_stock" || status == "mine" {
            return true
        }

        // Свежеоткрытый IN_STOCK lifecycle может прийти с пустым статусом до
        // догрузки деталей — fallback по rentalIsActive.
        return !rentalIsActive
    }
    private var bikeId: String? { details?.bikeId }
    private var displayPolicy: RentalDetailsDisplayPolicy {
        .init(rentalIsActive: rentalIsActive, isInStockState: isInStockState)
    }

    private var availableStartClients: [AdminClientSummaryResponse] {
        clients.availableForRentalStart()
    }

    private var selectedStartClient: AdminClientSummaryResponse? {
        guard let selectedStartClientId else { return nil }
        return availableStartClients.first(where: { $0.clientId == selectedStartClientId })
    }

    private var selectedStartClientName: String? {
        selectedStartClient?.fullName
    }

    private var startClientSelectorControl: some View {
        let hasSelectedClient = selectedStartClientId != nil
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text("КЛИЕНТ")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(0.8)
                    .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    .textCase(.uppercase)

                Text(selectedStartClientName ?? "выбрать клиента")
                    .font(.system(size: 13, weight: hasSelectedClient ? .bold : .medium))
                    .foregroundStyle(
                        hasSelectedClient
                            ? Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
                            : Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
                    )
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            }
            .frame(width: 28, height: 28)
        }
        .padding(.leading, 19)
        .padding(.trailing, 15)
        .padding(.vertical, 15)
        .frame(height: 58)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255), lineWidth: 1)
        )
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(
                    hasSelectedClient
                        ? Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
                        : Color(red: 211 / 255, green: 215 / 255, blue: 221 / 255)
                )
                .frame(width: 4)
                .padding(.vertical, 8)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
    }

    @ViewBuilder
    private var startClientPickerPopover: some View {
        if #available(iOS 16.4, *) {
            startClientPickerPopoverBody
                .presentationCompactAdaptation(.popover)
        } else {
            startClientPickerPopoverBody
        }
    }

    private var startClientPickerPopoverBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            if availableStartClients.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "person.slash")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    Text("Нет свободных клиентов")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .frame(width: 300)
            } else {
                ForEach(availableStartClients) { client in
                    startClientPickerRow(client)
                }
            }
        }
        .padding(7)
        .frame(width: 340, alignment: .leading)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.22), radius: 15, x: 0, y: 16)
    }

    private func startClientPickerRow(_ client: AdminClientSummaryResponse) -> some View {
        let isSelected = client.clientId == selectedStartClientId

        return Button {
            selectedStartClientId = client.clientId
            isClientPickerPresented = false
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255), lineWidth: 3)
                    )
                    .overlay(
                        Image(systemName: "person.fill")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255))
                    )
                    .frame(width: 34, height: 34)

                VStack(alignment: .leading, spacing: 2) {
                    Text(client.fullName)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                        .lineLimit(1)

                    Text(client.clientLogin?.isEmpty == false ? (client.clientLogin ?? "") : "Свободный клиент")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color(red: 240 / 255, green: 242 / 255, blue: 245 / 255) : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var canStartRental: Bool {
        isInStockState &&
            selectedStartClient != nil &&
            !normalizedCredential(editableRentalLogin).isEmpty &&
            !normalizedCredential(editableRentalPassword).isEmpty
    }

    private var startButtonTitle: String {
        "Начать!"
    }

    private var startButtonColor: Color {
        canStartRental
            ? Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255)
            : Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255).opacity(0.65)
    }

    private var avatarBorderColor: Color {
        // Закрытая клиентская аренда — историческая запись без активной
        // семантики lifecycle-статуса, рамка нейтральная серая.
        // См. docs/14_rental_lifecycle.md §2 — статусы long_term/soon_return/in_stock
        // относятся к lifecycle-аренде, а не к закрытой client_rental.
        let isCompletedClientRental = !(details?.completedAt?.isEmpty ?? true) || completedAtFallback != nil
        if isCompletedClientRental {
            return Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
        }
        if let details {
            if !details.rentalIsActive {
                return Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255)
            }
            if details.rentalPipelineStatus == "soon_return" {
                return Color(red: 255 / 255, green: 204 / 255, blue: 0)
            }
            return Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
        }
        return Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    }

    private func startRentalForSelectedClient() {
        guard isInStockState else { return }
        guard let selectedStartClient else {
            startValidationMessage = "Выберите клиента"
            return
        }

        let login = normalizedCredential(editableRentalLogin)
        let password = normalizedCredential(editableRentalPassword)
        if login.isEmpty, password.isEmpty {
            startValidationMessage = "Заполните логин и пароль"
            return
        }
        if login.isEmpty {
            startValidationMessage = "Заполните логин"
            return
        }
        if password.isEmpty {
            startValidationMessage = "Сгенерируйте новый пароль"
            return
        }

        startValidationMessage = nil
        let payload = CreateRentalPayload(
            clientId: selectedStartClient.clientId,
            bikeId: bikeId ?? "",
            login: login,
            password: password,
            periodStart: DateFormatter.apiDate.string(from: Date()),
            periodEnd: nil,
            videoUrl: nil,
            contractUrl: nil,
            comment: nil
        )
        onStartRental(payload)
    }

    /// Генерация черновика credentials под следующую client_rental.
    /// Согласно docs/14_rental_lifecycle.md §4 кнопка генерирует И логин,
    /// И пароль. Логин — короткий человекочитаемый суффикс (admin может
    /// затем отредактировать), пароль — 12 символов из безопасного
    /// алфавита (без однозначных глифов).
    private func generateCredentials() {
        editableRentalLogin = makeRandomLogin()
        editableRentalPassword = makeRandomPassword()
        startValidationMessage = nil
    }

    private func makeRandomLogin() -> String {
        // 6 цифр обеспечивают 1_000_000 вариантов — достаточно как черновик
        // для практически любого числа аренд; уникальность проверяет backend.
        let digits = "0123456789"
        let suffix = String((0..<6).map { _ in digits.randomElement()! })
        return "user\(suffix)"
    }

    private func makeRandomPassword() -> String {
        // Алфавит без I, O, l, 1, 0 — чтобы избежать спорных символов
        // при чтении или озвучивании. Длина 12 даёт примерно ~71 бит энтропии.
        let alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return String((0..<12).map { _ in alphabet.randomElement()! })
    }

    private func copyCredentialsToClipboard() {
        let draftLogin = normalizedCredential(editableRentalLogin)
        let draftPassword = normalizedCredential(editableRentalPassword)
        let serverLogin = normalizedCredential(details?.clientLogin ?? fallbackSummary?.clientLogin)
        let serverPassword = normalizedCredential(details?.clientPassword)

        let login = draftLogin.isEmpty ? serverLogin : draftLogin
        let password = draftPassword.isEmpty ? serverPassword : draftPassword

        if login.isEmpty, password.isEmpty {
            startValidationMessage = "заполните логин и пароль"
            return
        }
        if login.isEmpty {
            startValidationMessage = "заполните логин"
            return
        }
        if password.isEmpty {
            startValidationMessage = "заполните пароль"
            return
        }

        UIPasteboard.general.string = "Логин: \(login)\nПароль: \(password)"
        startValidationMessage = nil
        copyToastMessage = "скопированно"
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            copyToastMessage = nil
        }
    }

    private func normalizedCredential(_ value: String?) -> String {
        value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private var journalRows: [AdminRentalJournalEntry] {
        guard displayPolicy.showsJournalHistory, let details else { return [] }
        return details.journalEntries
    }

    private func journalAmountColor(_ amount: Int) -> Color {
        if amount > 0 {
            return Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255)
        }
        if amount < 0 {
            return Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255)
        }
        return Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    }

    private func prettyDate(_ value: String?) -> String {
        guard let value, let date = DateFormatter.apiDate.date(from: value) else { return "—" }
        return Self.prettyRuDateFormatter.string(from: date)
    }

    private func journalDateLabel(_ value: String) -> String {
        let parsedDate = Self.isoDateFormatterWithFractional.date(from: value)
            ?? Self.isoDateFormatter.date(from: value)
        guard let date = parsedDate else {
            return "—"
        }
        return Self.journalDateFormatter.string(from: date)
    }

    private func signedRub(_ amount: Int) -> String {
        let sign = amount > 0 ? "+" : ""
        return "\(sign)\(formattedRub(amount))"
    }

    private func formattedRub(_ amount: Int) -> String {
        let absAmount = Swift.abs(amount)
        let formatted = Self.rubFormatter.string(from: NSNumber(value: absAmount)) ?? "\(absAmount)"
        if amount < 0 {
            return "−\(formatted.replacingOccurrences(of: "\u{00A0}", with: " ")) ₽"
        }
        return "\(formatted.replacingOccurrences(of: "\u{00A0}", with: " ")) ₽"
    }

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()

    private static let journalDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "dd MMM"
        return formatter
    }()

    private static let prettyRuDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "dd MMM yyyy"
        return formatter
    }()

    private static let isoDateFormatterWithFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let isoDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}
