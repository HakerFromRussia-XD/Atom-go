import SwiftUI
import PhotosUI
import UIKit

private struct DebtAdjustmentContext: Identifiable {
    let clientId: String
    let clientName: String
    let currentDebtRub: Int

    var id: String { clientId }
}

/// Контекст модала операции над перенесённым долгом
/// (docs/14_rental_lifecycle.md §7). Передаёт всё, что нужно для UX:
/// текущую сумму долга и подсказку «есть ли активная аренда»
/// — чтобы UI заранее показал, что излишек payment-а уйдёт в неё.
private struct CarriedDebtOperationContext: Identifiable {
    let clientId: String
    let clientName: String
    let carriedDebtRub: Int
    let hasActiveRental: Bool
    let initialKind: CarriedDebtOperationKind

    var id: String { "\(clientId)-\(initialKind.apiValue)" }
}

private struct CreateClientPhoneDraft: Identifiable {
    let id: UUID = .init()
    var label: String
    var number: String
}

private struct RentalDetailsContext: Identifiable, Equatable {
    let clientId: String
    let rentalId: String
    let completedAtFallback: String?

    var id: String { "\(clientId)-\(rentalId)" }
}

private enum AdminRentFilter {
    case all
    case soonReturn
    case debtors
    case mine

    var accessibilityIdentifier: String {
        switch self {
        case .all:
            return "admin.filter.all"
        case .soonReturn:
            return "admin.filter.soonReturn"
        case .debtors:
            return "admin.filter.debtors"
        case .mine:
            return "admin.filter.mine"
        }
    }

    var accessibilityLabel: String {
        switch self {
        case .all:
            return "Все"
        case .soonReturn:
            return "Скоро вернут"
        case .debtors:
            return "Должники"
        case .mine:
            return "У меня"
        }
    }

    var accessibilityValue: String {
        switch self {
        case .all:
            return "all"
        case .soonReturn:
            return "soonReturn"
        case .debtors:
            return "debtors"
        case .mine:
            return "mine"
        }
    }
}

private enum AdminMainTab: CaseIterable {
    case rents
    case clients
    case bikes

    var title: String {
        switch self {
        case .rents:
            return "Аренды"
        case .clients:
            return "Клиенты"
        case .bikes:
            return "Велосипеды"
        }
    }

    var systemImage: String {
        switch self {
        case .rents:
            return "house.fill"
        case .clients:
            return "person.2.fill"
        case .bikes:
            return "bicycle"
        }
    }

    var accessibilityIdentifier: String {
        switch self {
        case .rents:
            return "admin.tab.rents"
        case .clients:
            return "admin.tab.clients"
        case .bikes:
            return "admin.tab.bikes"
        }
    }
}

private enum ClientCatalogFilter: CaseIterable {
    case all
    case debtors
    case active

    var title: String {
        switch self {
        case .all:
            return "Все"
        case .debtors:
            return "Должники"
        case .active:
            return "Активные"
        }
    }

    var accessibilityIdentifier: String {
        switch self {
        case .all:
            return "clientCatalog.filter.all"
        case .debtors:
            return "clientCatalog.filter.debtors"
        case .active:
            return "clientCatalog.filter.active"
        }
    }
}

private enum BikeCatalogFilter: CaseIterable {
    case all
    case free
    case rented

    var title: String {
        switch self {
        case .all:
            return "Все"
        case .free:
            return "Свободны"
        case .rented:
            return "В аренде"
        }
    }

    var accessibilityIdentifier: String {
        switch self {
        case .all:
            return "bikeCatalog.filter.all"
        case .free:
            return "bikeCatalog.filter.free"
        case .rented:
            return "bikeCatalog.filter.rented"
        }
    }
}

struct RentalDetailsDisplayPolicy {
    let rentalIsActive: Bool
    let isInStockState: Bool

    private static let dash = "—"
    private static let inactiveMetricColor = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)

    var showsJournalHistory: Bool { !isInStockState }
    var adjustmentButtonEnabled: Bool { rentalIsActive && !isInStockState }

    func metricText(activeValue: String) -> String {
        if isInStockState { return Self.dash }
        return activeValue
    }

    func correctionLineText(formattedAdjustment: String) -> String {
        if isInStockState { return "Корректировка: \(Self.dash)" }
        return "Корректировка: \(formattedAdjustment)"
    }

    func metricColor(activeColor: Color) -> Color {
        isInStockState ? Self.inactiveMetricColor : activeColor
    }

    func readOnlyCredentialText(serverValue: String?, draftValue: String) -> String {
        let normalizedServer = normalizeCredential(serverValue)
        if !normalizedServer.isEmpty {
            return normalizedServer
        }

        let normalizedDraft = normalizeCredential(draftValue)
        return normalizedDraft.isEmpty ? Self.dash : normalizedDraft
    }

    private func normalizeCredential(_ value: String?) -> String {
        value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
}

private struct AdminCardsTopKey: PreferenceKey {
    static var defaultValue: CGFloat = .greatestFiniteMagnitude

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct AdminHomeView: View {
    @ObservedObject var viewModel: AdminHomeViewModel
    let startupRentalDeepLink: AdminStartupRentalDeepLink?
    let onLogout: () -> Void

    @Environment(\.scenePhase) private var scenePhase

    @State private var isCreateRentalSheetPresented = false
    @State private var isDetailsSheetPresented = false
    @State private var detailsClientId: String?
    @State private var rentalDetailsContext: RentalDetailsContext?
    @State private var debtAdjustmentContext: DebtAdjustmentContext?
    @State private var carriedDebtOperationContext: CarriedDebtOperationContext?
    @State private var ignoredNextTapClientId: String?
    @State private var searchText = ""
    @State private var selectedFilter: AdminRentFilter = .all
    @State private var selectedMainTab: AdminMainTab = .rents
    @State private var isAdminMenuPresented = false
    @State private var pipelineMenuClientId: String?
    @State private var areFiltersInteractive = true
    @State private var initialCardsTopY: CGFloat?
    @State private var didHandleStartupDeepLink = false

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ZStack(alignment: .top) {
                    AppDesign.pageBackground.ignoresSafeArea()

                    switch viewModel.state {
                    case .idle, .loading:
                        ProgressView("Загружаем список аренд...")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                    case let .failed(message):
                        VStack(spacing: 12) {
                            Text("Не удалось загрузить аренды")
                                .font(.headline)
                                .foregroundStyle(AppDesign.titleText)
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

                    case let .loaded(clients):
                        adminLoadedRootView(clients: clients)
                    }
                }
                .confirmationDialog("Действия", isPresented: $isAdminMenuPresented, titleVisibility: .visible) {
                    Button("Новая аренда") {
                        isCreateRentalSheetPresented = true
                    }
                    Button("Обновить") {
                        viewModel.load()
                    }
                    Button("Выйти", role: .destructive) {
                        onLogout()
                    }
                    Button("Отмена", role: .cancel) {}
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .task {
            if case .idle = viewModel.state {
                viewModel.load()
            }
            openStartupRentalIfNeeded()
        }
        .onAppear {
            openStartupRentalIfNeeded()
        }
        .fullScreenCover(isPresented: $isCreateRentalSheetPresented) {
            CreateRentalSheet(
                clients: viewModel.clientCatalog,
                bikes: viewModel.bikes,
                isSaving: viewModel.isOperationInProgress,
                onCancel: { isCreateRentalSheetPresented = false },
                onCreate: { payload in
                    viewModel.createRental(payload: payload)
                    isCreateRentalSheetPresented = false
                }
            )
            .presentationDetents([.large])
        }
        .fullScreenCover(isPresented: $isDetailsSheetPresented, onDismiss: {
            viewModel.closeClientDetails()
            detailsClientId = nil
        }) {
            AdminClientDetailsSheet(
                details: viewModel.selectedClientDetails,
                isLoading: viewModel.isDetailsLoading,
                errorMessage: viewModel.detailsErrorMessage,
                operationErrorMessage: viewModel.operationErrorMessage,
                operationSuccessMessage: viewModel.operationSuccessMessage,
                isOperationInProgress: viewModel.isOperationInProgress,
                clients: viewModel.clientCatalog,
                bikes: viewModel.bikes,
                onClose: {
                    isDetailsSheetPresented = false
                },
                onRetry: {
                    if let clientId = detailsClientId {
                        viewModel.openClientDetails(clientId: clientId)
                    }
                },
                onAdjustDebtTap: { details in
                    isDetailsSheetPresented = false
                    debtAdjustmentContext = DebtAdjustmentContext(
                        clientId: details.clientId,
                        clientName: details.fullName,
                        currentDebtRub: details.debtRub
                    )
                },
                onOpenCarriedDebtSheet: { details, initialKind in
                    // У клиента есть активная клиентская аренда, если в списке
                    // его аренд хотя бы одна без period_end. Backend использует
                    // это же правило (см. applyCarriedDebtOperation), так что
                    // подсказка в UI совпадает с реальной валидацией.
                    let hasActive = details.rentals.contains { $0.periodEnd == nil }
                    isDetailsSheetPresented = false
                    carriedDebtOperationContext = CarriedDebtOperationContext(
                        clientId: details.clientId,
                        clientName: details.fullName,
                        carriedDebtRub: details.carriedDebtRub,
                        hasActiveRental: hasActive,
                        initialKind: initialKind
                    )
                },
                onSaveRentalComment: { clientId, rentalId, comment in
                    viewModel.updateRentalComment(
                        clientId: clientId,
                        rentalId: rentalId,
                        comment: comment
                    )
                },
                onSaveRentalLinks: { clientId, rentalId, videoUrl, contractUrl in
                    viewModel.updateRentalLinks(
                        clientId: clientId,
                        rentalId: rentalId,
                        videoUrl: videoUrl,
                        contractUrl: contractUrl
                    )
                },
                onSaveClientProfile: { clientId, payload in
                    viewModel.updateClientProfile(clientId: clientId, payload: payload)
                },
                onDeleteClient: { clientId in
                    viewModel.deleteClient(clientId: clientId) {
                        isDetailsSheetPresented = false
                        detailsClientId = nil
                    }
                },
                onCreateRental: { payload in
                    viewModel.createRental(payload: payload)
                },
                onUpdateRental: { payload in
                    viewModel.updateRental(payload: payload)
                },
                onDeleteRental: { clientId, rentalId in
                    viewModel.deleteRental(clientId: clientId, rentalId: rentalId)
                },
                onOpenRental: { clientId, rentalId, completedAt in
                    isDetailsSheetPresented = false
                    detailsClientId = nil
                    rentalDetailsContext = RentalDetailsContext(
                        clientId: clientId,
                        rentalId: rentalId,
                        completedAtFallback: completedAt
                    )
                    viewModel.openRentalDetails(rentalId: rentalId)
                }
            )
        }
        .fullScreenCover(item: $rentalDetailsContext, onDismiss: {
            viewModel.closeRentalDetails()
        }) { context in
            AdminRentalDetailsScreen(
                details: viewModel.selectedRentalDetails,
                fallbackSummary: currentSummary(for: context),
                completedAtFallback: context.completedAtFallback,
                clients: viewModel.clientCatalog,
                isLoading: viewModel.isRentalDetailsLoading,
                errorMessage: viewModel.rentalDetailsErrorMessage,
                isOperationInProgress: viewModel.isOperationInProgress,
                onClose: {
                    rentalDetailsContext = nil
                },
                onRetry: {
                    viewModel.openRentalDetails(rentalId: context.rentalId)
                },
                onOpenClientCard: {
                    rentalDetailsContext = nil
                    DispatchQueue.main.async {
                        detailsClientId = context.clientId
                        isDetailsSheetPresented = true
                        viewModel.openClientDetails(clientId: context.clientId)
                    }
                },
                onAdjustDebt: { clientId, clientName, currentDebtRub in
                    debtAdjustmentContext = DebtAdjustmentContext(
                        clientId: clientId,
                        clientName: clientName,
                        currentDebtRub: currentDebtRub
                    )
                },
                onFinishRental: { clientId, rentalId in
                    viewModel.finishRental(clientId: clientId, rentalId: rentalId)
                    viewModel.openRentalDetails(rentalId: rentalId)
                },
                onStartRental: { payload in
                    viewModel.startClientRentalInExisting(
                        rentalId: context.rentalId,
                        clientId: payload.clientId,
                        login: payload.login,
                        password: payload.password,
                        periodStart: payload.periodStart
                    ) {
                        rentalDetailsContext = RentalDetailsContext(
                            clientId: payload.clientId,
                            rentalId: context.rentalId,
                            completedAtFallback: nil
                        )
                        viewModel.openRentalDetails(rentalId: context.rentalId)
                    }
                },
                onDeleteRental: { clientId, rentalId in
                    viewModel.deleteRental(clientId: clientId, rentalId: rentalId)
                    rentalDetailsContext = nil
                }
            )
        }
        .sheet(item: $debtAdjustmentContext) { context in
            DebtAdjustmentSheet(
                context: context,
                isSaving: viewModel.isOperationInProgress,
                onCancel: {
                    debtAdjustmentContext = nil
                },
                onApply: { amountRub, sign, comment in
                    viewModel.adjustDebt(
                        clientId: context.clientId,
                        amountRub: amountRub,
                        sign: sign,
                        comment: comment
                    )
                    debtAdjustmentContext = nil
                }
            )
            .presentationDetents([.medium])
        }
        .sheet(item: $carriedDebtOperationContext) { context in
            CarriedDebtOperationSheet(
                context: context,
                isSaving: viewModel.isOperationInProgress,
                onCancel: {
                    carriedDebtOperationContext = nil
                },
                onApply: { amountRub, kind, comment in
                    viewModel.applyCarriedDebt(
                        clientId: context.clientId,
                        amountRub: amountRub,
                        kind: kind,
                        comment: comment
                    )
                    carriedDebtOperationContext = nil
                }
            )
            .presentationDetents([.medium])
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                viewModel.load()
            }
        }
    }

    private func openStartupRentalIfNeeded() {
        guard !didHandleStartupDeepLink else { return }
        guard let startupRentalDeepLink else { return }
        didHandleStartupDeepLink = true
        rentalDetailsContext = RentalDetailsContext(
            clientId: startupRentalDeepLink.clientId,
            rentalId: startupRentalDeepLink.rentalId,
            completedAtFallback: nil
        )
        viewModel.openRentalDetails(rentalId: startupRentalDeepLink.rentalId)
    }

    private func currentSummary(for context: RentalDetailsContext) -> AdminClientSummaryResponse? {
        guard case let .loaded(clients) = viewModel.state else { return nil }
        return clients.first(where: { $0.clientId == context.clientId && $0.rentalId == context.rentalId })
    }

    private func adminLoadedRootView(clients: [AdminClientSummaryResponse]) -> some View {
        ZStack(alignment: .top) {
            switch selectedMainTab {
            case .rents:
                adminPipelineLoadedView(clients: clients)
            case .clients:
                ClientCatalogSheet(
                    clients: viewModel.clientCatalog,
                    isSaving: viewModel.isOperationInProgress,
                    apiErrorMessage: viewModel.operationErrorMessage,
                    showsCloseButton: false,
                    onCancel: onLogout,
                    onCreate: { payload, onSuccess in
                        viewModel.createClient(payload: payload, onSuccess: onSuccess)
                    },
                    onOpenClient: { client in
                        detailsClientId = client.clientId
                        isDetailsSheetPresented = true
                        viewModel.openClientDetails(clientId: client.clientId)
                    }
                )
            case .bikes:
                BikeCatalogSheet(
                    bikes: viewModel.bikes,
                    rentals: clients,
                    isSaving: viewModel.isOperationInProgress,
                    apiErrorMessage: viewModel.operationErrorMessage,
                    showsCloseButton: false,
                    onCancel: onLogout,
                    onCreate: { payload, onSuccess in
                        viewModel.createBike(payload: payload, onSuccess: onSuccess)
                    },
                    onSave: { payload in
                        viewModel.updateBike(payload: payload)
                    },
                    onDelete: { bikeId in
                        viewModel.deleteBike(bikeId: bikeId)
                    }
                )
            }

            adminBottomTabBar
                .zIndex(5)
        }
    }

    private func adminPipelineLoadedView(clients: [AdminClientSummaryResponse]) -> some View {
        let horizontalInset: CGFloat = 8
        let topBarHeight: CGFloat = 62
        let searchTopPadding: CGFloat = 6
        let searchHeight: CGFloat = 46
        let chipsTopGap: CGFloat = 10
        let chipsHeight: CGFloat = 80
        let cardsInitialTop: CGFloat = 200
        let tabBarHeight: CGFloat = 76
        let searchTop = topBarHeight + searchTopPadding
        let searchMaskHeight = searchTop + searchHeight / 2
        let chipsTop = searchTop + searchHeight + chipsTopGap
        let chipsBottom = chipsTop + chipsHeight

        return ZStack(alignment: .top) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    Color.clear
                        .frame(height: cardsInitialTop)
                        .background {
                            GeometryReader { proxy in
                                Color.clear.preference(
                                    key: AdminCardsTopKey.self,
                                    value: proxy.frame(in: .named("adminPipeline")).maxY
                                )
                            }
                        }
                        .allowsHitTesting(false)

                    VStack(alignment: .leading, spacing: 15) {
                        if let errorText = viewModel.operationErrorMessage {
                            messageBanner(
                                title: "Ошибка операции",
                                text: errorText,
                                color: AppDesign.danger
                            )
                        }

                        if let successText = viewModel.operationSuccessMessage {
                            messageBanner(
                                title: "Успешно",
                                text: successText,
                                color: AppDesign.success
                            )
                        }

                        let visibleClients = filteredClients(clients)

                        if visibleClients.isEmpty {
                            emptyRentalsView
                        } else {
                            ForEach(visibleClients, id: \.id) { client in
                                clientCard(client)
                            }
                        }
                    }
                    .padding(.top, 1)
                    .background(AppDesign.pageBackground)
                }
                .padding(.horizontal, horizontalInset)
                .padding(.bottom, tabBarHeight + 44)
            }
            .accessibilityIdentifier("admin.rents.scroll")
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

