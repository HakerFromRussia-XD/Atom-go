import SwiftUI

/// Поле ввода с полностью кликабельной областью.
/// Использует @FocusState внутри, чтобы тап в любом месте контейнера фокусировал TextField.
struct AtomGoInputField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    var isDashed: Bool = false
    var keyboardType: UIKeyboardType = .default
    var textInputAutocapitalization: TextInputAutocapitalization = .sentences
    var accessibilityIdentifier: String = ""
    var valueWeight: Font.Weight = .regular
    var borderColor: Color = AppDesign.darkControl
    var accentBorder: Bool = false   // true — синяя рамка AppDesign.accent
    var autoFocus: Bool = false

    @FocusState private var isFocused: Bool

    private let paleSky = AppDesign.paleSky
    private let ghost   = AppDesign.ghost

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .tracking(0.66)
                .textCase(.uppercase)
                .foregroundStyle(paleSky)
                .lineLimit(1)

            TextField("", text: $text, prompt: Text(placeholder).foregroundColor(ghost))
                .font(.system(size: 13, weight: valueWeight))
                .foregroundStyle(AppDesign.darkControl)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(textInputAutocapitalization)
                .autocorrectionDisabled()
                .accessibilityIdentifier(accessibilityIdentifier)
                .frame(maxWidth: .infinity)
                .focused($isFocused)
        }
        .padding(.horizontal, 19)
        .frame(height: 58)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppDesign.surfaceBackground)
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
        .onAppear {
            guard autoFocus else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                isFocused = true
            }
        }
        .overlay {
            if isDashed {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(
                        AppDesign.placeholderStroke,
                        style: StrokeStyle(lineWidth: 1.5, dash: [3, 2.5])
                    )
            } else if accentBorder {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(AppDesign.accent, lineWidth: 1.5)
            } else {
                RoundedRectangle(cornerRadius: 12.84, style: .continuous)
                    .stroke(borderColor, lineWidth: 1.5)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12.84, style: .continuous))
    }
}
