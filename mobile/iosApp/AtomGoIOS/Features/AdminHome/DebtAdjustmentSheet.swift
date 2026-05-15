import SwiftUI

struct DebtAdjustmentSheet: View {
    let context: DebtAdjustmentContext
    let isSaving: Bool
    let onCancel: () -> Void
    let onApply: (Int, DebtAdjustmentSign, String?) -> Void

    @State private var sign: DebtAdjustmentSign = .minus
    @State private var amountRub = ""
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

    private let mainText = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    private let subtleText = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
    private let sheetHandle = Color(red: 210 / 255, green: 213 / 255, blue: 218 / 255)
    private let segmentBg = Color(red: 237 / 255, green: 239 / 255, blue: 244 / 255)

    var body: some View {
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 999, style: .continuous)
                .fill(sheetHandle)
                .frame(width: 40, height: 4)
                .padding(.top, 14)

            header
                .padding(.horizontal, 23)
                .padding(.top, 12)
                .padding(.bottom, 14)

            segmentControl
                .padding(.horizontal, 23)
                .padding(.bottom, 14)

            amountField
                .padding(.horizontal, 23)
                .padding(.bottom, 16)

            applyButton
                .padding(.horizontal, 23)
                .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.white)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(mainText)
                .frame(height: 1)
                .opacity(0.2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private var header: some View {
        HStack(alignment: .center) {
            Text("Корректировка долга")
                .font(.system(size: 15.5, weight: .bold))
                .foregroundStyle(mainText)
                .lineLimit(1)

            Spacer(minLength: 12)

            Button(action: onCancel) {
                Text("Закрыть ✕")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(subtleText)
            }
            .buttonStyle(.plain)
        }
    }

    private var segmentControl: some View {
        HStack(spacing: 0) {
            segmentButton(title: "– Уменьшить", isSelected: sign == .minus) {
                sign = .minus
            }
            segmentButton(title: "+ Увеличить", isSelected: sign == .plus) {
                sign = .plus
            }
        }
        .background(segmentBg)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var amountField: some View {
        AtomGoInputField(
            label: "СУММА, ₽",
            placeholder: "введите...",
            text: $amountRub,
            keyboardType: .numberPad,
            textInputAutocapitalization: .never,
            accessibilityIdentifier: "debtAdjustment.amountInput",
            borderColor: mainText,
            autoFocus: true
        )
    }

    private var applyButton: some View {
        Button {
            submit()
        } label: {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(mainText)
                if isSaving {
                    ProgressView().tint(.white)
                } else {
                    Text("Применить")
                        .font(.system(size: 14, weight: .bold))
                        .tracking(0.28)
                        .foregroundStyle(Color.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 63)
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isSaving)
    }

    private func segmentButton(
        title: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                .foregroundStyle(isSelected ? mainText : subtleText)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background {
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .fill(isSelected ? Color.white : Color.clear)
                        .shadow(
                            color: isSelected ? Color.black.opacity(0.06) : Color.clear,
                            radius: 5,
                            x: 0,
                            y: 2
                        )
                }
                .overlay {
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .stroke(isSelected ? mainText : Color.clear, lineWidth: 1)
                }
                .padding(4)
        }
        .buttonStyle(.plain)
    }

    private func submit() {
        guard let amount = Int(amountRub.trimmingCharacters(in: .whitespacesAndNewlines)),
              amount > 0 else {
            presentToast("Введите положительную сумму")
            return
        }
        onApply(amount, sign, nil)
    }

    private func presentToast(_ message: String) {
        toastDismissTask?.cancel()
        toastMessage = message
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            toastMessage = nil
        }
    }
}
