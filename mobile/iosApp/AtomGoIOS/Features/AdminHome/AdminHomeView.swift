import SwiftUI
import PhotosUI
import UIKit

struct DebtAdjustmentContext: Identifiable {
    let clientId: String
    let clientName: String
    let currentDebtRub: Int

    var id: String { clientId }
}

/// Контекст модала операции над перенесённым долгом
/// (docs/14_rental_lifecycle.md §7). Передаёт всё, что нужно для UX:
/// текущую сумму долга и подсказку «есть ли активная аренда»
/// — чтобы UI заранее показал, что излишек payment-а уйдёт в неё.
struct CarriedDebtOperationContext: Identifiable {
    let clientId: String
    let clientName: String
    let carriedDebtRub: Int
    let hasActiveRental: Bool
    let initialKind: CarriedDebtOperationKind

    var id: String { "\(clientId)-\(initialKind.apiValue)" }
}

struct CreateClientPhoneDraft: Identifiable {
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

enum ClientCatalogFilter: CaseIterable {
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

enum BikeCatalogFilter: CaseIterable {
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

struct AdminCardsTopKey: PreferenceKey {
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
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

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
        .onChange(of: viewModel.operationErrorMessage) { newValue in
            presentToast(newValue)
        }
        .onChange(of: viewModel.operationSuccessMessage) { newValue in
            presentToast(newValue)
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
                selectedRentalDetails: viewModel.selectedRentalDetails,
                isRentalDetailsLoading: viewModel.isRentalDetailsLoading,
                rentalDetailsErrorMessage: viewModel.rentalDetailsErrorMessage,
                clients: viewModel.clientCatalog,
                bikes: viewModel.bikes,
                fallbackSummaryForRental: { clientId, rentalId in
                    guard case let .loaded(clients) = viewModel.state else { return nil }
                    return clients.first(where: { $0.clientId == clientId && $0.rentalId == rentalId })
                },
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
                onOpenRental: { _, _, _ in
                },
                onRequestOpenRentalDetails: { rentalId in
                    viewModel.openRentalDetails(rentalId: rentalId)
                },
                onRequestCloseRentalDetails: {
                    viewModel.closeRentalDetails()
                },
                onAdjustDebtFromRental: { clientId, clientName, currentDebtRub in
                    debtAdjustmentContext = DebtAdjustmentContext(
                        clientId: clientId,
                        clientName: clientName,
                        currentDebtRub: currentDebtRub
                    )
                },
                onFinishRental: { clientId, rentalId in
                    viewModel.finishRental(clientId: clientId, rentalId: rentalId) {
                        viewModel.openRentalDetails(rentalId: rentalId)
                    }
                },
                onStartRental: { rentalId, payload in
                    viewModel.startClientRentalInExisting(
                        rentalId: rentalId,
                        clientId: payload.clientId,
                        login: payload.login,
                        password: payload.password,
                        periodStart: payload.periodStart
                    ) {
                        viewModel.openRentalDetails(rentalId: rentalId)
                    }
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
                    // openRentalDetails ОБЯЗАТЕЛЬНО после завершения finish,
                    // иначе GET летит параллельно POST'у и возвращает старые
                    // данные (lifecycle ещё long_term, активная client_rental
                    // не закрыта). Это превращалось в «данные старой» при
                    // последующем «Создать новую!».
                    viewModel.finishRental(clientId: clientId, rentalId: rentalId) {
                        viewModel.openRentalDetails(rentalId: rentalId)
                    }
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
        .appToast(message: $toastMessage, bottomPadding: 96)
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
            return searched.filter(\.isDebtor)
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
                filterChip(.debtors, title: "Должники", count: clients.filter(\.isDebtor).count)
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

    /// Цвет рамки аватарки на главном экране — строго по pipeline-статусу
    /// lifecycle-аренды (docs/14_rental_lifecycle.md §2):
    ///   long_term  → зелёный
    ///   soon_return → жёлтый
    ///   in_stock   → фиолетовый
    /// `rentalIsActive` сам по себе НЕ маркирует фиолетовый: активная in_stock
    /// (теоретически невозможна по invariant, но возможна в момент race)
    /// должна показываться фиолетовой согласно статусу. И наоборот, неактивная
    /// long_term-карточка (legacy/race) не должна внезапно стать фиолетовой.
    private func avatarBorderColor(for client: AdminClientSummaryResponse) -> Color {
        let normalizedStatus = (client.rentalPipelineStatus ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        switch normalizedStatus {
        case "in_stock", "mine":
            return Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255) // фиолетовый
        case "soon_return":
            return Color(red: 255 / 255, green: 204 / 255, blue: 0) // жёлтый
        case "long_term":
            return Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255) // зелёный
        default:
            // Статус не пришёл (свежесозданная запись / legacy) — fallback
            // по rentalIsActive: активная зелёная, неактивная фиолетовая.
            // Этот путь должен быть редким; основной — switch выше.
            return client.rentalIsActive
                ? Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
                : Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255)
        }
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
}
