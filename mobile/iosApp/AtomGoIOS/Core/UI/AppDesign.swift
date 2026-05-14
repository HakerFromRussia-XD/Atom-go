import SwiftUI

enum AppDesign {
    static let pageBackground = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    static let surfaceBackground = Color.white
    static let cardBackground = Color.white
    static let accent = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
    static let titleText = Color(red: 0.20, green: 0.20, blue: 0.30)
    static let subtleText = Color(red: 0.45, green: 0.45, blue: 0.50)
    static let iconSoft = Color(red: 0.70, green: 0.71, blue: 0.76)
    static let danger = Color(red: 0.82, green: 0.19, blue: 0.18)
    static let success = Color(red: 0.14, green: 0.56, blue: 0.28)

    static func poppinsMedium(size: CGFloat) -> Font {
        Font.custom("Poppins-Medium", size: size)
    }

    static func urbanistBold(size: CGFloat) -> Font {
        Font.custom("UrbanistRoman-Bold", size: size)
    }
}

struct AppToastModifier: ViewModifier {
    @Binding var message: String?
    var bottomPadding: CGFloat = 86

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message, !message.isEmpty {
                    Text(message)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color.black)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color.white.opacity(0.98))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
                        .padding(.bottom, bottomPadding)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                }
            }
            .animation(.easeInOut(duration: 0.18), value: message)
    }
}

extension View {
    func appToast(message: Binding<String?>, bottomPadding: CGFloat = 86) -> some View {
        modifier(AppToastModifier(message: message, bottomPadding: bottomPadding))
    }
}