            chipRows(clients: clients)
                .padding(.horizontal, horizontalInset)
                .frame(height: chipsHeight, alignment: .topLeading)
                .offset(y: chipsTop)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .zIndex(1)

            filterHitLayer
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
                topBar
                    .frame(height: topBarHeight)
                searchField
                    .padding(.horizontal, horizontalInset)
                    .padding(.top, searchTopPadding)
                Spacer(minLength: 0)
            }
            .zIndex(4)

            Text(selectedFilter.accessibilityValue)
                .foregroundStyle(.clear)
                .frame(width: 1, height: 1)
                .accessibilityIdentifier("admin.selectedFilter")
                .accessibilityValue(selectedFilter.accessibilityValue)
                .allowsHitTesting(false)
                .zIndex(6)
        }
        .coordinateSpace(name: "adminPipeline")
    }

    private func filteredClients(_ clients: [AdminClientSummaryResponse]) -> [AdminClientSummaryResponse] {
        let searched = clients.filter { client in
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !query.isEmpty else { return true }
            let lower = query.lowercased()
            return client.fullName.lowercased().contains(lower)
                || client.bikeModel.lowercased().contains(lower)
                || (client.clientLogin?.lowercased().contains(lower) ?? false)
        }

        switch selectedFilter {
        case .all:
            return searched
        case .soonReturn:
            return searched.filter { $0.rentalIsActive && $0.rentalPipelineStatus == "soon_return" }
        case .debtors:
            return searched.filter { $0.debtRub > 0 }
        case .mine:
            return searched.filter { !$0.rentalIsActive }
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(AppDesign.titleText)

            TextField("Поиск по клиенту, велосипеду...", text: $searchText)
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
        .accessibilityIdentifier("admin.searchField")
    }

    private func chipRows(clients: [AdminClientSummaryResponse]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                filterChip(.all, title: "Все", count: clients.count, isDark: true)
                filterChip(.soonReturn, title: "Скоро вернут", count: clients.filter { $0.rentalIsActive && $0.rentalPipelineStatus == "soon_return" }.count)
                filterChip(.debtors, title: "Должники", count: clients.filter { $0.debtRub > 0 }.count)
            }
            filterChip(.mine, title: "У меня", count: clients.filter { !$0.rentalIsActive }.count)
        }
    }

    private var filterHitLayer: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                filterHitTarget(.all, width: 84)
                filterHitTarget(.soonReturn, width: 112)
                filterHitTarget(.debtors, width: 132)
            }
            filterHitTarget(.mine, width: 108)
        }
    }

    private func filterHitTarget(_ filter: AdminRentFilter, width: CGFloat) -> some View {
        Button {
            selectedFilter = filter
        } label: {
            Color.white.opacity(0.001)
                .frame(width: width, height: 36)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(filter.accessibilityLabel)
        .accessibilityIdentifier(filter.accessibilityIdentifier)
        .accessibilityValue(selectedFilter == filter ? "selected" : "normal")
    }

    private func filterChip(_ filter: AdminRentFilter, title: String, count: Int, isDark: Bool = false) -> some View {
        let isSelected = selectedFilter == filter
        let dark = isSelected

        return Button {
            selectedFilter = filter
        } label: {
            HStack(spacing: 6) {
                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .lineLimit(filter == .soonReturn ? 2 : 1)
                    .minimumScaleFactor(0.9)
                    .allowsTightening(true)
                Text("\(count)")
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background((dark ? Color.white.opacity(0.2) : Color.black.opacity(0.08)))
                    .clipShape(Capsule())
            }
            .foregroundStyle(dark ? Color.white : AppDesign.accent)
            .padding(.horizontal, 15)
            .frame(height: 36)
            .background(dark ? AppDesign.accent : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1)
            )
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var topBar: some View {
        HStack {
            topIconButton(
                systemName: "rectangle.portrait.and.arrow.right",
                accessibilityIdentifier: "admin.logoutButton",
                action: onLogout
            )
            Spacer()
            Text("Все аренды")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
            Spacer()
            topIconButton(
                systemName: "ellipsis",
                accessibilityIdentifier: "admin.openServiceButton",
                action: { isAdminMenuPresented = true }
            )
        }
        .padding(.horizontal, 8)
    }

    private func topIconButton(
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

    private var adminBottomTabBar: some View {
        VStack {
            Spacer()
            HStack(spacing: 0) {
                ForEach(AdminMainTab.allCases, id: \.self) { tab in
                    adminBottomTabButton(tab)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 12)
            .padding(.bottom, 22)
            .background(.ultraThinMaterial)
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(Color(red: 218 / 255, green: 218 / 255, blue: 218 / 255))
                    .frame(height: 1)
            }
        }
        .ignoresSafeArea(edges: .bottom)
    }

    private func adminBottomTabButton(_ tab: AdminMainTab) -> some View {
        let isSelected = selectedMainTab == tab

        return Button {
            selectedMainTab = tab
        } label: {
            VStack(spacing: 6) {
                Image(systemName: tab.systemImage)
                    .font(.system(size: tab == .bikes ? 24 : 22, weight: isSelected ? .bold : .regular))
                    .foregroundStyle(isSelected ? Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255) : AppDesign.iconSoft)
                    .frame(height: 25)

                Text(tab.title)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(isSelected ? Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255) : AppDesign.iconSoft)
                    .lineLimit(1)

                Circle()
                    .fill(isSelected ? Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255) : Color.clear)
                    .frame(width: 6, height: 6)
            }
            .frame(width: 96, height: 54)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(tab.accessibilityIdentifier)
        .accessibilityValue(isSelected ? "selected" : "normal")
    }

    private var emptyRentalsView: some View {
        VStack(spacing: 14) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)

            VStack(spacing: 4) {
                Text("Аренд пока нет")
                    .font(.headline)
                    .foregroundStyle(AppDesign.titleText)
                Text("Клиентов в каталоге: \(viewModel.clientCatalog.count)")
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.subtleText)
            }

            HStack(spacing: 10) {
                Button("Каталог клиентов") {
                    selectedMainTab = .clients
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("admin.emptyOpenClientCatalogButton")

                Button("Новая аренда") {
                    isCreateRentalSheetPresented = true
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.clientCatalog.isEmpty || viewModel.bikes.isEmpty)
                .accessibilityIdentifier("admin.emptyCreateRentalButton")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 260)
        .padding(20)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func clientCard(_ client: AdminClientSummaryResponse) -> some View {
        let cardKey = client.rentalId ?? "client-\(client.clientId)"
        let displayName = client.rentalIsActive ? client.fullName : "Клиент не выбран"

        return HStack(alignment: .center, spacing: 8) {
            Button {
                pipelineMenuClientId = cardKey
            } label: {
                bikeAvatar(urlString: client.bikeAvatarUrl, borderColor: avatarBorderColor(for: client))
            }
            .buttonStyle(.plain)
            .popover(
                isPresented: Binding(
                    get: { pipelineMenuClientId == cardKey },
                    set: { isPresented in
                        if !isPresented {
                            pipelineMenuClientId = nil
                        }
                    }
                ),
                attachmentAnchor: .rect(.bounds),
                arrowEdge: .leading
            ) {
                rentalPipelinePopoverContent(for: client)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255))
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                    .allowsTightening(true)
                Text(client.bikeModel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255).opacity(0.5))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .allowsTightening(true)
                Text("Корректировка: \(formattedRub(client.totalAdjustmentRub))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255).opacity(0.5))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .allowsTightening(true)
            }
            .frame(width: 136, alignment: .leading)
            .layoutPriority(1)

            Spacer(minLength: 0)

            Button {
                ignoredNextTapClientId = cardKey
                debtAdjustmentContext = DebtAdjustmentContext(
                    clientId: client.clientId,
                    clientName: client.fullName,
                    currentDebtRub: client.debtRub
                )
            } label: {
                statusPill(for: client)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 9)
        .frame(height: 77)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255))
        .overlay(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08), radius: 15, x: 0, y: 20)
        .contentShape(Rectangle())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("admin.rent.card.\(cardKey)")
        .onTapGesture {
            if ignoredNextTapClientId == cardKey {
                ignoredNextTapClientId = nil
                return
            }
            guard let rentalId = client.rentalId else {
                return
            }
            rentalDetailsContext = RentalDetailsContext(
                clientId: client.clientId,
                rentalId: rentalId,
                completedAtFallback: nil
            )
            viewModel.openRentalDetails(rentalId: rentalId)
        }
    }

    private func bikeAvatar(urlString: String, borderColor: Color) -> some View {
        BikePhotoView(source: urlString) {
            placeholderBikeAvatar
        }
        .frame(width: 59, height: 59)
        .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(borderColor, lineWidth: 3)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func rentalPipelinePopover(for client: AdminClientSummaryResponse) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            rentalPipelineRow(
                title: "Долгосрочная аренда",
                color: Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255),
                isSelected: client.rentalIsActive && client.rentalPipelineStatus != "soon_return"
            ) {
                updatePipelineStatus(for: client, status: "long_term")
            }

            rentalPipelineRow(
                title: "Вернут в течении недели",
                color: Color(red: 255 / 255, green: 204 / 255, blue: 0),
                isSelected: client.rentalIsActive && client.rentalPipelineStatus == "soon_return"
            ) {
                updatePipelineStatus(for: client, status: "soon_return")
            }

            rentalPipelineRow(
                title: "Велосипед у меня",
                color: Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255),
                isSelected: !client.rentalIsActive
            ) {
                finishRental(for: client)
            }
        }
        .padding(7)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(AppDesign.accent, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.18), radius: 14, x: 0, y: 12)
    }

    @ViewBuilder
    private func rentalPipelinePopoverContent(for client: AdminClientSummaryResponse) -> some View {
        if #available(iOS 16.4, *) {
            rentalPipelinePopover(for: client)
                .presentationCompactAdaptation(.popover)
        } else {
            rentalPipelinePopover(for: client)
        }
    }

    private func rentalPipelineRow(
        title: String,
        color: Color,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(color, lineWidth: 3)
                    .frame(width: 20, height: 20)

                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(AppDesign.accent)
                    .lineLimit(1)
            }
            .padding(.leading, 10)
            .padding(.trailing, 34)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color(red: 243 / 255, green: 244 / 255, blue: 246 / 255) : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func updatePipelineStatus(for client: AdminClientSummaryResponse, status: String) {
        guard let rentalId = client.rentalId else {
            pipelineMenuClientId = nil
            return
        }
        pipelineMenuClientId = nil
        viewModel.updateRentalPipelineStatus(
            clientId: client.clientId,
            rentalId: rentalId,
            pipelineStatus: status
        )
    }

    private func finishRental(for client: AdminClientSummaryResponse) {
        guard let rentalId = client.rentalId else {
            pipelineMenuClientId = nil
            return
        }
        pipelineMenuClientId = nil
        viewModel.finishRental(clientId: client.clientId, rentalId: rentalId)
    }

    private func statusPill(for client: AdminClientSummaryResponse) -> some View {
        let status = rentStatus(for: client)

        return VStack(spacing: 1) {
            Text(status.title)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.85))
                .lineLimit(1)
            Text(status.value)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .allowsTightening(true)
        }
        .frame(width: status.width, height: 44)
        .background(status.color)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func rentStatus(for client: AdminClientSummaryResponse) -> (title: String, value: String, color: Color, width: CGFloat) {
        if !client.rentalIsActive {
            return ("У меня", "—", Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255), 108)
        }

        if client.debtRub > 0 {
            return ("Долг", formattedRub(client.debtRub), Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255), 108)
        }

        return (
            "Оплачено на",
            paidDaysText(for: client),
            Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255),
            108
        )
    }

    private func avatarBorderColor(for client: AdminClientSummaryResponse) -> Color {
        if !client.rentalIsActive {
            return Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255)
        }
        if client.rentalPipelineStatus == "soon_return" {
            return Color(red: 255 / 255, green: 204 / 255, blue: 0)
        }
        if client.debtRub > 0 {
            return Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
        }
        return Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
    }

    private func paidDaysText(for client: AdminClientSummaryResponse) -> String {
        let status = client.statusText.lowercased()
        if let days = firstInteger(in: status) {
            return daysText(days)
        }

        guard let paidUntil = client.paidUntil,
              let date = DateFormatter.apiDate.date(from: paidUntil) else {
            return "—"
        }

        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let endDate = calendar.startOfDay(for: date)
        let days = max(0, calendar.dateComponents([.day], from: today, to: endDate).day ?? 0)
        return daysText(days)
    }

    private func firstInteger(in text: String) -> Int? {
        let digits = text.split { !$0.isNumber }.first
        return digits.flatMap { Int($0) }
    }

    private func daysText(_ days: Int) -> String {
        let mod10 = days % 10
        let mod100 = days % 100

        if mod10 == 1 && mod100 != 11 {
            return "\(days) день"
        }
        if (2 ... 4).contains(mod10) && !(12 ... 14).contains(mod100) {
            return "\(days) дня"
        }
        return "\(days) дней"
    }

    private func formattedRub(_ amount: Int) -> String {
        let formatted = Self.rubFormatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
        return "\(formatted.replacingOccurrences(of: "\u{00A0}", with: " ")) ₽"
    }

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()

    private var placeholderBikeAvatar: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(14)
            .foregroundStyle(AppDesign.iconSoft)
    }

    private func messageBanner(title: String, text: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(color)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(AppDesign.titleText)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct AdminRentalDetailsScreen: View {
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
        .overlay(alignment: .bottom) {
            if let copyToastMessage, !copyToastMessage.isEmpty {
                Text(copyToastMessage)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.black)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.98))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
                    .padding(.bottom, 86)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(.easeInOut(duration: 0.18), value: copyToastMessage)
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
                    isEditable: !runningRentalIsActive,
                    readOnlyText: displayPolicy.readOnlyCredentialText(
                        serverValue: details?.clientLogin ?? fallbackSummary?.clientLogin,
                        draftValue: editableRentalLogin
                    ),
                    accessibilityIdentifier: "rentalDetails.loginField"
                )
                credentialField(
                    title: "ПАРОЛЬ",
                    text: $editableRentalPassword,
                    isEditable: !runningRentalIsActive,
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
        clients
            .filter { !$0.rentalIsActive }
            .sorted { left, right in
                left.fullName.localizedCaseInsensitiveCompare(right.fullName) == .orderedAscending
            }
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

private struct CreateClientSheet: View {
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload) -> Void

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)

    @State private var fullName = ""
    @State private var address = ""
    @State private var passportData = ""
    @State private var phones: [CreateClientPhoneDraft] = [
        CreateClientPhoneDraft(label: "Рабочий (TG)", number: "")
    ]
    @State private var isCommentVisible = false
    @State private var comment = ""
    @State private var validationError: String?

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding: CGFloat = 8
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack(alignment: .top) {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    createClientTopBar(horizontalPadding: horizontalPadding)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 18) {
                            sectionTitle("Профиль")

                            createClientInput(
                                label: "ФИО",
                                placeholder: "введите...",
                                text: $fullName,
                                accessibilityIdentifier: "createClient.fullNameField"
                            )
                            createClientInput(
                                label: "Адрес",
                                placeholder: "введите...",
                                text: $address,
                                accessibilityIdentifier: "createClient.addressField"
                            )
                            createClientInput(
                                label: "Паспортные данные",
                                placeholder: "введите...",
                                text: $passportData,
                                accessibilityIdentifier: "createClient.passportField"
                            )

                            sectionTitle("Телефоны")
                                .padding(.top, 6)

                            ForEach(Array(phones.indices), id: \.self) { index in
                                createClientInput(
                                    label: "Подпись",
                                    placeholder: "введите...",
                                    text: $phones[index].label,
                                    accessibilityIdentifier: index == 0
                                        ? "createClient.phoneLabel1Field"
                                        : "createClient.phoneLabelField.\(index)",
                                    valueWeight: .bold
                                )
                                createClientInput(
                                    label: "Телефон",
                                    placeholder: "+7 …",
                                    text: $phones[index].number,
                                    accessibilityIdentifier: index == 0
                                        ? "createClient.phoneNumber1Field"
                                        : "createClient.phoneNumberField.\(index)",
                                    keyboardType: .phonePad
                                )
                            }

                            dashedActionButton(
                                title: "+ Добавить телефон",
                                accessibilityIdentifier: "createClient.addPhoneButton"
                            ) {
                                phones.append(CreateClientPhoneDraft(label: "", number: ""))
                            }

                            if isCommentVisible {
                                createClientInput(
                                    label: "Комментарий",
                                    placeholder: "введите...",
                                    text: $comment,
                                    accessibilityIdentifier: "createClient.commentField"
                                )
                            }

                            dashedActionButton(
                                title: "+ Добавить комментарий",
                                accessibilityIdentifier: "createClient.addCommentButton"
                            ) {
                                isCommentVisible = true
                            }

                            createClientErrorBlock
                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 16)
                        .padding(.bottom, 24)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .padding(.horizontal, horizontalPadding)
                }
            }
        }
    }

    private func submit() {
        validationError = nil
        let input = CreateClientFormInput(
            fullName: fullName,
            address: address,
            passportData: passportData,
            phones: phones.map { CreateClientPhoneInput(label: $0.label, number: $0.number) }
        )

        switch CreateClientFormValidator.buildPayload(from: input) {
        case let .success(payload):
            onCreate(payload)
        case let .failure(error):
            validationError = error.localizedDescription
        }
    }

    private func createClientTopBar(horizontalPadding: CGFloat) -> some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(ebonyClay)
                    .frame(width: 47, height: 47)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(ebonyClay, lineWidth: 1)
                    }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("createClient.cancelButton")

            Spacer()

            Text("Новый клиент")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer()

            Button {
                submit()
            } label: {
                Group {
                    if isSaving {
                        ProgressView()
                            .tint(Color.white)
                    } else {
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(Color.white)
                    }
                }
                .frame(width: 47, height: 47)
                .background(ebonyClay)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("createClient.submitButton")
        }
        .padding(.horizontal, horizontalPadding)
        .frame(height: 62)
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("createClient.section.\(title)")
    }

    private func createClientInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        valueWeight: Font.Weight = .regular,
        keyboardType: UIKeyboardType = .default
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)

            TextField("", text: text, prompt: Text(placeholder).foregroundColor(ghost))
                .font(.system(size: 13, weight: valueWeight))
                .foregroundStyle(ebonyClay)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(label == "ФИО" ? .words : .sentences)
                .autocorrectionDisabled()
                .accessibilityIdentifier(accessibilityIdentifier)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(ebonyClay, lineWidth: 1)
        }
    }

    private func dashedActionButton(
        title: String,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .tracking(0.28)
                .foregroundStyle(ebonyClay)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(grayChateau, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    @ViewBuilder
    private var createClientErrorBlock: some View {
        if validationError != nil || (apiErrorMessage?.isEmpty == false) {
            VStack(alignment: .leading, spacing: 8) {
                if let validationError {
                    Text(validationError)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(AppDesign.danger)
                        .accessibilityIdentifier("createClient.validationError")
                }

                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Text(apiErrorMessage)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(AppDesign.danger)
                        .accessibilityIdentifier("createClient.apiError")
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

}

private struct CreateBikeSheet: View {
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateBikePayload) -> Void

    @State private var bikeModel = ""
    @State private var weeklyRateRub = "3000"
    @State private var frameSerialNumber = ""
    @State private var motorSerialNumber = ""
    @State private var batterySerialNumber1 = ""
    @State private var batterySerialNumber2 = ""
    @State private var validationError: String?
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoPreview: Image?
    @State private var encodedPhotoDataUrl: String?

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalPadding: CGFloat = 23

    var body: some View {
        GeometryReader { proxy in
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    topBar
                        .padding(.horizontal, horizontalPadding)
                        .padding(.top, 8)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 14) {
                            if let validationError {
                                createBikeErrorCard(
                                    validationError,
                                    accessibilityIdentifier: "createBike.validationError"
                                )
                            }
                            if let apiErrorMessage, !apiErrorMessage.isEmpty {
                                createBikeErrorCard(
                                    apiErrorMessage,
                                    accessibilityIdentifier: "createBike.apiError"
                                )
                            }

                            photoPickerCard(width: fieldWidth)

                            createBikeSectionTitle("Обязательные")

                            createBikeInput(
                                label: "Название/модель",
                                placeholder: "введите...",
                                text: $bikeModel,
                                accessibilityIdentifier: "createBike.modelField",
                                textInputAutocapitalization: .words
                            )
                            createBikeInput(
                                label: "Серийный номер / VIN",
                                placeholder: "введите...",
                                text: $frameSerialNumber,
                                accessibilityIdentifier: "createBike.frameSerialField"
                            )
                            createBikeInput(
                                label: "Серийный номер мотора",
                                placeholder: "введите...",
                                text: $motorSerialNumber,
                                accessibilityIdentifier: "createBike.motorSerialField"
                            )
                            createBikeInput(
                                label: "Недельная ставка W (₽)",
                                placeholder: "введите...",
                                text: $weeklyRateRub,
                                accessibilityIdentifier: "createBike.weeklyRateField",
                                keyboardType: .numberPad
                            )

                            createBikeSectionTitle("Опционально", topPadding: 4)

                            createBikeInput(
                                label: "Серийный номер АКБ 1",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber1,
                                accessibilityIdentifier: "createBike.battery1Field",
                                isDashed: true
                            )
                            createBikeInput(
                                label: "Серийный номер АКБ 2",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber2,
                                accessibilityIdentifier: "createBike.battery2Field",
                                isDashed: true
                            )
                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 14)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, horizontalPadding)
                    .scrollDismissesKeyboard(.interactively)
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            createBikeTopButton(
                imageName: "chevron.left",
                isDark: false,
                accessibilityIdentifier: "createBike.cancelButton",
                action: onCancel
            )
            .disabled(isSaving)

            Spacer(minLength: 12)

            Text("Новый велосипед")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer(minLength: 12)

            createBikeTopButton(
                imageName: "checkmark",
                isDark: true,
                accessibilityIdentifier: "createBike.submitButton",
                action: submit
            )
            .disabled(isSaving)
            .opacity(isSaving ? 0.45 : 1)
        }
        .frame(height: 47)
    }

    private func createBikeTopButton(
        imageName: String,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? ebonyClay : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                )
                .overlay(
                    Group {
                        if isDark && isSaving && accessibilityIdentifier == "createBike.submitButton" {
                            ProgressView()
                                .tint(Color.white)
                        } else {
                            Image(systemName: imageName)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(isDark ? Color.white : ebonyClay)
                        }
                    }
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func photoPickerCard(width: CGFloat) -> some View {
        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 238 / 255, green: 240 / 255, blue: 243 / 255))

                if let selectedPhotoPreview {
                    selectedPhotoPreview
                        .resizable()
                        .scaledToFill()
                        .frame(width: width, height: 202)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay {
                            Color.black.opacity(0.2)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                }

                VStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(ebonyClay, lineWidth: 1)
                        )
                        .overlay(
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 24, weight: .regular))
                                .foregroundStyle(ebonyClay)
                        )
                        .frame(width: 58, height: 58)

                    Text("Загрузить фото")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(ebonyClay)

                    Text("Нажмите, чтобы выбрать из галереи")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(paleSky)
                }
            }
            .frame(width: width, height: 202)
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(ebonyClay, style: StrokeStyle(lineWidth: 1, dash: [3, 2.5]))
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("createBike.photoPicker")
    }

    private func createBikeSectionTitle(_ text: String, topPadding: CGFloat = 0) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .padding(.top, topPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func createBikeInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        isDashed: Bool = false,
        keyboardType: UIKeyboardType = .default,
        textInputAutocapitalization: TextInputAutocapitalization = .never
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)
                .lineLimit(1)

            TextField(
                "",
                text: text,
                prompt: Text(placeholder).foregroundColor(ghost)
            )
            .font(.system(size: 13, weight: .regular))
            .foregroundStyle(ebonyClay)
            .keyboardType(keyboardType)
            .textInputAutocapitalization(textInputAutocapitalization)
            .autocorrectionDisabled()
            .accessibilityIdentifier(accessibilityIdentifier)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .overlay {
            if isDashed {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(grayChateau, style: StrokeStyle(lineWidth: 1, dash: [3, 2.5]))
            } else {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(ebonyClay, lineWidth: 1)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
    }

    private func createBikeErrorCard(_ text: String, accessibilityIdentifier: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(AppDesign.danger)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
            .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func submit() {
        validationError = nil
        let model = bikeModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let frame = frameSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let motor = motorSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery1 = batterySerialNumber1.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery2 = batterySerialNumber2.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !model.isEmpty else {
            validationError = "Укажите модель велосипеда"
            return
        }
        guard let weeklyRate = Int(weeklyRateRub.trimmingCharacters(in: .whitespacesAndNewlines)), weeklyRate > 0 else {
            validationError = "Стоимость недели должна быть положительным числом"
            return
        }
        guard !frame.isEmpty else {
            validationError = "Укажите серийный номер рамы"
            return
        }
        guard !motor.isEmpty else {
            validationError = "Укажите серийный номер мотора"
            return
        }
        guard !battery1.isEmpty else {
            validationError = "Укажите серийный номер аккумулятора 1"
            return
        }

        let duplicateValidation = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: bikes,
            bikeIdToIgnore: nil,
            frameSerial: frame,
            motorSerial: motor,
            batterySerialNumber1: battery1,
            batterySerialNumber2: battery2.isEmpty ? nil : battery2
        )
        if let duplicateValidation {
            validationError = duplicateValidation
            return
        }

        onCreate(
            CreateBikePayload(
                photoUrl: encodedPhotoDataUrl,
                bikeModel: model,
                weeklyRateRub: weeklyRate,
                frameSerialNumber: frame,
                motorSerialNumber: motor,
                batterySerialNumber1: battery1,
                batterySerialNumber2: battery2.isEmpty ? nil : battery2
            )
        )
    }

    private func loadPhoto(item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            guard let uiImage = UIImage(data: data) else { return }
            let jpeg = uiImage.jpegData(compressionQuality: 0.82) ?? data
            await MainActor.run {
                selectedPhotoPreview = Image(uiImage: uiImage)
                encodedPhotoDataUrl = "data:image/jpeg;base64,\(jpeg.base64EncodedString())"
            }
        } catch {
            await MainActor.run {
                validationError = "Не удалось загрузить фото"
            }
        }
    }
}

