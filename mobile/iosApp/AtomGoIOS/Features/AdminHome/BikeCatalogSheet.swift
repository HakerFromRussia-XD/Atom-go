import SwiftUI
import PhotosUI
import UIKit

struct BikeCatalogSheet: View {
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
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    private let athensGray = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    private let blackHaze = Color(red: 250 / 255, green: 251 / 255, blue: 251 / 255)
    private let alto = Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255)
    private let horizontalInset: CGFloat = 8
    private let topBarHeight: CGFloat = 62
    private let searchTopPadding: CGFloat = 6
    private let searchHeight: CGFloat = 46
    private let chipsTopGap: CGFloat = 10
    private let chipsHeight: CGFloat = 36
    private let tabBarHeight: CGFloat = 76

    private var searchTop: CGFloat { topBarHeight + searchTopPadding }
    private var searchMaskHeight: CGFloat { searchTop + searchHeight / 2 }
    private var chipsTop: CGFloat { searchTop + searchHeight + chipsTopGap }
    private var chipsBottom: CGFloat { chipsTop + chipsHeight }
    private var cardsInitialTop: CGFloat { chipsBottom - 4 }

    private struct BikeCatalogRuntimeSnapshot {
        let hasActiveRental: Bool
        let activeCount: Int
        let totalDebtRub: Int
        let borderColor: Color
        let activeRental: AdminClientSummaryResponse?

        static let idle = BikeCatalogRuntimeSnapshot(
            hasActiveRental: false,
            activeCount: 0,
            totalDebtRub: 0,
            borderColor: Color(red: 203 / 255, green: 48 / 255, blue: 224 / 255),
            activeRental: nil
        )
    }

    private struct BikeCatalogProjection {
        let visibleBikes: [AdminBikeResponse]
        let allCount: Int
        let freeCount: Int
        let rentedCount: Int
        let snapshotsByBikeId: [String: BikeCatalogRuntimeSnapshot]

        func snapshot(for bike: AdminBikeResponse) -> BikeCatalogRuntimeSnapshot {
            snapshotsByBikeId[bike.bikeId] ?? .idle
        }
    }

    var body: some View {
        let projection = makeProjection()

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
                                    value: proxy.frame(in: .named("bikeCatalogPipeline")).maxY
                                )
                            }
                        }
                        .allowsHitTesting(false)

                    VStack(alignment: .leading, spacing: 0) {
                        if projection.visibleBikes.isEmpty {
                            bikeEmptyState
                                .padding(.top, 14)
                        } else {
                            LazyVStack(spacing: 0) {
                                ForEach(Array(projection.visibleBikes.enumerated()), id: \.element.bikeId) { index, bike in
                                    bikeRow(bike, runtime: projection.snapshot(for: bike))
                                    if index < projection.visibleBikes.count - 1 {
                                        Divider()
                                            .overlay(Color(red: 234 / 255, green: 234 / 255, blue: 240 / 255))
                                    }
                                }
                            }
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

            bikeChipRows(projection: projection)
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
        .fullScreenCover(isPresented: $isCreateBikePresented) {
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
        .onChange(of: apiErrorMessage) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private func bikeChipRows(projection: BikeCatalogProjection) -> some View {
        HStack(spacing: 8) {
            bikeFilterChip(.all, count: projection.allCount)
            bikeFilterChip(.free, count: projection.freeCount)
            bikeFilterChip(.rented, count: projection.rentedCount)
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
            if showsCloseButton {
                headerIconButton(
                    systemName: "xmark",
                    accessibilityIdentifier: "bikeCatalog.closeButton",
                    action: onCancel
                )
            } else {
                headerIconButton(
                    assetName: "exit",
                    assetSize: 16,
                    accessibilityIdentifier: "bikeCatalog.logoutButton",
                    action: onCancel
                )
            }

            Spacer()

            Text("Велосипеды")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

            Spacer()

            headerIconButton(
                assetName: "plus",
                assetSize: 16,
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
        .accessibilityIdentifier("bikeCatalog.searchField")
    }

    private func makeProjection() -> BikeCatalogProjection {
        let activeRentalsByBikeModel = Dictionary(
            grouping: rentals.filter(\.rentalIsActive),
            by: { normalizedSearchText($0.bikeModel) }
        )
        var snapshotsByBikeId: [String: BikeCatalogRuntimeSnapshot] = [:]
        snapshotsByBikeId.reserveCapacity(bikes.count)

        for bike in bikes {
            let activeRentals = activeRentalsByBikeModel[normalizedSearchText(bike.bikeModel)] ?? []
            snapshotsByBikeId[bike.bikeId] = runtimeSnapshot(activeRentals: activeRentals)
        }

        let normalizedQuery = normalizedSearchText(searchText)
        let searched = bikes.filter { bike in
            normalizedQuery.isEmpty || bikeMatchesSearch(bike, query: normalizedQuery)
        }
        let filtered: [AdminBikeResponse]
        switch selectedFilter {
        case .all:
            filtered = searched
        case .free:
            filtered = searched.filter { !(snapshotsByBikeId[$0.bikeId]?.hasActiveRental ?? false) }
        case .rented:
            filtered = searched.filter { snapshotsByBikeId[$0.bikeId]?.hasActiveRental ?? false }
        }

        let visibleBikes = filtered.sorted {
            $0.bikeModel.localizedCaseInsensitiveCompare($1.bikeModel) == .orderedAscending
        }
        let rentedCount = snapshotsByBikeId.values.reduce(0) { partial, snapshot in
            partial + (snapshot.hasActiveRental ? 1 : 0)
        }

        return BikeCatalogProjection(
            visibleBikes: visibleBikes,
            allCount: bikes.count,
            freeCount: max(0, bikes.count - rentedCount),
            rentedCount: rentedCount,
            snapshotsByBikeId: snapshotsByBikeId
        )
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

    private func bikeRow(_ bike: AdminBikeResponse, runtime: BikeCatalogRuntimeSnapshot) -> some View {
        return Button {
            editingBike = bike
        } label: {
            HStack(spacing: 8) {
                bikeThumb(bike: bike, borderColor: runtime.borderColor)

                VStack(alignment: .leading, spacing: 4) {
                    Text(bike.bikeModel)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255))
                        .lineLimit(1)

                    Text(bikeSubtitle(for: bike, runtime: runtime))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color(red: 17 / 255, green: 24 / 255, blue: 39 / 255).opacity(0.5))
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)

                Text("\(formattedCompactRub(bike.weeklyRateRub)) ₽/нед")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255))
                    .fixedSize(horizontal: true, vertical: false)

                Image(systemName: "chevron.right")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Color(red: 167 / 255, green: 167 / 255, blue: 171 / 255))
            }
            .padding(.horizontal, 9)
            .frame(minHeight: 67)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Редактировать") {
                editingBike = bike
            }
            if !runtime.hasActiveRental && runtime.totalDebtRub == 0 {
                Button("Удалить", role: .destructive) {
                    bikePendingDeletion = bike
                }
            }
        }
        .disabled(isSaving)
        .accessibilityIdentifier("bikeCatalog.edit.\(bike.bikeModel)")
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

    private func bikeThumb(bike: AdminBikeResponse, borderColor: Color) -> some View {
        BikePhotoView(source: bike.photoUrl) {
            placeholder
        }
            .frame(width: 59, height: 59)
            .background(Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(borderColor, lineWidth: 3)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func runtimeSnapshot(activeRentals: [AdminClientSummaryResponse]) -> BikeCatalogRuntimeSnapshot {
        let hasActiveRental = !activeRentals.isEmpty
        let totalDebt = activeRentals.reduce(0) { partial, item in
            partial + max(0, item.debtRub)
        }
        let hasSoonReturn = activeRentals.contains { $0.rentalPipelineStatus == "soon_return" }
        let activeRental = activeRentals.first
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
            borderColor: borderColor,
            activeRental: activeRental
        )
    }

    private func bikeSubtitle(for bike: AdminBikeResponse, runtime: BikeCatalogRuntimeSnapshot) -> String {
        guard let rental = runtime.activeRental else {
            return "-"
        }

        let clientName = rental.fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let namePart = clientName.isEmpty ? "Клиент" : clientName
        switch rental.rentalPipelineStatus {
        case "soon_return":
            return "\(namePart) · вернут в течении нед."
        default:
            if let paidUntil = shortPaidUntilText(for: rental) {
                return "\(namePart) · до \(paidUntil)"
            }
            return "\(namePart) · долгосрочно"
        }
    }

    private func shortPaidUntilText(for rental: AdminClientSummaryResponse) -> String? {
        guard
            let paidUntil = rental.paidUntil?.trimmingCharacters(in: .whitespacesAndNewlines),
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

    private func bikeMatchesSearch(_ bike: AdminBikeResponse, query: String) -> Bool {
        normalizedSearchText(bike.bikeModel).contains(query)
            || normalizedSearchText(bike.frameSerialNumber).contains(query)
            || normalizedSearchText(bike.motorSerialNumber).contains(query)
            || normalizedSearchText(bike.batterySerialNumber1).contains(query)
            || normalizedSearchText(bike.batterySerialNumber2 ?? "").contains(query)
    }

    private func normalizedSearchText(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private func formattedCompactRub(_ amount: Int) -> String {
        let formatted = Self.rubFormatter.string(from: NSNumber(value: max(0, amount))) ?? "\(max(0, amount))"
        return formatted.replacingOccurrences(of: "\u{00A0}", with: " ")
    }

    private var placeholder: some View {
        Image(systemName: "bicycle")
            .resizable()
            .scaledToFit()
            .padding(14)
            .foregroundStyle(AppDesign.iconSoft)
    }

    private static let rubFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter
    }()

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

    private static let ruShortMonths = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]
}
