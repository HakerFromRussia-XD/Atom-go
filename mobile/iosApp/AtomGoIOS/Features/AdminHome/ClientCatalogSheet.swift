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
    private let cardsInitialTop: CGFloat = 216
    private let tabBarHeight: CGFloat = 76

    private var searchTop: CGFloat { topBarHeight + searchTopPadding }
    private var searchMaskHeight: CGFloat { searchTop + searchHeight / 2 }
    private var chipsTop: CGFloat { searchTop + searchHeight + chipsTopGap }
    private var chipsBottom: CGFloat { chipsTop + chipsHeight }

    var body: some View {
        ZStack(alignment: .top) {
            athensGray.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
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
                                            .padding(.leading, 72)
                                    }
                                }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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
            headerIconButton(
                systemName: showsCloseButton ? "xmark" : "rectangle.portrait.and.arrow.right",
                accessibilityIdentifier: showsCloseButton ? "clientCatalog.closeButton" : "clientCatalog.logoutButton",
                action: onCancel
            )

            Spacer()

            Text("Клиенты")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

            Spacer()

            headerIconButton(
                systemName: "plus",
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
        Button {
            onOpenClient(client)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "person.crop.circle")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(AppDesign.iconSoft)
                    .frame(width: 48, height: 48)
                    .background(AppDesign.surfaceBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(client.fullName)
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    if let login = client.clientLogin, !login.isEmpty {
                        Text("Логин: \(login)")
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                    }
                    Text(client.bikeModel)
                        .font(.caption)
                        .foregroundStyle(AppDesign.subtleText)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppDesign.subtleText)
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("clientCatalog.open.\(client.fullName)")
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
}
