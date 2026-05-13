import SwiftUI
import PhotosUI
import UIKit

struct CarriedDebtOperationSheet: View {
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