private struct RentalStartClientPickerSheet: View {
    let clients: [AdminClientSummaryResponse]
    @Binding var selectedClientId: String?
    let onClose: () -> Void
    let onConfirm: () -> Void

    @State private var searchText = ""
    @State private var selectedFilter: ClientCatalogFilter = .all

    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalInset: CGFloat = 8

    var body: some View {
        ZStack(alignment: .top) {
            athensGray.ignoresSafeArea()

            VStack(spacing: 0) {
                header

                if visibleClients.isEmpty {
                    emptyState
                        .padding(.horizontal, horizontalInset)
                        .padding(.top, 14)
                } else {
                    List {
                        ForEach(visibleClients) { client in
                            clientRow(client)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
        }
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
            return searched.filter { $0.debtRub > 0 }
        case .active:
            return []
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                headerIconButton(
                    systemName: "xmark",
                    accessibilityIdentifier: "rentalClientPicker.closeButton",
                    action: onClose
                )

                Spacer()

                Text("Клиенты")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

                Spacer()

                headerIconButton(
                    systemName: "checkmark",
                    accessibilityIdentifier: "rentalClientPicker.confirmButton",
                    action: onConfirm
                )
                .disabled(selectedClientId == nil)
                .opacity(selectedClientId == nil ? 0.45 : 1)
            }
            .padding(.horizontal, horizontalInset)
            .frame(height: 62)

            searchField
                .padding(.horizontal, horizontalInset)
                .padding(.top, 6)

            HStack(spacing: 8) {
                filterChip(.all, count: clients.count)
                filterChip(.debtors, count: clients.filter { $0.debtRub > 0 }.count)
                filterChip(.active, count: 0)
            }
            .padding(.horizontal, horizontalInset)
            .padding(.top, 10)
        }
        .background(athensGray)
        .accessibilityIdentifier("rentalClientPicker.header")
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
        .accessibilityIdentifier("rentalClientPicker.searchField")
    }

    private func filterChip(_ filter: ClientCatalogFilter, count: Int) -> some View {
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
        .accessibilityIdentifier("rentalClientPicker.\(filter.accessibilityIdentifier)")
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
            Image(systemName: "person.slash")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)
            Text("Нет свободных клиентов")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)
            Text("В списке выбора скрыты клиенты, которые уже участвуют в активных арендах.")
                .font(.subheadline)
                .foregroundStyle(AppDesign.subtleText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func clientRow(_ client: AdminClientSummaryResponse) -> some View {
        let isSelected = selectedClientId == client.clientId

        return Button {
            selectedClientId = client.clientId
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
                    } else {
                        Text("Свободный клиент")
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                    }
                    Text(client.bikeModel)
                        .font(.caption)
                        .foregroundStyle(AppDesign.subtleText)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? AppDesign.success : AppDesign.iconSoft)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("rentalClientPicker.client.\(client.clientId)")
        .accessibilityValue(isSelected ? "selected" : "normal")
    }
}

private struct RentalStartBikePickerSheet: View {
    let bikes: [AdminBikeResponse]
    @Binding var selectedBikeId: String?
    let onClose: () -> Void
    let onConfirm: () -> Void

