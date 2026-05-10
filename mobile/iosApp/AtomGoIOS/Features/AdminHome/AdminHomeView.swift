import SwiftUI
import PhotosUI
import UIKit

private struct DebtAdjustmentContext: Identifiable {
    let clientId: String
    let clientName: String
    let currentDebtRub: Int

    var id: String { clientId }
}

private struct CreateClientPhoneDraft: Identifiable {
    let id: UUID = .init()
    var label: String
    var number: String
}

private struct RentalDetailsContext: Identifiable, Equatable {
    let clientId: String
    let rentalId: String

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
    @State private var isServiceSheetPresented = false
    @State private var isClientCatalogPresented = false
    @State private var isBikeCatalogPresented = false
    @State private var isDetailsSheetPresented = false
    @State private var detailsClientId: String?
    @State private var rentalDetailsContext: RentalDetailsContext?
    @State private var debtAdjustmentContext: DebtAdjustmentContext?
    @State private var ignoredNextTapClientId: String?
    @State private var searchText = ""
    @State private var selectedFilter: AdminRentFilter = .all
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
                        adminPipelineLoadedView(clients: clients)
                    }
                }
                .confirmationDialog("Действия", isPresented: $isAdminMenuPresented, titleVisibility: .visible) {
                    Button("Сервис") {
                        isServiceSheetPresented = true
                    }
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
        .sheet(isPresented: $isServiceSheetPresented) {
            AdminServiceSheet(
                onOpenClientsCatalog: {
                    isServiceSheetPresented = false
                    viewModel.refreshClientCatalog {
                        isClientCatalogPresented = true
                    }
                },
                onOpenBikeCatalog: {
                    isServiceSheetPresented = false
                    isBikeCatalogPresented = true
                }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $isCreateRentalSheetPresented) {
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
        .sheet(isPresented: $isClientCatalogPresented) {
            ClientCatalogSheet(
                clients: viewModel.clientCatalog,
                isSaving: viewModel.isOperationInProgress,
                apiErrorMessage: viewModel.operationErrorMessage,
                onCancel: { isClientCatalogPresented = false },
                onCreate: { payload, onSuccess in
                    viewModel.createClient(payload: payload, onSuccess: onSuccess)
                },
                onOpenClient: { client in
                    isClientCatalogPresented = false
                    DispatchQueue.main.async {
                        detailsClientId = client.clientId
                        isDetailsSheetPresented = true
                        viewModel.openClientDetails(clientId: client.clientId)
                    }
                }
            )
            .presentationDetents([.large])
        }
        .sheet(isPresented: $isBikeCatalogPresented) {
            BikeCatalogSheet(
                bikes: viewModel.bikes,
                isSaving: viewModel.isOperationInProgress,
                apiErrorMessage: viewModel.operationErrorMessage,
                onCancel: { isBikeCatalogPresented = false },
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
            .presentationDetents([.large])
        }
        .sheet(isPresented: $isDetailsSheetPresented, onDismiss: {
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
                }
            )
            .presentationDetents([.large])
        }
        .fullScreenCover(item: $rentalDetailsContext, onDismiss: {
            viewModel.closeRentalDetails()
        }) { context in
            AdminRentalDetailsScreen(
                details: viewModel.selectedRentalDetails,
                fallbackSummary: currentSummary(for: context),
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
            rentalId: startupRentalDeepLink.rentalId
        )
        viewModel.openRentalDetails(rentalId: startupRentalDeepLink.rentalId)
    }

    private func currentSummary(for context: RentalDetailsContext) -> AdminClientSummaryResponse? {
        guard case let .loaded(clients) = viewModel.state else { return nil }
        return clients.first(where: { $0.clientId == context.clientId && $0.rentalId == context.rentalId })
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

            adminBottomTabBar
                .zIndex(5)

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
            Text("All rent's")
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
            HStack(spacing: 76) {
                VStack(spacing: 12) {
                    Image(systemName: "house.fill")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
                    Circle()
                        .fill(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
                        .frame(width: 6, height: 6)
                }
                .frame(width: 44)

                Button {
                    isServiceSheetPresented = true
                } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 26, weight: .regular))
                        .foregroundStyle(AppDesign.iconSoft)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 18)
            .padding(.bottom, 28)
            .background(.ultraThinMaterial)
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(Color(red: 218 / 255, green: 218 / 255, blue: 218 / 255))
                    .frame(height: 1)
            }
        }
        .ignoresSafeArea(edges: .bottom)
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
                    isClientCatalogPresented = true
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
        HStack(alignment: .center, spacing: 8) {
            Button {
                pipelineMenuClientId = client.clientId
            } label: {
                bikeAvatar(urlString: client.bikeAvatarUrl, borderColor: avatarBorderColor(for: client))
            }
            .buttonStyle(.plain)
            .popover(
                isPresented: Binding(
                    get: { pipelineMenuClientId == client.clientId },
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
                Text(client.fullName)
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
                ignoredNextTapClientId = client.clientId
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
        .accessibilityIdentifier("admin.rent.card.\(client.clientId)")
        .onTapGesture {
            if ignoredNextTapClientId == client.clientId {
                ignoredNextTapClientId = nil
                return
            }
            guard let rentalId = client.rentalId else {
                return
            }
            rentalDetailsContext = RentalDetailsContext(clientId: client.clientId, rentalId: rentalId)
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
    let isLoading: Bool
    let errorMessage: String?
    let isOperationInProgress: Bool
    let onClose: () -> Void
    let onRetry: () -> Void
    let onOpenClientCard: () -> Void
    let onAdjustDebt: (_ clientId: String, _ clientName: String, _ currentDebtRub: Int) -> Void
    let onFinishRental: (_ clientId: String, _ rentalId: String) -> Void
    let onDeleteRental: (_ clientId: String, _ rentalId: String) -> Void

    @State private var isDeleteDialogPresented = false

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
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 12) {
                        topBar
                            .padding(.top, 8)

                        rentalCard

                        Text("ЖУРНАЛ")
                            .font(.system(size: 11, weight: .bold))
                            .tracking(0.88)
                            .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
                            .padding(.horizontal, 1)
                            .padding(.top, 8)

                        VStack(spacing: 8) {
                            ForEach(journalRows) { row in
                                journalRow(row)
                            }
                        }
                    }
                    .padding(.horizontal, 23)
                    .padding(.bottom, 128)
                }
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
                guard let clientId, let rentalId else { return }
                onDeleteRental(clientId, rentalId)
            }
            Button("Отмена", role: .cancel) {}
        }
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
                    Text("Корректировка: \(formattedRub(totalAdjustmentRub))")
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
                metricColumn(title: "ОПЛАЧЕНО", value: "+\(formattedRub(totalPaidRub))", color: Color(red: 35 / 255, green: 143 / 255, blue: 71 / 255))
                Spacer(minLength: 0)
                metricColumn(title: "ДОЛГ", value: formattedRub(debtRub), color: Color(red: 214 / 255, green: 48 / 255, blue: 52 / 255))
                Spacer(minLength: 0)
                metricColumn(title: "КОРРЕКТ.", value: formattedRub(totalAdjustmentRub), color: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                Spacer(minLength: 0)
                metricColumn(title: "ОПЛАЧ. ДО", value: paidUntilText, color: Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
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
                credentialLine(title: "ЛОГИН", value: details?.clientLogin)
                credentialLine(title: "ПАРОЛЬ", value: details?.clientPassword)
            }
            HStack(spacing: 8) {
                Button("Сгенерировать") {}
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 110, height: 47)
                    .background(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))
                    .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    .buttonStyle(.plain)

                Button(action: {}) {
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

    private func credentialLine(title: String, value: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255))
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(Color(red: 211 / 255, green: 215 / 255, blue: 221 / 255))
                .frame(width: 150, height: 13)
                .overlay(alignment: .leading) {
                    if let value, !value.isEmpty {
                        Text(value)
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                            .padding(.horizontal, 4)
                            .lineLimit(1)
                    }
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
        HStack(spacing: 8) {
            Button {
                guard let clientId else { return }
                onAdjustDebt(clientId, clientName, debtRub)
            } label: {
                Text("+ Корректировка")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(clientId == nil || isOperationInProgress)

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
            .disabled(clientId == nil || rentalId == nil || !rentalIsActive || isOperationInProgress)
            .opacity((clientId == nil || rentalId == nil || !rentalIsActive) ? 0.6 : 1)
        }
        .frame(width: max(UIScreen.main.bounds.width - 16, 0))
    }

    private var clientId: String? {
        details?.clientId ?? fallbackSummary?.clientId
    }

    private var rentalId: String? {
        details?.rentalId ?? fallbackSummary?.rentalId
    }

    private var clientName: String {
        details?.clientFullName ?? fallbackSummary?.fullName ?? "Клиент"
    }

    private var bikeTitle: String {
        if let bikeModel = details?.bikeModel, !bikeModel.isEmpty {
            return "№1  \(bikeModel)"
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
    private var rentalIsActive: Bool { details?.rentalIsActive ?? fallbackSummary?.rentalIsActive ?? false }

    private var avatarBorderColor: Color {
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

    private var journalRows: [AdminRentalJournalEntry] {
        guard let details else { return [] }
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

private struct AdminServiceSheet: View {
    let onOpenClientsCatalog: () -> Void
    let onOpenBikeCatalog: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                serviceActionCard(
                    title: "Клиенты",
                    subtitle: "Список всех клиентов и редактирование",
                    icon: "person.2.fill",
                    action: onOpenClientsCatalog
                )
                .accessibilityIdentifier("admin.service.clientsCatalogButton")

                serviceActionCard(
                    title: "Велосипеды",
                    subtitle: "Список всех велосипедов и базовое редактирование",
                    icon: "list.bullet.rectangle",
                    action: onOpenBikeCatalog
                )
                .accessibilityIdentifier("admin.service.bikesCatalogButton")
                Spacer()
            }
            .padding(16)
            .background(AppDesign.pageBackground.ignoresSafeArea())
            .navigationTitle("Сервис")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Закрыть") { dismiss() }
                }
            }
        }
    }

    private func serviceActionCard(
        title: String,
        subtitle: String,
        icon: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(AppDesign.accent)
                    .frame(width: 40, height: 40)
                    .background(AppDesign.surfaceBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(AppDesign.titleText)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(AppDesign.subtleText)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppDesign.subtleText)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppDesign.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct CreateClientSheet: View {
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload) -> Void

    @State private var fullName = ""
    @State private var address = ""
    @State private var passportData = ""
    @State private var phones: [CreateClientPhoneDraft] = [
        CreateClientPhoneDraft(label: "Рабочий (TG)", number: "")
    ]
    @State private var validationError: String?

    var body: some View {
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createClient.validationError")
                    }
                }

                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createClient.apiError")
                    }
                }

                Section("Профиль") {
                    TextField("ФИО", text: $fullName)
                        .accessibilityIdentifier("createClient.fullNameField")
                    TextField("Адрес", text: $address)
                        .accessibilityIdentifier("createClient.addressField")
                    TextField("Паспортные данные", text: $passportData)
                        .accessibilityIdentifier("createClient.passportField")
                }

                Section("Телефоны") {
                    ForEach(Array(phones.indices), id: \.self) { index in
                        TextField("Подпись", text: $phones[index].label)
                            .accessibilityIdentifier(
                                index == 0
                                    ? "createClient.phoneLabel1Field"
                                    : "createClient.phoneLabelField.\(index)"
                            )
                        TextField("Телефон", text: $phones[index].number)
                            .keyboardType(.phonePad)
                            .accessibilityIdentifier(
                                index == 0
                                    ? "createClient.phoneNumber1Field"
                                    : "createClient.phoneNumberField.\(index)"
                            )
                    }
                    Button("Добавить телефон") {
                        phones.append(CreateClientPhoneDraft(label: "", number: ""))
                    }
                    .accessibilityIdentifier("createClient.addPhoneButton")
                }

            }
            .navigationTitle("Новый клиент")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createClient.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createClient.submitButton")
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

    var body: some View {
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                    }
                }

                Section("Фото велосипеда") {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Text("Выбрать фото из галереи")
                    }
                    .accessibilityIdentifier("createBike.photoPicker")
                    if let selectedPhotoPreview {
                        selectedPhotoPreview
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Section("Параметры велосипеда") {
                    TextField("Модель велосипеда", text: $bikeModel)
                        .accessibilityIdentifier("createBike.modelField")
                    TextField("Стоимость недели аренды, ₽", text: $weeklyRateRub)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("createBike.weeklyRateField")
                    TextField("Серийный номер рамы", text: $frameSerialNumber)
                        .accessibilityIdentifier("createBike.frameSerialField")
                    TextField("Серийный номер мотора", text: $motorSerialNumber)
                        .accessibilityIdentifier("createBike.motorSerialField")
                    TextField("Серийный номер аккумулятора 1", text: $batterySerialNumber1)
                        .accessibilityIdentifier("createBike.battery1Field")
                    TextField("Серийный номер аккумулятора 2", text: $batterySerialNumber2)
                        .accessibilityIdentifier("createBike.battery2Field")
                }
            }
            .navigationTitle("Новый велосипед")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена", action: onCancel)
                        .disabled(isSaving)
                        .accessibilityIdentifier("createBike.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") { submit() }
                        .disabled(isSaving)
                        .accessibilityIdentifier("createBike.submitButton")
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
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

