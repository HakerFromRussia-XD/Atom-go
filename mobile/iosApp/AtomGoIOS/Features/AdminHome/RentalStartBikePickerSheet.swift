import SwiftUI
import PhotosUI
import UIKit

struct RentalStartBikePickerSheet: View {
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

    /// Свободные велосипеды — те, что НЕ привязаны к активной lifecycle-аренде
    /// (`bike_is_in_rental == false`). Picker применяет фильтр сам, чтобы
    /// call-сайт не мог случайно показать занятый велосипед как выбираемый.
    /// См. docs/14_rental_lifecycle.md §1 — invariant «один bike — одна
    /// неудалённая lifecycle».
    private var availableBikes: [AdminBikeResponse] {
        bikes.filter { !$0.bikeIsInRental }
    }

    private var visibleBikes: [AdminBikeResponse] {
        let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let searched = availableBikes.filter { bike in
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
                    isDark: false,
                    accessibilityIdentifier: "rentalBikePicker.closeButton",
                    action: onClose
                )

                Spacer()

                Text("Велосипеды")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(red: 20 / 255, green: 23 / 255, blue: 24 / 255))

                Spacer()

                headerIconButton(
                    assetName: "ok",
                    assetSize: 16,
                    isDark: true,
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
                .stroke(AppDesign.accent, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
        .accessibilityIdentifier("rentalBikePicker.searchField")
    }

    private func headerIconButton(
        systemName: String,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? AppDesign.accent : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1.5)
                )
                .overlay(
                    Image(systemName: systemName)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(isDark ? Color.white : AppDesign.accent)
                )
                .frame(width: 47, height: 47)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private func headerIconButton(
        assetName: String,
        assetSize: CGFloat,
        isDark: Bool,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isDark ? AppDesign.accent : Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppDesign.accent, lineWidth: 1.5)
                )
                .overlay(
                    ZStack {
                        if isDark {
                            Image(assetName)
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: assetSize, height: assetSize)
                                .foregroundStyle(Color.white)
                        } else {
                            Image(assetName)
                                .renderingMode(.original)
                                .resizable()
                                .scaledToFit()
                                .frame(width: assetSize, height: assetSize)
                        }
                    }
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