    @State private var searchText = ""

    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalInset: CGFloat = 8

    var body: some View {
        ZStack(alignment: .top) {
            athensGray.ignoresSafeArea()

            VStack(spacing: 0) {
                header

                if visibleBikes.isEmpty {
                    emptyState
                        .padding(.horizontal, horizontalInset)
                        .padding(.top, 14)
                } else {
                    List {
                        ForEach(visibleBikes) { bike in
                            bikeRow(bike)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
        }
    }

    private var visibleBikes: [AdminBikeResponse] {
        let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let searched = bikes.filter { bike in
            guard !normalizedQuery.isEmpty else { return true }
            return bike.bikeModel.lowercased().contains(normalizedQuery)
                || bike.frameSerialNumber.lowercased().contains(normalizedQuery)
                || bike.motorSerialNumber.lowercased().contains(normalizedQuery)
                || bike.batterySerialNumber1.lowercased().contains(normalizedQuery)
                || (bike.batterySerialNumber2?.lowercased().contains(normalizedQuery) ?? false)
        }

        return searched.sorted { left, right in
            left.bikeModel.localizedCaseInsensitiveCompare(right.bikeModel) == .orderedAscending
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                headerIconButton(
                    systemName: "xmark",
                    accessibilityIdentifier: "rentalBikePicker.closeButton",
                    action: onClose
                )

                Spacer()

                Text("Велосипеды")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

                Spacer()

                headerIconButton(
                    systemName: "checkmark",
                    accessibilityIdentifier: "rentalBikePicker.confirmButton",
                    action: onConfirm
                )
                .disabled(selectedBikeId == nil)
                .opacity(selectedBikeId == nil ? 0.45 : 1)
            }
            .padding(.horizontal, horizontalInset)
            .frame(height: 62)

            searchField
                .padding(.horizontal, horizontalInset)
                .padding(.top, 6)
        }
        .background(athensGray)
        .accessibilityIdentifier("rentalBikePicker.header")
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(AppDesign.titleText)

            TextField("Поиск: модель, серийный номер", text: $searchText)
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
        .accessibilityIdentifier("rentalBikePicker.searchField")
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
            Image(systemName: "bicycle")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)
            Text("Нет велосипедов")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func bikeRow(_ bike: AdminBikeResponse) -> some View {
        let isSelected = selectedBikeId == bike.bikeId

        return Button {
            selectedBikeId = bike.bikeId
        } label: {
            HStack(spacing: 12) {
                BikePhotoView(source: bike.photoUrl) {
                    Image(systemName: "bicycle")
                        .resizable()
                        .scaledToFit()
                        .padding(10)
                        .foregroundStyle(AppDesign.iconSoft)
                }
                .frame(width: 48, height: 48)
                .background(AppDesign.surfaceBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(bike.bikeModel)
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    Text("\(bike.weeklyRateRub) ₽ / неделя")
                        .font(.subheadline)
                        .foregroundStyle(AppDesign.subtleText)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? AppDesign.success : AppDesign.iconSoft)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("rentalBikePicker.bike.\(bike.bikeId)")
        .accessibilityValue(isSelected ? "selected" : "normal")
    }
}

private struct ClientCatalogSheet: View {
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
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Text(apiErrorMessage)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(AppDesign.danger)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(16)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                                .padding(.top, 14)
                                .accessibilityIdentifier("clientCatalog.error")
                        }

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
            return searched.filter { $0.debtRub > 0 }
        case .active:
            return searched.filter { $0.rentalIsActive }
        }
    }

    private func clientChipRows(clients: [AdminClientSummaryResponse]) -> some View {
        HStack(spacing: 8) {
            clientFilterChip(.all, count: clients.count)
            clientFilterChip(.debtors, count: clients.filter { $0.debtRub > 0 }.count)
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
}

private struct BikeCatalogSheet: View {
    let bikes: [AdminBikeResponse]
    let rentals: [AdminClientSummaryResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    var showsCloseButton: Bool = true
    let onCancel: () -> Void
    let onCreate: (CreateBikePayload, @escaping () -> Void) -> Void
    let onSave: (UpdateBikePayload) -> Void
    let onDelete: (String) -> Void

    @State private var editingBike: AdminBikeResponse?
    @State private var bikePendingDeletion: AdminBikeResponse?
    @State private var isCreateBikePresented = false
    @State private var searchText = ""
    @State private var selectedFilter: BikeCatalogFilter = .all
    @State private var initialCardsTopY: CGFloat?
    @State private var areFiltersInteractive = true

    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let blackHaze = Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255)
    private let alto = Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255)
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

    private struct BikeCatalogRuntimeSnapshot {
        let hasActiveRental: Bool
        let activeCount: Int
        let totalDebtRub: Int
        let borderColor: Color
    }

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
                                    value: proxy.frame(in: .named("bikeCatalogPipeline")).maxY
                                )
                            }
                        }
                        .allowsHitTesting(false)

