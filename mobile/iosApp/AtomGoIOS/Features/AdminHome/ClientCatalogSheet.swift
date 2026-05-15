import SwiftUI
import PhotosUI
import UIKit

struct ClientCatalogSheet: View {
    let clients: [AdminClientSummaryResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    var showsCloseButton: Bool = true
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload, @escaping () -> Void) -> Void
    let onOpenClient: (AdminClientSummaryResponse) -> Void

    @Environment(\.openURL) private var openURL
    @State private var isCreateClientPresented = false
    @State private var searchText = ""
    @State private var selectedFilter: ClientCatalogFilter = .all
    @State private var initialCardsTopY: CGFloat?
    @State private var areFiltersInteractive = true
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalInset: CGFloat = 8
    private let topBarHeight: CGFloat = 62
    private let searchTopPadding: CGFloat = 6
    private let searchHeight: CGFloat = 46
    private let chipsTopGap: CGFloat = 10
    private let chipsHeight: CGFloat = 36
    private let tabBarHeight: CGFloat = 76
    private let callButtonGreen = Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)

    private var searchTop: CGFloat { topBarHeight + searchTopPadding }
    private var searchMaskHeight: CGFloat { searchTop + searchHeight / 2 }
    private var chipsTop: CGFloat { searchTop + searchHeight + chipsTopGap }
    private var chipsBottom: CGFloat { chipsTop + chipsHeight }
    private var cardsInitialTop: CGFloat { chipsBottom - 4 }

    var body: some View {
        ZStack(alignment: .top) {
            athensGray.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    Color.clear
                        .frame(height: cardsInitialTop)
                        .background {
                            GeometryReader { proxy in
                                Color.clear.preference(
                                    key: AdminCardsTopKey.self,
                                    value: proxy.frame(in: .named("clientCatalogPipeline")).maxY
                                )
                            }
                        }
                        .allowsHitTesting(false)

                    VStack(alignment: .leading, spacing: 0) {
                        if visibleClients.isEmpty {
                            emptyState
                                .padding(.top, 14)
                        } else {
                            LazyVStack(spacing: 0) {
                                ForEach(Array(visibleClients.enumerated()), id: \.element.id) { index, client in
                                    clientRow(client)
                                    if index < visibleClients.count - 1 {
                                        Divider()
                                            .overlay(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                                    }
                                }
                            }
                            .padding(.horizontal, 15)
                            .padding(.vertical, 5)
                            .background(Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255))
                            .overlay(
                                RoundedRectangle(cornerRadius: 15, style: .continuous)
                                    .stroke(AppDesign.accent, lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                            .shadow(
                                color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08),
                                radius: 15,
                                x: 0,
                                y: 20
                            )
                        }
                    }
                    .padding(.top, 1)
                    .background(AppDesign.pageBackground)
                }
                .padding(.horizontal, horizontalInset)
                .padding(.bottom, showsCloseButton ? 18 : tabBarHeight + 30)
            }
            .mask(alignment: .top) {
                VStack(spacing: 0) {
                    Color.clear
                        .frame(height: searchMaskHeight)
                    Color.white
                }
            }
            .onPreferenceChange(AdminCardsTopKey.self) { cardsTopY in
                guard cardsTopY.isFinite else { return }
                if initialCardsTopY == nil || cardsTopY > (initialCardsTopY ?? cardsTopY) {
                    initialCardsTopY = cardsTopY
                    if !areFiltersInteractive {
                        areFiltersInteractive = true
                    }
                    return
                }
                guard let initialCardsTopY else { return }
                let overlapDistance = max(10, initialCardsTopY - chipsBottom + 2)
                let scrollDistance = max(0, initialCardsTopY - cardsTopY)
                let nextInteractive = scrollDistance < overlapDistance
                if nextInteractive != areFiltersInteractive {
                    areFiltersInteractive = nextInteractive
                }
            }
            .zIndex(2)

            clientChipRows(clients: clients)
                .padding(.horizontal, horizontalInset)
                .frame(height: chipsHeight, alignment: .topLeading)
                .offset(y: chipsTop)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .zIndex(1)

            clientFilterHitLayer
                .padding(.horizontal, horizontalInset)
                .frame(height: chipsHeight, alignment: .topLeading)
                .offset(y: chipsTop)
                .allowsHitTesting(areFiltersInteractive)
                .zIndex(3.5)

            Rectangle()
                .fill(AppDesign.pageBackground)
                .frame(height: searchMaskHeight)
                .ignoresSafeArea(edges: .top)
                .zIndex(3)

            VStack(spacing: 0) {
                clientTopBar
                    .frame(height: topBarHeight)
                searchField
                    .padding(.horizontal, horizontalInset)
                    .padding(.top, searchTopPadding)
                Spacer(minLength: 0)
            }
            .zIndex(4)
        }
        .coordinateSpace(name: "clientCatalogPipeline")
        .fullScreenCover(isPresented: $isCreateClientPresented) {
            CreateClientSheet(
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { isCreateClientPresented = false },
                onCreate: { payload in
                    onCreate(payload) {
                        isCreateClientPresented = false
                    }
                }
            )
        }
        .onChange(of: apiErrorMessage) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private var visibleClients: [AdminClientSummaryResponse] {
        let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let searched = clients.filter { client in
            guard !normalizedQuery.isEmpty else { return true }
            return client.fullName.lowercased().contains(normalizedQuery)
                || client.bikeModel.lowercased().contains(normalizedQuery)
                || (client.clientLogin?.lowercased().contains(normalizedQuery) ?? false)
        }

        switch selectedFilter {
        case .all:
            return searched
        case .debtors:
            return searched.filter(\.isDebtor)
        case .active:
            return searched.filter { $0.rentalIsActive }
        }
    }

    private func clientChipRows(clients: [AdminClientSummaryResponse]) -> some View {
        HStack(spacing: 8) {
            clientFilterChip(.all, count: clients.count)
            clientFilterChip(.debtors, count: clients.filter(\.isDebtor).count)
            clientFilterChip(.active, count: clients.filter { $0.rentalIsActive }.count)
        }
    }

    private var clientFilterHitLayer: some View {
        HStack(spacing: 8) {
            clientFilterHitTarget(.all, width: 84)
            clientFilterHitTarget(.debtors, width: 106)
            clientFilterHitTarget(.active, width: 106)
        }
    }

    private func clientFilterHitTarget(_ filter: ClientCatalogFilter, width: CGFloat) -> some View {
        Button {
            selectedFilter = filter
        } label: {
            Color.white.opacity(0.001)
                .frame(width: width, height: 36)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(filter.accessibilityIdentifier)
        .accessibilityValue(selectedFilter == filter ? "selected" : "normal")
    }

    private var clientTopBar: some View {
        HStack {
            if showsCloseButton {
                headerIconButton(
                    systemName: "xmark",
                    accessibilityIdentifier: "clientCatalog.closeButton",
                    action: onCancel
                )
            } else {
                headerIconButton(
                    assetName: "exit",
                    assetSize: 16,
                    accessibilityIdentifier: "clientCatalog.logoutButton",
                    action: onCancel
                )
            }

            Spacer()

            Text("Клиенты")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

            Spacer()

            headerIconButton(
                assetName: "plus",
                assetSize: 16,
                accessibilityIdentifier: "clientCatalog.addClientButton",
                action: { isCreateClientPresented = true }
            )
        }
        .padding(.horizontal, 8)
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(AppDesign.titleText)

            TextField("Поиск: ФИО, телефон, паспорт", text: $searchText)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(AppDesign.titleText)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
        }
        .padding(.horizontal, 15)
        .frame(height: 46)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(AppDesign.accent, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .accessibilityIdentifier("clientCatalog.searchField")
    }

    private func clientFilterChip(_ filter: ClientCatalogFilter, count: Int) -> some View {
        let isSelected = selectedFilter == filter

        return Button {
            selectedFilter = filter
        } label: {
            HStack(spacing: 6) {
                Text(filter.title)
                    .font(.system(size: 12, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .allowsTightening(true)

                Text("\(count)")
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(isSelected ? Color.white.opacity(0.2) : Color.black.opacity(0.08))
                    .clipShape(Capsule())
            }
            .foregroundStyle(isSelected ? Color.white : AppDesign.accent)
            .padding(.horizontal, 15)
            .frame(height: 36)
            .background(isSelected ? AppDesign.accent : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1)
            )
            .clipShape(Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(filter.accessibilityIdentifier)
        .accessibilityValue(isSelected ? "selected" : "normal")
    }

    private func headerIconButton(
        systemName: String,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .overlay(
                    Image(systemName: systemName)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(AppDesign.accent)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func headerIconButton(
        assetName: String,
        assetSize: CGFloat,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .overlay(
                    Image(assetName)
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: assetSize, height: assetSize)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "person.2.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)
            Text("Список клиентов пуст")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)
            Text("Создайте первого клиента внутри этого списка.")
                .font(.subheadline)
                .foregroundStyle(AppDesign.subtleText)
                .multilineTextAlignment(.center)
            Button("Создать клиента") {
                isCreateClientPresented = true
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("clientCatalog.emptyCreateClientButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func clientRow(_ client: AdminClientSummaryResponse) -> some View {
        HStack(spacing: 12) {
            callButton(for: client)

            Button {
                onOpenClient(client)
            } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(client.fullName)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                            .lineLimit(1)
                            .truncationMode(.tail)
                        Text(clientSubtitle(for: client))
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if clientTotalDebtRub(for: client) > 0 {
                        Text(formattedDebtRub(clientTotalDebtRub(for: client)))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255))
                            .fixedSize(horizontal: true, vertical: false)
                    }

                    Image(systemName: "chevron.right")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(Color(red: 167 / 255, green: 167 / 255, blue: 171 / 255))
                }
                .frame(minHeight: 51)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .accessibilityIdentifier("clientCatalog.open.\(client.fullName)")
    }

    private func callButton(for client: AdminClientSummaryResponse) -> some View {
        let telURL = telURLString(for: client.primaryPhone)

        return Button {
            guard let telURL, let url = URL(string: telURL) else { return }
            openURL(url)
        } label: {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(callButtonGreen, lineWidth: 1)
                )
                .overlay(
                    Image(systemName: "phone.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(callButtonGreen)
                )
                .frame(width: 36, height: 36)
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(telURL == nil)
        .opacity(telURL == nil ? 0.45 : 1)
        .accessibilityIdentifier("clientCatalog.call.\(client.fullName)")
    }

    private func telURLString(for rawPhone: String?) -> String? {
        guard let rawPhone else { return nil }
        let trimmed = rawPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        var normalized = ""
        for (index, scalar) in trimmed.unicodeScalars.enumerated() {
            if CharacterSet.decimalDigits.contains(scalar) {
                normalized.unicodeScalars.append(scalar)
            } else if scalar == "+" && index == 0 {
                normalized.unicodeScalars.append(scalar)
            }
        }

        guard !normalized.isEmpty else { return nil }
        return "tel://\(normalized)"
    }

    private func clientSubtitle(for client: AdminClientSummaryResponse) -> String {
        if client.rentalIsActive {
            let model = normalizedBikeModel(client.bikeModel)
            if let paidUntil = shortPaidUntilText(for: client) {
                return model.isEmpty ? "до \(paidUntil)" : "\(model) · до \(paidUntil)"
            }
            return model.isEmpty ? "Активная аренда" : model
        }

        let model = normalizedBikeModel(client.bikeModel)
        if model.isEmpty {
            return "-"
        }
        return model
    }

    private func normalizedBikeModel(_ rawValue: String) -> String {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty || value == "-" {
            return ""
        }
        return value
    }

    private func shortPaidUntilText(for client: AdminClientSummaryResponse) -> String? {
        guard
            let paidUntil = client.paidUntil?.trimmingCharacters(in: .whitespacesAndNewlines),
            !paidUntil.isEmpty,
            let date = Self.apiDateFormatter.date(from: paidUntil)
        else {
            return nil
        }

        let day = Self.dayFormatter.string(from: date)
        let monthIndex = Calendar(identifier: .gregorian).component(.month, from: date) - 1
        let month = Self.ruShortMonths.indices.contains(monthIndex) ? Self.ruShortMonths[monthIndex] : ""
        return "\(day) \(month)"
    }

    private func clientTotalDebtRub(for client: AdminClientSummaryResponse) -> Int {
        max(0, client.debtRub) + max(0, client.carriedDebtRub)
    }

    private func formattedDebtRub(_ amount: Int) -> String {
        let formatted = Self.rubFormatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
        return "\(formatted.replacingOccurrences(of: "\u{00A0}", with: " ")) ₽"
    }

    private func presentToast(_ message: String?) {
        guard let message = message?.trimmingCharacters(in: .whitespacesAndNewlines), !message.isEmpty else { return }
        toastDismissTask?.cancel()
        toastMessage = message
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            toastMessage = nil
        }
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

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.maximumFractionDigits = 0
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()

    private static let ruShortMonths = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]
}