private struct ClientCatalogSheet: View {
    let clients: [AdminClientSummaryResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateClientPayload, @escaping () -> Void) -> Void
    let onOpenClient: (AdminClientSummaryResponse) -> Void

    @State private var isCreateClientPresented = false

    var body: some View {
        NavigationStack {
            Group {
                if clients.isEmpty {
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
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
                } else {
                    List {
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Section {
                                Text(apiErrorMessage)
                                    .foregroundStyle(AppDesign.danger)
                            }
                        }

                        ForEach(clients) { client in
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
                    .scrollContentBackground(.hidden)
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("Клиенты")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { onCancel() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Новый клиент") {
                        isCreateClientPresented = true
                    }
                    .accessibilityIdentifier("clientCatalog.addClientButton")
                }
            }
        }
        .sheet(isPresented: $isCreateClientPresented) {
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
            .presentationDetents([.large])
        }
    }
}

private struct BikeCatalogSheet: View {
    let bikes: [AdminBikeResponse]
    let isSaving: Bool
    let apiErrorMessage: String?
    let onCancel: () -> Void
    let onCreate: (CreateBikePayload, @escaping () -> Void) -> Void
    let onSave: (UpdateBikePayload) -> Void
    let onDelete: (String) -> Void

    @State private var editingBike: AdminBikeResponse?
    @State private var bikePendingDeletion: AdminBikeResponse?
    @State private var isCreateBikePresented = false