                    VStack(alignment: .leading, spacing: 0) {
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Text(apiErrorMessage)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(AppDesign.danger)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                                .padding(.top, 10)
                                .accessibilityIdentifier("bikeCatalog.error")
                        }

                        if visibleBikes.isEmpty {
                            bikeEmptyState
                                .padding(.top, 10)
                        } else {
                            LazyVStack(spacing: 10) {
                                ForEach(visibleBikes) { bike in
                                    bikeRow(bike)
                                }
                            }
                            .padding(.top, 10)
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

            bikeChipRows
                .padding(.horizontal, horizontalInset)
                .frame(height: chipsHeight, alignment: .topLeading)
                .offset(y: chipsTop)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .zIndex(1)

            bikeFilterHitLayer
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
                topBar
                    .frame(height: topBarHeight)
                searchField
                    .padding(.horizontal, horizontalInset)
                    .padding(.top, searchTopPadding)
                Spacer(minLength: 0)
            }
            .zIndex(4)
        }
        .coordinateSpace(name: "bikeCatalogPipeline")
        .sheet(isPresented: $isCreateBikePresented) {
            CreateBikeSheet(
                bikes: bikes,
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { isCreateBikePresented = false },
                onCreate: { payload in
                    onCreate(payload) {
                        isCreateBikePresented = false
                    }
                }
            )
            .presentationDetents([.large])
        }
        .fullScreenCover(item: $editingBike) { bike in
            EditBikeSheet(
                bike: bike,
                bikes: bikes,
                isSaving: isSaving,
                apiErrorMessage: apiErrorMessage,
                onCancel: { editingBike = nil },
                onSave: { payload in
                    onSave(payload)
                    editingBike = nil
                }
            )
        }
        .confirmationDialog(
            "Удалить велосипед?",
            isPresented: Binding(
                get: { bikePendingDeletion != nil },
                set: { isPresented in
                    if !isPresented {
                        bikePendingDeletion = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                if let bike = bikePendingDeletion {
                    onDelete(bike.bikeId)
                    bikePendingDeletion = nil
                }
            }
            Button("Отмена", role: .cancel) {
                bikePendingDeletion = nil
            }
        } message: {
            Text("Велосипед без истории аренд будет удален из каталога.")
        }
    }

    private var bikeChipRows: some View {
        HStack(spacing: 8) {
            bikeFilterChip(.all, count: bikes.count)
            bikeFilterChip(.free, count: bikes.filter { !snapshot(for: $0).hasActiveRental }.count)
            bikeFilterChip(.rented, count: bikes.filter { snapshot(for: $0).hasActiveRental }.count)
        }
    }

    private var bikeFilterHitLayer: some View {
        HStack(spacing: 8) {
            bikeFilterHitTarget(.all, width: 84)
            bikeFilterHitTarget(.free, width: 128)
            bikeFilterHitTarget(.rented, width: 110)
        }
    }

    private func bikeFilterHitTarget(_ filter: BikeCatalogFilter, width: CGFloat) -> some View {
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

    private var topBar: some View {
        HStack {
            headerIconButton(
                systemName: showsCloseButton ? "xmark" : "rectangle.portrait.and.arrow.right",
                accessibilityIdentifier: showsCloseButton ? "bikeCatalog.closeButton" : "bikeCatalog.logoutButton",
                action: onCancel
            )

            Spacer()

            Text("Велосипеды")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

            Spacer()

            headerIconButton(
                systemName: "plus",
                accessibilityIdentifier: "bikeCatalog.addBikeButton",
                action: { isCreateBikePresented = true }
            )
        }
        .padding(.horizontal, horizontalInset)
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
        .accessibilityIdentifier("bikeCatalog.searchField")
    }

    private var visibleBikes: [AdminBikeResponse] {
        let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let searched = bikes.filter { bike in
            guard !normalizedQuery.isEmpty else { return true }
            return bike.bikeModel.lowercased().contains(normalizedQuery)
                || bike.frameSerialNumber.lowercased().contains(normalizedQuery)
                || bike.motorSerialNumber.lowercased().contains(normalizedQuery)
                || bike.batterySerialNumber1.lowercased().contains(normalizedQuery)
                || (bike.batterySerialNumber2?.lowercased().contains(normalizedQuery) ?? false)
        }

        let filtered: [AdminBikeResponse]
        switch selectedFilter {
        case .all:
            filtered = searched
        case .free:
            filtered = searched.filter { !snapshot(for: $0).hasActiveRental }
        case .rented:
            filtered = searched.filter { snapshot(for: $0).hasActiveRental }
        }

        return filtered.sorted {
            $0.bikeModel.localizedCaseInsensitiveCompare($1.bikeModel) == .orderedAscending
        }
    }

    private func bikeFilterChip(_ filter: BikeCatalogFilter, count: Int) -> some View {
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

    private var bikeEmptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "bicycle")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(AppDesign.iconSoft)

            Text(searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Список велосипедов пуст" : "Ничего не найдено")
                .font(.headline)
                .foregroundStyle(AppDesign.titleText)

            Text(
                searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "Создайте первый велосипед внутри этого списка."
                : "Измените запрос или фильтр."
            )
            .font(.subheadline)
            .foregroundStyle(AppDesign.subtleText)
            .multilineTextAlignment(.center)

            if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Button("Создать велосипед") {
                    isCreateBikePresented = true
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("bikeCatalog.emptyCreateBikeButton")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func bikeRow(_ bike: AdminBikeResponse) -> some View {
        let runtime = snapshot(for: bike)
        let hasDebt = runtime.totalDebtRub > 0

        return Button {
            editingBike = bike
        } label: {
            HStack(spacing: 16) {
                bikeThumb(bike: bike, borderColor: runtime.borderColor)

                VStack(alignment: .leading, spacing: 8) {
                    Text(bike.bikeModel)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(AppDesign.titleText)
                        .lineLimit(1)

                    Text("\(bike.weeklyRateRub) ₽/нед")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                        .lineLimit(1)

                    Text(serialLine(for: bike))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if runtime.hasActiveRental {
                    statusPill(runtime: runtime)
                }
            }
            .padding(.leading, 19)
            .padding(.trailing, 15)
            .padding(.vertical, 22)
            .frame(height: 110)
            .background(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .fill(blackHaze)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .stroke(alto, lineWidth: 1)
            )
            .shadow(
                color: Color(red: 25 / 255, green: 28 / 255, blue: 50 / 255).opacity(0.08),
                radius: 15,
                x: 0,
                y: 20
            )
            .contentShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Редактировать") {
                editingBike = bike
            }
            if !runtime.hasActiveRental && !hasDebt {
                Button("Удалить", role: .destructive) {
                    bikePendingDeletion = bike
                }
            }
        }
        .disabled(isSaving)
        .accessibilityIdentifier("bikeCatalog.edit.\(bike.bikeModel)")
    }

    private func bikeThumb(bike: AdminBikeResponse, borderColor: Color) -> some View {
        BikePhotoView(source: bike.photoUrl) {
            placeholder
        }
            .frame(width: 44, height: 44)
            .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(borderColor, lineWidth: 3)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func statusPill(runtime: BikeCatalogRuntimeSnapshot) -> some View {
        let isDebt = runtime.totalDebtRub > 0
        return VStack(spacing: 0) {
            Text(isDebt ? "Долг" : "Активн.")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.85))
                .lineLimit(1)
            Text(isDebt ? "\(formattedCompactRub(runtime.totalDebtRub)) ₽" : "\(runtime.activeCount)")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color.white)
                .lineLimit(1)
        }
        .frame(minWidth: 108, minHeight: 56)
        .padding(.horizontal, 14)
        .background(isDebt ? AppDesign.danger : AppDesign.success)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func snapshot(for bike: AdminBikeResponse) -> BikeCatalogRuntimeSnapshot {
        let matchedRentals = rentals.filter { summary in
            summary.bikeModel.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            == bike.bikeModel.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        }

        let activeRentals = matchedRentals.filter { $0.rentalIsActive }
        let hasActiveRental = !activeRentals.isEmpty
        let totalDebt = activeRentals.reduce(0) { partial, item in
            partial + max(0, item.debtRub)
        }
        let hasSoonReturn = activeRentals.contains { $0.rentalPipelineStatus == "soon_return" }
        let borderColor: Color
        if hasActiveRental {
            borderColor = hasSoonReturn
                ? Color(red: 255 / 255, green: 204 / 255, blue: 0)
                : Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
        } else {
            borderColor = Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255)
        }

        return BikeCatalogRuntimeSnapshot(
            hasActiveRental: hasActiveRental,
            activeCount: activeRentals.count,
            totalDebtRub: totalDebt,
            borderColor: borderColor
        )
    }

    private func formattedCompactRub(_ amount: Int) -> String {
        let formatted = Self.rubFormatter.string(from: NSNumber(value: max(0, amount))) ?? "\(max(0, amount))"
        return formatted.replacingOccurrences(of: "\u{00A0}", with: " ")
    }

    private func serialLine(for bike: AdminBikeResponse) -> String {
        let serial = bike.frameSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        if serial.isEmpty { return "Серийный номер не задан" }
        return "SN \(serial)"
    }

    private var placeholder: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(8)
            .foregroundStyle(AppDesign.iconSoft)
    }

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()
}

private struct EditBikeSheet: View {
    let bike: AdminBikeResponse
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onSave: (UpdateBikePayload) -> Void

    @State private var bikeModel: String
    @State private var weeklyRateRub: String
    @State private var frameSerialNumber: String
    @State private var motorSerialNumber: String
    @State private var batterySerialNumber1: String
    @State private var batterySerialNumber2: String
    @State private var validationError: String?
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoPreview: Image?
    @State private var overridePhotoDataUrl: String?

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let horizontalPadding: CGFloat = 23

    init(
        bike: AdminBikeResponse,
        bikes: [AdminBikeResponse],
        isSaving: Bool,
        apiErrorMessage: String?,
        onCancel: @escaping () -> Void,
        onSave: @escaping (UpdateBikePayload) -> Void
    ) {
        self.bike = bike
        self.bikes = bikes
        self.isSaving = isSaving
        self.apiErrorMessage = apiErrorMessage
        self.onCancel = onCancel
        self.onSave = onSave
        _bikeModel = State(initialValue: bike.bikeModel)
        _weeklyRateRub = State(initialValue: "\(bike.weeklyRateRub)")
        _frameSerialNumber = State(initialValue: bike.frameSerialNumber)
        _motorSerialNumber = State(initialValue: bike.motorSerialNumber)
        _batterySerialNumber1 = State(initialValue: bike.batterySerialNumber1)
        _batterySerialNumber2 = State(initialValue: bike.batterySerialNumber2 ?? "")
    }

    var body: some View {
        GeometryReader { proxy in
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    topBar
                        .padding(.horizontal, horizontalPadding)
                        .padding(.top, 8)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 14) {
                            if let validationError {
                                createBikeErrorCard(
                                    validationError,
                                    accessibilityIdentifier: "editBike.validationError"
                                )
                            }
                            if let apiErrorMessage, !apiErrorMessage.isEmpty {
                                createBikeErrorCard(
                                    apiErrorMessage,
                                    accessibilityIdentifier: "editBike.apiError"
                                )
                            }

                            photoPickerCard(width: fieldWidth)

                            createBikeSectionTitle("Обязательные")

                            createBikeInput(
                                label: "Название/модель",
                                placeholder: "введите...",
                                text: $bikeModel,
                                accessibilityIdentifier: "editBike.modelField",
                                textInputAutocapitalization: .words
                            )
                            createBikeInput(
                                label: "Серийный номер / VIN",
                                placeholder: "введите...",
                                text: $frameSerialNumber,
                                accessibilityIdentifier: "editBike.frameSerialField"
                            )
                            createBikeInput(
                                label: "Серийный номер мотора",
                                placeholder: "введите...",
                                text: $motorSerialNumber,
                                accessibilityIdentifier: "editBike.motorSerialField"
                            )
                            createBikeInput(
                                label: "Недельная ставка W (₽)",
                                placeholder: "введите...",
                                text: $weeklyRateRub,
                                accessibilityIdentifier: "editBike.weeklyRateField",
                                keyboardType: .numberPad
                            )

                            createBikeSectionTitle("Опционально", topPadding: 4)

                            createBikeInput(
                                label: "Серийный номер АКБ 1",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber1,
                                accessibilityIdentifier: "editBike.battery1Field",
                                isDashed: true
                            )
                            createBikeInput(
                                label: "Серийный номер АКБ 2",
                                placeholder: "не обязательно",
                                text: $batterySerialNumber2,
                                accessibilityIdentifier: "editBike.battery2Field",
                                isDashed: true
                            )
                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 14)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, horizontalPadding)
                    .scrollDismissesKeyboard(.interactively)
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            createBikeTopButton(
                imageName: "chevron.left",
                isDark: false,
                showsProgress: false,
                accessibilityIdentifier: "editBike.cancelButton",
                action: onCancel
            )
            .disabled(isSaving)

