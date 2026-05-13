import SwiftUI
import PhotosUI
import UIKit

struct RentalHistoryCard: View {
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

