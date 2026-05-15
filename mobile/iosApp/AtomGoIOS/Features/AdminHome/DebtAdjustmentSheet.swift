import SwiftUI
import PhotosUI
import UIKit

struct DebtAdjustmentSheet: View {
    let context: DebtAdjustmentContext
    let isSaving: Bool
    let onCancel: () -> Void
    let onApply: (Int, DebtAdjustmentSign, String?) -> Void

    @State private var sign: DebtAdjustmentSign = .minus
    @State private var amountRub = ""
    @State private var comment = ""
    @State private var validationError: String?
    @State private var toastMessage: String?
    @State private var toastDismissTask: Task<Void, Never>?

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
        .onChange(of: validationError) { newValue in
            presentToast(newValue)
        }
        .appToast(message: $toastMessage, bottomPadding: 96)
    }

    private func submit() {
        validationError = nil
        guard let amount = Int(amountRub), amount > 0 else {
            validationError = "Введите положительную сумму"
            return
        }

        onApply(amount, sign, comment.trimmedToOptional)
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

/// Модал admin-операций над перенесённым долгом клиента.
/// Списать (без оплаты) или Принять оплату (наличные/перевод вне YooKassa).
/// Излишек payment-а автоматически уходит в активную клиентскую аренду —
/// в UI это видно по подсказке «Излишек уйдёт в активную аренду» и
/// в итоговом success-сообщении.