            Spacer(minLength: 12)

            Text("Ред. велосипед")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer(minLength: 12)

            createBikeTopButton(
                imageName: "checkmark",
                isDark: true,
                showsProgress: true,
                accessibilityIdentifier: "editBike.submitButton",
                action: submit
            )
            .disabled(isSaving)
            .opacity(isSaving ? 0.45 : 1)
        }
        .frame(height: 47)
    }

    private func createBikeTopButton(
        imageName: String,
        isDark: Bool,
        showsProgress: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? ebonyClay : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                )
                .overlay(
                    Group {
                        if isDark && showsProgress && isSaving {
                            ProgressView()
                                .tint(Color.white)
                        } else {
                            Image(systemName: imageName)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(isDark ? Color.white : ebonyClay)
                        }
                    }
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private var normalizedCurrentPhotoSource: String? {
        let source = (overridePhotoDataUrl ?? bike.photoUrl)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return source.isEmpty ? nil : source
    }

    private func photoPickerCard(width: CGFloat) -> some View {
        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 238 / 255, green: 240 / 255, blue: 243 / 255))

                if let selectedPhotoPreview {
                    selectedPhotoPreview
                        .resizable()
                        .scaledToFill()
                        .frame(width: width, height: 202)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay {
                            Color.black.opacity(0.2)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                } else if let source = normalizedCurrentPhotoSource {
                    BikePhotoView(source: source) {
                        PlaceholderBikeAvatar(cornerRadius: 14)
                    }
                    .frame(width: width, height: 202)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        Color.black.opacity(0.2)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                }

                VStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(ebonyClay, lineWidth: 1)
                        )
                        .overlay(
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 24, weight: .regular))
                                .foregroundStyle(ebonyClay)
                        )
                        .frame(width: 58, height: 58)

                    Text("Заменить фото")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(ebonyClay)

                    Text("Нажмите, чтобы выбрать из галереи")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(paleSky)
                }
            }
            .frame(width: width, height: 202)
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(ebonyClay, style: StrokeStyle(lineWidth: 1, dash: [3, 2.5]))
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("editBike.photoPicker")
    }

    private func createBikeSectionTitle(_ text: String, topPadding: CGFloat = 0) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .padding(.top, topPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func createBikeInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        isDashed: Bool = false,
        keyboardType: UIKeyboardType = .default,
        textInputAutocapitalization: TextInputAutocapitalization = .never
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)
                .lineLimit(1)

            TextField(
                "",
                text: text,
                prompt: Text(placeholder).foregroundColor(ghost)
            )
            .font(.system(size: 13, weight: .regular))
            .foregroundStyle(ebonyClay)
            .keyboardType(keyboardType)
            .textInputAutocapitalization(textInputAutocapitalization)
            .autocorrectionDisabled()
            .accessibilityIdentifier(accessibilityIdentifier)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .overlay {
            if isDashed {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(grayChateau, style: StrokeStyle(lineWidth: 1, dash: [3, 2.5]))
            } else {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(ebonyClay, lineWidth: 1)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
    }

    private func createBikeErrorCard(_ text: String, accessibilityIdentifier: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(AppDesign.danger)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
            .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func submit() {
        validationError = nil

        let model = bikeModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let frame = frameSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let motor = motorSerialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery1 = batterySerialNumber1.trimmingCharacters(in: .whitespacesAndNewlines)
        let battery2 = batterySerialNumber2.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !model.isEmpty else {
            validationError = "Укажите модель велосипеда"
            return
        }
        guard let weeklyRate = Int(weeklyRateRub.trimmingCharacters(in: .whitespacesAndNewlines)), weeklyRate > 0 else {
            validationError = "Стоимость недели должна быть положительным числом"
            return
        }
        guard !frame.isEmpty else {
            validationError = "Укажите серийный номер рамы"
            return
        }
        guard !motor.isEmpty else {
            validationError = "Укажите серийный номер мотора"
            return
        }
        guard !battery1.isEmpty else {
            validationError = "Укажите серийный номер аккумулятора 1"
            return
        }

        let duplicateValidation = AdminFormValidator.validateBikeSerialDuplicates(
            allBikes: bikes,
            bikeIdToIgnore: bike.bikeId,
            frameSerial: frame,
            motorSerial: motor,
            batterySerialNumber1: battery1,
            batterySerialNumber2: battery2.isEmpty ? nil : battery2
        )
        if let duplicateValidation {
            validationError = duplicateValidation
            return
        }

        onSave(
            UpdateBikePayload(
                bikeId: bike.bikeId,
                photoUrl: overridePhotoDataUrl ?? bike.photoUrl,
                bikeModel: model,
                weeklyRateRub: weeklyRate,
                frameSerialNumber: frame,
                motorSerialNumber: motor,
                batterySerialNumber1: battery1,
                batterySerialNumber2: battery2.isEmpty ? nil : battery2
            )
        )
    }

    private func loadPhoto(item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            guard let uiImage = UIImage(data: data) else { return }
            let jpeg = uiImage.jpegData(compressionQuality: 0.82) ?? data
            await MainActor.run {
                selectedPhotoPreview = Image(uiImage: uiImage)
                overridePhotoDataUrl = "data:image/jpeg;base64,\(jpeg.base64EncodedString())"
            }
        } catch {
            await MainActor.run {
                validationError = "Не удалось загрузить фото"
            }
        }
    }
}

private struct DebtAdjustmentSheet: View {
    let context: DebtAdjustmentContext
    let isSaving: Bool
    let onCancel: () -> Void
    let onApply: (Int, DebtAdjustmentSign, String?) -> Void

    @State private var sign: DebtAdjustmentSign = .minus
    @State private var amountRub = ""
    @State private var comment = ""
    @State private var validationError: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Клиент") {
                    Text(context.clientName)
                    Text("Текущий долг: \(context.currentDebtRub) ₽")
                        .foregroundStyle(AppDesign.subtleText)
                }

                Section("Корректировка") {
                    Picker("Тип", selection: $sign) {
                        Text("Уменьшить долг").tag(DebtAdjustmentSign.minus)
                        Text("Увеличить долг").tag(DebtAdjustmentSign.plus)
                    }
                    .pickerStyle(.segmented)

                    TextField("Сумма, ₽", text: $amountRub)
                        .keyboardType(.numberPad)
                    TextField("Комментарий (необязательно)", text: $comment)
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
            }
            .navigationTitle("Корректировка")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() {
        validationError = nil
        guard let amount = Int(amountRub), amount > 0 else {
            validationError = "Введите положительную сумму"
            return
        }

        onApply(amount, sign, comment.trimmedToOptional)
    }
}

/// Модал admin-операций над перенесённым долгом клиента.
/// Списать (без оплаты) или Принять оплату (наличные/перевод вне YooKassa).
/// Излишек payment-а автоматически уходит в активную клиентскую аренду —
/// в UI это видно по подсказке «Излишек уйдёт в активную аренду» и
/// в итоговом success-сообщении.
private struct CarriedDebtOperationSheet: View {
    let context: CarriedDebtOperationContext
    let isSaving: Bool
    let onCancel: () -> Void
    let onApply: (Int, CarriedDebtOperationKind, String?) -> Void

    @State private var kind: CarriedDebtOperationKind
    @State private var amountRub: String = ""
    @State private var comment: String = ""
    @State private var validationError: String?

    init(
        context: CarriedDebtOperationContext,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onApply: @escaping (Int, CarriedDebtOperationKind, String?) -> Void
    ) {
        self.context = context
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onApply = onApply
        _kind = State(initialValue: context.initialKind)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Клиент") {
                    Text(context.clientName)
                    Text("Перенесённый долг: \(context.carriedDebtRub) ₽")
                        .foregroundStyle(AppDesign.subtleText)
                }

                Section("Операция") {
                    Picker("Тип", selection: $kind) {
                        Text("Принять оплату").tag(CarriedDebtOperationKind.payment)
                        Text("Списать").tag(CarriedDebtOperationKind.writeoff)
                    }
                    .pickerStyle(.segmented)

                    TextField("Сумма, ₽", text: $amountRub)
                        .keyboardType(.numberPad)
                    TextField("Комментарий (необязательно)", text: $comment)
                }

                Section {
                    Text(hintText)
                        .font(.footnote)
                        .foregroundStyle(AppDesign.subtleText)
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
            }
            .navigationTitle("Перенесённый долг")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    /// Подсказка под полями: меняется в зависимости от выбранного типа
    /// и наличия активной аренды, чтобы админ заранее понимал поведение
    /// (особенно про excess для payment).
    private var hintText: String {
        switch kind {
        case .writeoff:
            return "Сумма списания не может превышать перенесённый долг (\(context.carriedDebtRub) ₽)."
        case .payment:
            if context.hasActiveRental {
                return "До \(context.carriedDebtRub) ₽ уйдёт в перенесённый долг. Излишек автоматически зачтётся в активную клиентскую аренду."
            } else {
                return "До \(context.carriedDebtRub) ₽ уйдёт в перенесённый долг. У клиента нет активной аренды — сумма больше \(context.carriedDebtRub) ₽ не пройдёт."
            }
        }
    }

    private func submit() {
        validationError = nil
        guard let amount = Int(amountRub), amount > 0 else {
            validationError = "Введите положительную сумму"
            return
        }

        // Локальная валидация: writeoff заведомо больше carriedDebt — не дёргать backend.
        if kind == .writeoff && amount > context.carriedDebtRub {
            validationError = "Сумма списания больше перенесённого долга (\(context.carriedDebtRub) ₽)"
            return
        }
        // Payment с amount > carriedDebt без активной аренды backend заведомо отклонит,
        // покажем понятный текст сразу, не делая запрос.
        if kind == .payment && amount > context.carriedDebtRub && !context.hasActiveRental {
            validationError = "У клиента нет активной аренды, поэтому излишек платежа некуда направить"
            return
        }

        onApply(amount, kind, comment.trimmedToOptional)
    }
}

private struct AdminClientDetailsSheet: View {
    let details: AdminClientDetailsResponse?
    let isLoading: Bool
    let errorMessage: String?
    let operationErrorMessage: String?
    let operationSuccessMessage: String?
    let isOperationInProgress: Bool
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
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