    var body: some View {
        NavigationStack {
            Group {
                if bikes.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bicycle")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(AppDesign.iconSoft)
                        Text("Список велосипедов пуст")
                            .font(.headline)
                            .foregroundStyle(AppDesign.titleText)
                        Text("Создайте первый велосипед внутри этого списка.")
                            .font(.subheadline)
                            .foregroundStyle(AppDesign.subtleText)
                            .multilineTextAlignment(.center)
                        Button("Создать велосипед") {
                            isCreateBikePresented = true
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("bikeCatalog.emptyCreateBikeButton")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
                } else {
                    List {
                        if let apiErrorMessage, !apiErrorMessage.isEmpty {
                            Section {
                                Text(apiErrorMessage)
                                    .foregroundStyle(AppDesign.danger)
                            }
                        }

                        ForEach(bikes) { bike in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(alignment: .top, spacing: 12) {
                                    bikePreview(urlString: bike.photoUrl)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(bike.bikeModel)
                                            .font(.headline)
                                            .foregroundStyle(AppDesign.titleText)
                                        Text("\(bike.weeklyRateRub) ₽ / неделя")
                                            .font(.subheadline)
                                            .foregroundStyle(AppDesign.subtleText)
                                    }
                                    Spacer()
                                    Button("Редактировать") {
                                        editingBike = bike
                                    }
                                    .buttonStyle(.bordered)
                                    .accessibilityIdentifier("bikeCatalog.edit.\(bike.bikeModel)")

                                    Button("Удалить", role: .destructive) {
                                        bikePendingDeletion = bike
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(isSaving)
                                    .accessibilityIdentifier("bikeCatalog.delete.\(bike.bikeModel)")
                                }

                                Text("Рама: \(bike.frameSerialNumber)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                Text("Мотор: \(bike.motorSerialNumber)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                Text("АКБ1: \(bike.batterySerialNumber1)")
                                    .font(.caption)
                                    .foregroundStyle(AppDesign.subtleText)
                                if let battery2 = bike.batterySerialNumber2, !battery2.isEmpty {
                                    Text("АКБ2: \(battery2)")
                                        .font(.caption)
                                        .foregroundStyle(AppDesign.subtleText)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                }
            }
            .navigationTitle("Велосипеды")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { onCancel() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Новый велосипед") {
                        isCreateBikePresented = true
                    }
                    .accessibilityIdentifier("bikeCatalog.addBikeButton")
                }
            }
        }
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
        .sheet(item: $editingBike) { bike in
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
            .presentationDetents([.large])
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

    private func bikePreview(urlString: String?) -> some View {
        BikePhotoView(source: urlString) {
            placeholder
        }
        .frame(width: 58, height: 58)
        .background(AppDesign.surfaceBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var placeholder: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(14)
            .foregroundStyle(AppDesign.iconSoft)
    }
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
        NavigationStack {
            Form {
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
                if let apiErrorMessage, !apiErrorMessage.isEmpty {
                    Section {
                        Text(apiErrorMessage)
                            .foregroundStyle(AppDesign.danger)
                    }
                }

                Section("Фото велосипеда") {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Text("Заменить фото")
                    }
                    .accessibilityIdentifier("editBike.photoPicker")
                    if let selectedPhotoPreview {
                        selectedPhotoPreview
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Section("Параметры велосипеда") {
                    TextField("Модель велосипеда", text: $bikeModel)
                        .accessibilityIdentifier("editBike.modelField")
                    TextField("Стоимость недели аренды, ₽", text: $weeklyRateRub)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("editBike.weeklyRateField")
                    TextField("Серийный номер рамы", text: $frameSerialNumber)
                        .accessibilityIdentifier("editBike.frameSerialField")
                    TextField("Серийный номер мотора", text: $motorSerialNumber)
                        .accessibilityIdentifier("editBike.motorSerialField")
                    TextField("Серийный номер аккумулятора 1", text: $batterySerialNumber1)
                        .accessibilityIdentifier("editBike.battery1Field")
                    TextField("Серийный номер аккумулятора 2", text: $batterySerialNumber2)
                        .accessibilityIdentifier("editBike.battery2Field")
                }
            }
            .navigationTitle("Редактировать велосипед")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена", action: onCancel)
                        .disabled(isSaving)
                        .accessibilityIdentifier("editBike.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Сохранить") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("editBike.submitButton")
                }
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task { await loadPhoto(item: newItem) }
        }
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

private struct AdminClientDetailsSheet: View {
    let details: AdminClientDetailsResponse?
    let isLoading: Bool
    let errorMessage: String?
    let operationErrorMessage: String?
    let operationSuccessMessage: String?
    let isOperationInProgress: Bool
    let clients: [AdminClientSummaryResponse]
    let bikes: [AdminBikeResponse]
    let onRetry: () -> Void
    let onAdjustDebtTap: (AdminClientDetailsResponse) -> Void
    let onSaveRentalComment: (String, String, String) -> Void
    let onSaveRentalLinks: (String, String, String?, String?) -> Void
    let onSaveClientProfile: (String, UpdateClientProfilePayload) -> Void
    let onDeleteClient: (String) -> Void
    let onCreateRental: (CreateRentalPayload) -> Void
    let onUpdateRental: (UpdateRentalPayload) -> Void
    let onDeleteRental: (String, String) -> Void

    @Environment(\.openURL) private var openURL
    @State private var isProfileEditorPresented = false
    @State private var isCreateRentalPresented = false
    @State private var isDeleteClientConfirmationPresented = false

    var body: some View {
        NavigationStack {
            Group {
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
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            header(details)
                            profileSection(details)
                            financialSection(details)
                            rentalsSection(details)
                            if let operationErrorMessage {
                                Text(operationErrorMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.danger)
                            }
                            if let operationSuccessMessage {
                                Text(operationSuccessMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(AppDesign.success)
                            }
                        }
                        .padding(16)
                    }
                    .background(AppDesign.pageBackground.ignoresSafeArea())
                    .sheet(isPresented: $isProfileEditorPresented) {
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
                        .presentationDetents([.large])
                    }
                    .sheet(isPresented: $isCreateRentalPresented) {
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
            .navigationTitle("Клиент")
        }
    }

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

            Button("Скорректировать долг") {
                onAdjustDebtTap(details)
            }
            .buttonStyle(.borderedProminent)
            .tint(details.debtRub > 0 ? AppDesign.danger : AppDesign.accent)
            .padding(.top, 4)
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
        let initialClientId = preselectedClientId ?? clients.first?.clientId ?? ""
        let initialBikeId = bikes.first?.bikeId ?? ""
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
        NavigationStack {
            Form {
                Section("Клиент и велосипед") {
                    if clients.isEmpty {
                        Text("Сначала создайте клиента в сервисном экране")
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Клиент", selection: $selectedClientId) {
                            ForEach(clients) { client in
                                Text(client.fullName).tag(client.clientId)
                            }
                        }
                        .accessibilityIdentifier("createRental.clientPicker")
                    }

                    if bikes.isEmpty {
                        Text("Сначала создайте велосипед в сервисном экране")
                            .foregroundStyle(AppDesign.subtleText)
                    } else {
                        Picker("Велосипед", selection: $selectedBikeId) {
                            ForEach(bikes) { bike in
                                Text("\(bike.bikeModel) • \(bike.weeklyRateRub) ₽/нед").tag(bike.bikeId)
                            }
                        }
                        .accessibilityIdentifier("createRental.bikePicker")
                    }

                    TextField("Дата начала (YYYY-MM-DD)", text: $periodStart)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.periodStartField")
                    TextField("Дата окончания (необязательно)", text: $periodEnd)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.periodEndField")
                }

                Section("Доступ клиента") {
                    TextField("Логин клиента", text: $login)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.loginField")
                    SecureField("Пароль клиента", text: $password)
                        .accessibilityIdentifier("createRental.passwordField")
                }

                Section("Документы и комментарий") {
                    TextField("Ссылка на видео", text: $videoUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.videoUrlField")
                    TextField("Ссылка на договор", text: $contractUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("createRental.contractUrlField")
                    TextField("Комментарий", text: $comment, axis: .vertical)
                        .lineLimit(3, reservesSpace: true)
                        .accessibilityIdentifier("createRental.commentField")
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                            .accessibilityIdentifier("createRental.validationError")
                    }
                }
            }
            .navigationTitle("Новая аренда")
            .onChange(of: selectedClientId) { newClientId in
                if let suggestedLogin = clients.first(where: { $0.clientId == newClientId })?.clientLogin,
                   !suggestedLogin.isEmpty {
                    login = suggestedLogin
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        onCancel()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createRental.cancelButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isSaving ? "Сохраняем..." : "Создать") {
                        submit()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("createRental.submitButton")
                }
            }
        }
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

    @State private var fullName: String
    @State private var address: String
    @State private var passportData: String
    @State private var phones: [EditClientProfilePhone]
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
        NavigationStack {
            Form {
                Section("Профиль") {
                    TextField("ФИО", text: $fullName)
                    TextField("Адрес", text: $address)
                    TextField("Паспортные данные", text: $passportData)
                }

                Section("Телефоны") {
                    ForEach($phones) { $phone in
                        VStack(alignment: .leading, spacing: 6) {
                            TextField("Подпись", text: $phone.label)
                            TextField("Телефон", text: $phone.number)
                                .keyboardType(.phonePad)
                        }
                    }

                    Button("Добавить телефон") {
                        phones.append(
                            EditClientProfilePhone(
                                id: UUID().uuidString,
                                label: "",
                                number: ""
                            )
                        )
                    }
                    .buttonStyle(.bordered)
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(AppDesign.danger)
                    }
                }
            }
            .navigationTitle("Редактировать клиента")
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
        onDeleteRental: @escaping (String, String) -> Void
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