    @Environment(\.openURL) private var openURL
    @State private var isProfileEditorPresented = false
    @State private var isCreateRentalPresented = false
    @State private var isDeleteClientConfirmationPresented = false

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

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 20) {
                            clientStatusCard(details)
                            profileBlock(details)
                            rentalHistoryBlock(details)
                            operationMessages
                        }
                        .padding(.horizontal, 23)
                        .padding(.top, 14)
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
        .padding(.horizontal, 23)
        .frame(height: 86)
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
            onOpenRental(details.clientId, rental.id, rental.periodEnd)
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

    @ViewBuilder
    private var operationMessages: some View {
        if let operationErrorMessage {
            Text(operationErrorMessage)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(AppDesign.danger)
        }
        if let operationSuccessMessage {
            Text(operationSuccessMessage)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(AppDesign.success)
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

private struct CreateRentalSheet: View {
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let preselectedClientId: String?
    let isSaving: Bool
    let onCancel: () -> Void
    let onCreate: (CreateRentalPayload) -> Void

    @State private var selectedClientId: String
    @State private var selectedBikeId: String
    @State private var login: String
    @State private var password: String
    @State private var periodStart: String
    @State private var periodEnd: String
    @State private var videoUrl: String
    @State private var contractUrl: String
    @State private var comment: String
    @State private var validationError: String?
    @State private var isClientPickerPresented = false
    @State private var isBikePickerPresented = false

    init(
        clients: [AdminClientSummaryResponse],
        bikes: [AdminBikeResponse],
        preselectedClientId: String? = nil,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onCreate: @escaping (CreateRentalPayload) -> Void
    ) {
        self.clients = clients
        self.bikes = bikes
        self.preselectedClientId = preselectedClientId
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onCreate = onCreate
        let initialClientId = preselectedClientId ?? ""
        let initialBikeId = ""
        let initialClientLogin = clients.first(where: { $0.clientId == initialClientId })?.clientLogin ?? ""
        _selectedClientId = State(initialValue: initialClientId)
        _selectedBikeId = State(initialValue: initialBikeId)
        _login = State(initialValue: initialClientLogin)
        _password = State(initialValue: "")
        _periodStart = State(initialValue: DateFormatter.apiDate.string(from: Date()))
        _periodEnd = State(initialValue: "")
        _videoUrl = State(initialValue: "")
        _contractUrl = State(initialValue: "")
        _comment = State(initialValue: "")
    }

    var body: some View {
        ZStack {
            AppDesign.pageBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 23)
                    .padding(.top, 8)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        if let validationError {
                            Text(validationError)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(AppDesign.danger)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
                                .accessibilityIdentifier("createRental.validationError")
                        }

                        sectionTitle("КЛИЕНТ И ВЕЛОСИПЕД")

                        selectionField(
                            title: "КЛИЕНТ",
                            value: selectedClientName,
                            placeholder: "выбрать клаента",
                            leadingMarkerColor: Color(red: 211 / 255, green: 215 / 255, blue: 221 / 255),
                            action: { isClientPickerPresented = true }
                        )
                        .accessibilityIdentifier("createRental.clientPicker")

                        selectionField(
                            title: "ВЕЛОСИПЕД",
                            value: selectedBikeName,
                            placeholder: "выбрать · покажет ставку",
                            leadingMarkerColor: Color(red: 205 / 255, green: 209 / 255, blue: 217 / 255),
                            action: { isBikePickerPresented = true }
                        )
                        .accessibilityIdentifier("createRental.bikePicker")

                        inputField(
                            title: "ДАТА НАЧАЛА",
                            placeholder: "YYYY-MM-DD",
                            text: $periodStart,
                            id: "createRental.periodStartField"
                        )
                        inputField(
                            title: "ДАТА ОКОНЧАНИЯ",
                            placeholder: "не обязательно",
                            text: $periodEnd,
                            id: "createRental.periodEndField",
                            isDashed: true
                        )

                        sectionTitle("ДОСТУП КЛИЕНТА", topPadding: 6)

                        inputField(
                            title: "ЛОГИН КЛИЕНТА",
                            placeholder: "введите...",
                            text: $login,
                            id: "createRental.loginField"
                        )
                        inputField(
                            title: "ПАРОЛЬ КЛИЕНТА",
                            placeholder: "введите...",
                            text: $password,
                            id: "createRental.passwordField"
                        )

                        credentialButtonsRow

                        sectionTitle("ДОКУМЕНТЫ И КОММЕНТАРИЙ", topPadding: 6)

                        inputField(
                            title: "ССЫЛКА НА ВИДЕО",
                            placeholder: "не обязательно",
                            text: $videoUrl,
                            id: "createRental.videoUrlField",
                            isDashed: true
                        )
                        inputField(
                            title: "ССЫЛКА НА ДОГОВОР",
                            placeholder: "не обязательно",
                            text: $contractUrl,
                            id: "createRental.contractUrlField",
                            isDashed: true
                        )
                        inputField(
                            title: "КОММЕНТАРИЙ",
                            placeholder: "не обязательно",
                            text: $comment,
                            id: "createRental.commentField",
                            isDashed: true
                        )
                    }
                    .padding(.horizontal, 23)
                    .padding(.top, 14)
                    .padding(.bottom, 26)
                }
            }
        }
        .onChange(of: selectedClientId) { newClientId in
            if let suggestedLogin = clients.first(where: { $0.clientId == newClientId })?.clientLogin,
               !suggestedLogin.isEmpty {
                login = suggestedLogin
            }
        }
        .fullScreenCover(isPresented: $isClientPickerPresented) {
            RentalStartClientPickerSheet(
                clients: availableClientsForStart,
                selectedClientId: Binding(
                    get: { selectedClientId.isEmpty ? nil : selectedClientId },
                    set: { selectedClientId = $0 ?? "" }
                ),
                onClose: { isClientPickerPresented = false },
                onConfirm: { isClientPickerPresented = false }
            )
        }
        .fullScreenCover(isPresented: $isBikePickerPresented) {
            RentalStartBikePickerSheet(
                bikes: bikes,
                selectedBikeId: Binding(
                    get: { selectedBikeId.isEmpty ? nil : selectedBikeId },
                    set: { selectedBikeId = $0 ?? "" }
                ),
                onClose: { isBikePickerPresented = false },
                onConfirm: { isBikePickerPresented = false }
            )
        }
    }

    private var availableClientsForStart: [AdminClientSummaryResponse] {
        clients
            .filter { !$0.rentalIsActive }
            .sorted { lhs, rhs in
                lhs.fullName.localizedCaseInsensitiveCompare(rhs.fullName) == .orderedAscending
            }
    }

    private var selectedClientName: String? {
        clients.first(where: { $0.clientId == selectedClientId })?.fullName
    }

    private var selectedBikeName: String? {
        guard let bike = bikes.first(where: { $0.bikeId == selectedBikeId }) else { return nil }
        return "\(bike.bikeModel) · \(bike.weeklyRateRub) ₽/нед"
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            topBarButton(
                imageName: "chevron.left",
                isDark: false,
                accessibilityIdentifier: "createRental.cancelButton",
                action: onCancel
            )
            .disabled(isSaving)

            Spacer(minLength: 12)

            Text("Новая аренда")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AppDesign.titleText)

            Spacer(minLength: 12)

            topBarButton(
                imageName: "checkmark",
                isDark: true,
                accessibilityIdentifier: "createRental.submitButton",
                action: submit
            )
            .disabled(isSaving)
            .opacity(isSaving ? 0.45 : 1)
        }
        .frame(height: 47)
    }

    private func topBarButton(
        imageName: String,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? AppDesign.accent : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .overlay(
                    Image(systemName: imageName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(isDark ? Color.white : AppDesign.accent)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func sectionTitle(_ text: String, topPadding: CGFloat = 0) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            .padding(.top, topPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func selectionField(
        title: String,
        value: String?,
        placeholder: String,
        leadingMarkerColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 10, weight: .bold))
                        .tracking(0.8)
                        .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                    Text(value ?? placeholder)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(value == nil ? Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255) : AppDesign.titleText)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
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
            .frame(minHeight: 58)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1)
            )
            .overlay(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(leadingMarkerColor)
                    .frame(width: 4)
                    .padding(.vertical, 8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var credentialButtonsRow: some View {
        HStack(spacing: 8) {
            Button(action: generateCredentials) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 14, weight: .medium))
                    Text("Сгенерировать")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(Color.white)
                .frame(width: 179, height: 44)
                .background(AppDesign.accent)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("createRental.generateCredentialsButton")

            Button(action: copyCredentials) {
                HStack(spacing: 8) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 14, weight: .medium))
                    Text("Скопировать")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(AppDesign.accent)
                .frame(width: 181, height: 46)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("createRental.copyCredentialsButton")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func inputField(
        title: String,
        placeholder: String,
        text: Binding<String>,
        id: String,
        isDashed: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                .lineLimit(1)
            TextField(
                "",
                text: text,
                prompt: Text(placeholder)
                    .foregroundColor(Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255))
            )
            .font(.system(size: 13, weight: .regular))
            .foregroundStyle(AppDesign.titleText)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .accessibilityIdentifier(id)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .background(Color.white)
        .overlay {
            if isDashed {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(
                        Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255),
                        style: StrokeStyle(lineWidth: 1, dash: [3, 2.5])
                    )
            } else {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
    }

    private func generateCredentials() {
        let symbols = Array("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%")
        password = String((0..<12).compactMap { _ in symbols.randomElement() })
        if login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if let selectedClientName {
                login = selectedClientName
                    .lowercased()
                    .replacingOccurrences(of: " ", with: ".")
                    .filter { $0.isLetter || $0.isNumber || $0 == "." }
            }
            if login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                login = "client\(Int.random(in: 1000...9999))"
            }
        }
    }

    private func copyCredentials() {
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedLogin.isEmpty && normalizedPassword.isEmpty {
            validationError = "Заполните логин и пароль"
            return
        }
        if normalizedLogin.isEmpty {
            validationError = "Заполните логин"
            return
        }
        if normalizedPassword.isEmpty {
            validationError = "Заполните пароль"
            return
        }
        validationError = nil
        UIPasteboard.general.string = "Логин: \(normalizedLogin)\nПароль: \(normalizedPassword)"
    }

    private func submit() {
        validationError = nil
        let normalizedStart = periodStart.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedEnd = periodEnd.trimmedToOptional
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !selectedClientId.isEmpty else {
            validationError = "Выберите клиента"
            return
        }
        guard !selectedBikeId.isEmpty else {
            validationError = "Выберите велосипед"
            return
        }
        guard !normalizedLogin.isEmpty, !normalizedPassword.isEmpty else {
            validationError = "Укажите логин и пароль клиента"
            return
        }
        if let duplicateLoginMessage = AdminFormValidator.validateRentalLoginDuplicate(
            clients: clients,
            selectedClientId: selectedClientId,
            login: normalizedLogin
        ) {
            validationError = duplicateLoginMessage
            return
        }
        guard isValidApiDate(normalizedStart) else {
            validationError = "Дата начала должна быть в формате YYYY-MM-DD"
            return
        }
        if let normalizedEnd {
            guard isValidApiDate(normalizedEnd) else {
                validationError = "Дата окончания должна быть в формате YYYY-MM-DD"
                return
            }
            if normalizedEnd < normalizedStart {
                validationError = "Дата окончания не может быть раньше даты начала"
                return
            }
        }

        onCreate(
            CreateRentalPayload(
                clientId: selectedClientId,
                bikeId: selectedBikeId,
                login: normalizedLogin,
                password: normalizedPassword,
                periodStart: normalizedStart,
                periodEnd: normalizedEnd,
                videoUrl: videoUrl.trimmedToOptional,
                contractUrl: contractUrl.trimmedToOptional,
                comment: comment.trimmedToOptional
            )
        )
    }

    private func isValidApiDate(_ value: String) -> Bool {
        guard value.count == 10 else { return false }
        let formatter = DateFormatter.apiDate
        return formatter.date(from: value) != nil
    }
}

private struct EditClientProfilePhone: Identifiable {
    let id: String
    var label: String
    var number: String
}

private struct EditClientProfileSheet: View {
    let details: AdminClientDetailsResponse
    let isSaving: Bool
    let onCancel: () -> Void
    let onSave: (UpdateClientProfilePayload) -> Void

    private let ebonyClay = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let paleSky = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let ghost = Color(red: 201 / 255, green: 204 / 255, blue: 210 / 255)
    private let grayChateau = Color(red: 152 / 255, green: 161 / 255, blue: 173 / 255)
    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)

    @State private var fullName: String
    @State private var address: String
    @State private var passportData: String
    @State private var phones: [EditClientProfilePhone]
    @State private var isCommentVisible = false
    @State private var comment = ""
    @State private var validationError: String?

    init(
        details: AdminClientDetailsResponse,
        isSaving: Bool,
        onCancel: @escaping () -> Void,
        onSave: @escaping (UpdateClientProfilePayload) -> Void
    ) {
        self.details = details
        self.isSaving = isSaving
        self.onCancel = onCancel
        self.onSave = onSave
        _fullName = State(initialValue: details.fullName)
        _address = State(initialValue: details.address)
        _passportData = State(initialValue: details.passportData)
        _phones = State(initialValue: details.phones.map {
            EditClientProfilePhone(id: $0.id, label: $0.label, number: $0.number)
        })
    }

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding: CGFloat = 8
            let fieldWidth = max(0, proxy.size.width - horizontalPadding * 2)

            ZStack(alignment: .top) {
                athensGray.ignoresSafeArea()

                VStack(spacing: 0) {
                    editClientTopBar(horizontalPadding: horizontalPadding)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 18) {
                            editSectionTitle("Профиль")

                            editClientInput(
                                label: "ФИО",
                                placeholder: "введите...",
                                text: $fullName,
                                accessibilityIdentifier: "editClient.fullNameField"
                            )
                            editClientInput(
                                label: "Адрес",
                                placeholder: "введите...",
                                text: $address,
                                accessibilityIdentifier: "editClient.addressField"
                            )
                            editClientInput(
                                label: "Паспортные данные",
                                placeholder: "введите...",
                                text: $passportData,
                                accessibilityIdentifier: "editClient.passportField"
                            )

                            editSectionTitle("Телефоны")
                                .padding(.top, 6)

                            ForEach(Array(phones.indices), id: \.self) { index in
                                editClientInput(
                                    label: "Подпись",
                                    placeholder: "введите...",
                                    text: $phones[index].label,
                                    accessibilityIdentifier: index == 0
                                        ? "editClient.phoneLabel1Field"
                                        : "editClient.phoneLabelField.\(index)",
                                    valueWeight: .bold
                                )
                                editClientInput(
                                    label: "Телефон",
                                    placeholder: "+7 …",
                                    text: $phones[index].number,
                                    accessibilityIdentifier: index == 0
                                        ? "editClient.phoneNumber1Field"
                                        : "editClient.phoneNumberField.\(index)",
                                    keyboardType: .phonePad
                                )
                            }

                            editDashedActionButton(
                                title: "+ Добавить телефон",
                                accessibilityIdentifier: "editClient.addPhoneButton"
                            ) {
                                phones.append(
                                    EditClientProfilePhone(
                                        id: UUID().uuidString,
                                        label: "",
                                        number: ""
                                    )
                                )
                            }

                            if isCommentVisible {
                                editClientInput(
                                    label: "Комментарий",
                                    placeholder: "введите...",
                                    text: $comment,
                                    accessibilityIdentifier: "editClient.commentField"
                                )
                            }

                            editDashedActionButton(
                                title: "+ Добавить комментарий",
                                accessibilityIdentifier: "editClient.addCommentButton"
                            ) {
                                isCommentVisible = true
                            }

                            editClientErrorBlock
                        }
                        .frame(width: fieldWidth, alignment: .leading)
                        .padding(.top, 16)
                        .padding(.bottom, 24)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .padding(.horizontal, horizontalPadding)
                }
            }
        }
    }

    private func editClientTopBar(horizontalPadding: CGFloat) -> some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(ebonyClay)
                    .frame(width: 47, height: 47)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(ebonyClay, lineWidth: 1)
                    }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("editClient.cancelButton")

            Spacer()

            Text("Редактировать")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(ebonyClay)
                .lineLimit(1)

            Spacer()

            Button {
                submit()
            } label: {
                Group {
                    if isSaving {
                        ProgressView()
                            .tint(Color.white)
                    } else {
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(Color.white)
                    }
                }
                .frame(width: 47, height: 47)
                .background(ebonyClay)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(ebonyClay, lineWidth: 1)
                }
            }
            .disabled(isSaving)
            .buttonStyle(.plain)
            .accessibilityIdentifier("editClient.submitButton")
        }
        .padding(.horizontal, horizontalPadding)
        .frame(height: 62)
    }

    private func editSectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.88)
            .textCase(.uppercase)
            .foregroundStyle(paleSky)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("editClient.section.\(title)")
    }

    private func editClientInput(
        label: String,
        placeholder: String,
        text: Binding<String>,
        accessibilityIdentifier: String,
        valueWeight: Font.Weight = .regular,
        keyboardType: UIKeyboardType = .default
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)

            TextField("", text: text, prompt: Text(placeholder).foregroundColor(ghost))
                .font(.system(size: 13, weight: valueWeight))
                .foregroundStyle(ebonyClay)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(label == "ФИО" ? .words : .sentences)
                .autocorrectionDisabled()
                .accessibilityIdentifier(accessibilityIdentifier)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                .stroke(ebonyClay, lineWidth: 1)
        }
    }

    private func editDashedActionButton(
        title: String,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .tracking(0.28)
                .foregroundStyle(ebonyClay)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(grayChateau, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    @ViewBuilder
    private var editClientErrorBlock: some View {
        if let validationError {
            Text(validationError)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(AppDesign.danger)
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityIdentifier("editClient.validationError")
        }
    }

    private func submit() {
        validationError = nil

        let normalizedFullName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedAddress = address.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedPassport = passportData.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !normalizedFullName.isEmpty else {
            validationError = "Укажите ФИО клиента"
            return
        }

        let normalizedPhones = phones
            .map { rawPhone in
                AdminClientPhone(
                    id: rawPhone.id,
                    label: rawPhone.label.trimmingCharacters(in: .whitespacesAndNewlines),
                    number: rawPhone.number.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            }
            .filter { !$0.label.isEmpty && !$0.number.isEmpty }

        let payload = UpdateClientProfilePayload(
            fullName: normalizedFullName,
            address: normalizedAddress,
            passportData: normalizedPassport,
            phones: normalizedPhones
        )
        onSave(payload)
    }
}

private struct RentalHistoryCard: View {
    let clientId: String
    let rental: AdminRentalHistoryItem
    let bikes: [AdminBikeResponse]
    let onOpenVideo: () -> Void
    let onOpenContract: () -> Void
    let onSaveComment: (String, String, String) -> Void
    let onSaveLinks: (String, String, String?, String?) -> Void
    let onSaveRental: (UpdateRentalPayload) -> Void
    let onDeleteRental: (String, String) -> Void
    let onOpenRental: (String, String, String?) -> Void

    @State private var isEditingComment = false
    @State private var commentDraft: String
    @State private var isEditingVideoLink = false
    @State private var isEditingContractLink = false
    @State private var isEditingRental = false
    @State private var isDeleteConfirmationPresented = false
    @State private var videoUrlDraft: String
    @State private var contractUrlDraft: String
    @State private var selectedBikeId: String
    @State private var periodStartDraft: String
    @State private var periodEndDraft: String
    @State private var rentalValidationError: String?

    init(
        clientId: String,
        rental: AdminRentalHistoryItem,
        bikes: [AdminBikeResponse],
        onOpenVideo: @escaping () -> Void,
        onOpenContract: @escaping () -> Void,
        onSaveComment: @escaping (String, String, String) -> Void,
        onSaveLinks: @escaping (String, String, String?, String?) -> Void,
        onSaveRental: @escaping (UpdateRentalPayload) -> Void,
        onDeleteRental: @escaping (String, String) -> Void,
        onOpenRental: @escaping (String, String, String?) -> Void
    ) {
        self.clientId = clientId
        self.rental = rental
        self.bikes = bikes
        self.onOpenVideo = onOpenVideo
        self.onOpenContract = onOpenContract
        self.onSaveComment = onSaveComment
        self.onSaveLinks = onSaveLinks
        self.onSaveRental = onSaveRental
        self.onDeleteRental = onDeleteRental
        self.onOpenRental = onOpenRental
        _commentDraft = State(initialValue: rental.comment ?? "")
        _videoUrlDraft = State(initialValue: rental.videoUrl ?? "")
        _contractUrlDraft = State(initialValue: rental.contractUrl ?? "")
        _selectedBikeId = State(initialValue: rental.bikeId)
        _periodStartDraft = State(initialValue: rental.periodStart)
        _periodEndDraft = State(initialValue: rental.periodEnd ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                avatar

                VStack(alignment: .leading, spacing: 4) {
                    Text(periodText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppDesign.titleText)
                    Text(rental.bikeModel)
                        .font(.caption)
                        .foregroundStyle(AppDesign.subtleText)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 6) {
                    Button("Открыть") {
                        onOpenRental(clientId, rental.id, rental.periodEnd)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppDesign.accent)
                    .accessibilityIdentifier("rentalCard.openRentalButton")
                    HStack(spacing: 6) {
                        Button("Видео") { onOpenVideo() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("rentalCard.openVideoButton")
                        Button("Ред.") {
                            isEditingVideoLink.toggle()
                            if isEditingVideoLink {
                                isEditingContractLink = false
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.editVideoLinkButton")
                    }
                    HStack(spacing: 6) {
                        Button("Договор") { onOpenContract() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("rentalCard.openContractButton")
                        Button("Ред.") {
                            isEditingContractLink.toggle()
                            if isEditingContractLink {
                                isEditingVideoLink = false
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.editContractLinkButton")
                    }
                    Button(isEditingComment ? "Скрыть" : "Комментарий") {
                        isEditingComment.toggle()
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("rentalCard.toggleCommentButton")

                    HStack(spacing: 6) {
                        Button(isEditingRental ? "Скрыть" : "Изм.") {
                            isEditingRental.toggle()
                            if isEditingRental {
                                rentalValidationError = nil
                            }
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.toggleEditButton")

                        Button("Удалить") {
                            isDeleteConfirmationPresented = true
                        }
                        .buttonStyle(.bordered)
                        .tint(AppDesign.danger)
                        .accessibilityIdentifier("rentalCard.deleteButton")
                    }
                }
                .font(.caption)
            }

            if let comment = rental.comment, !comment.isEmpty {
                Text(comment)
                    .font(.subheadline)
                    .foregroundStyle(AppDesign.titleText)
            }

            if isEditingVideoLink {
                linkEditor(
                    title: "Ссылка на видео",
                    text: $videoUrlDraft,
                    onCancel: {
                        videoUrlDraft = rental.videoUrl ?? ""
                        isEditingVideoLink = false
                    },
                    onSave: {
                        onSaveLinks(clientId, rental.id, videoUrlDraft.trimmedToOptional, contractUrlDraft.trimmedToOptional)
                        isEditingVideoLink = false
                    }
                )
            }

            if isEditingContractLink {
                linkEditor(
                    title: "Ссылка на договор",
                    text: $contractUrlDraft,
                    onCancel: {
                        contractUrlDraft = rental.contractUrl ?? ""
                        isEditingContractLink = false
                    },
                    onSave: {
                        onSaveLinks(clientId, rental.id, videoUrlDraft.trimmedToOptional, contractUrlDraft.trimmedToOptional)
                        isEditingContractLink = false
                    }
                )
            }

            if isEditingComment {
                VStack(alignment: .leading, spacing: 6) {
                    TextField("Комментарий к аренде", text: $commentDraft, axis: .vertical)
                        .lineLimit(3, reservesSpace: true)
                        .padding(8)
                        .background(AppDesign.surfaceBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                    HStack(spacing: 10) {
                        Button("Сохранить") {
                            onSaveComment(clientId, rental.id, commentDraft)
                            isEditingComment = false
                        }
                        .buttonStyle(.borderedProminent)

                        Button("Отмена") {
                            commentDraft = rental.comment ?? ""
                            isEditingComment = false
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            if isEditingRental {
                VStack(alignment: .leading, spacing: 8) {
                    if bikes.isEmpty {
                        Text("Нет доступных велосипедов для выбора")
                            .font(.caption)
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Велосипед", selection: $selectedBikeId) {
                            ForEach(bikes) { bike in
                                Text("\(bike.bikeModel) • \(bike.weeklyRateRub) ₽/нед").tag(bike.bikeId)
                            }
                        }
                        .accessibilityIdentifier("rentalCard.bikePicker")
                    }

                    TextField("Дата начала (YYYY-MM-DD)", text: $periodStartDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("rentalCard.periodStartField")
                    TextField("Дата окончания (необязательно)", text: $periodEndDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("rentalCard.periodEndField")

                    if let rentalValidationError {
                        Text(rentalValidationError)
                            .font(.caption)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("rentalCard.validationError")
                    }

                    HStack(spacing: 10) {
                        Button("Сохранить") {
                            submitRentalUpdate()
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("rentalCard.saveEditButton")

                        Button("Отмена") {
                            resetRentalEditor()
                            isEditingRental = false
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("rentalCard.cancelEditButton")
                    }
                }
            }
        }
        .padding(10)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .confirmationDialog(
            "Удалить аренду?",
            isPresented: $isDeleteConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                onDeleteRental(clientId, rental.id)
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Действие нельзя отменить.")
        }
        .onAppear {
            if !bikes.contains(where: { $0.bikeId == selectedBikeId }) {
                selectedBikeId = bikes.first?.bikeId ?? ""
            }
        }
    }

    @ViewBuilder
    private func linkEditor(
        title: String,
        text: Binding<String>,
        onCancel: @escaping () -> Void,
        onSave: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppDesign.subtleText)
            TextField(title, text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(8)
                .background(AppDesign.surfaceBackground)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .foregroundStyle(AppDesign.titleText)

            HStack(spacing: 10) {
                Button("Сохранить", action: onSave)
                    .buttonStyle(.borderedProminent)
                Button("Отмена", action: onCancel)
                    .buttonStyle(.bordered)
            }
        }
    }

    private var avatar: some View {
        BikePhotoView(source: rental.bikeAvatarUrl) {
            Image(systemName: "bicycle")
                .resizable()
                .scaledToFit()
                .padding(9)
                .foregroundStyle(AppDesign.iconSoft)
        }
        .frame(width: 44, height: 44)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var periodText: String {
        if let end = rental.periodEnd, !end.isEmpty {
            return "\(rental.periodStart) - \(end)"
        }
        return "\(rental.periodStart) - н.в."
    }

    private func submitRentalUpdate() {
        rentalValidationError = nil
        let start = periodStartDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let end = periodEndDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedEnd = end.isEmpty ? nil : end

        guard !selectedBikeId.isEmpty else {
            rentalValidationError = "Выберите велосипед"
            return
        }
        guard isValidApiDate(start) else {
            rentalValidationError = "Дата начала должна быть в формате YYYY-MM-DD"
            return
        }
        if let normalizedEnd {
            guard isValidApiDate(normalizedEnd) else {
                rentalValidationError = "Дата окончания должна быть в формате YYYY-MM-DD"
                return
            }
            if normalizedEnd < start {
                rentalValidationError = "Дата окончания не может быть раньше даты начала"
                return
            }
        }

        onSaveRental(
            UpdateRentalPayload(
                clientId: clientId,
                rentalId: rental.id,
                bikeId: selectedBikeId,
                periodStart: start,
                periodEnd: normalizedEnd
            )
        )
        isEditingRental = false
    }

    private func isValidApiDate(_ value: String) -> Bool {
        guard value.count == 10 else { return false }
        return DateFormatter.apiDate.date(from: value) != nil
    }

    private func resetRentalEditor() {
        selectedBikeId = rental.bikeId
        periodStartDraft = rental.periodStart
        periodEndDraft = rental.periodEnd ?? ""
        rentalValidationError = nil
    }
}

private struct PlaceholderBikeAvatar: View {
    let cornerRadius: CGFloat

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            ZStack {
                Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255)

                Path { path in
                    path.move(to: CGPoint(x: 0, y: 0))
                    path.addLine(to: CGPoint(x: size.width, y: size.height))
                    path.move(to: CGPoint(x: size.width, y: 0))
                    path.addLine(to: CGPoint(x: 0, y: size.height))
                }
                .stroke(Color(red: 156 / 255, green: 166 / 255, blue: 179 / 255).opacity(0.45), lineWidth: 1)

                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        Color(red: 156 / 255, green: 166 / 255, blue: 179 / 255),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                    )
            }
        }
    }
}

private struct BikePhotoView<Placeholder: View>: View {
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
                    image.resizable().scaledToFill()
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

private extension String {
    var trimmedToOptional: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}


private extension DateFormatter {
    static let apiDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
