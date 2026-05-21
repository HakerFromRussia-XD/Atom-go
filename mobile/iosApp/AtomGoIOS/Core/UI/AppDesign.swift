import SwiftUI

enum AppDesign {
    static let pageBackground = Color("AppPageBackground")
    static let surfaceBackground = Color("AppSurfaceBackground")
    static let black = Color("AppBlack")
    static let clear = Color("AppClear")
    static let cardBackground = Color("AppCardBackground")
    static let darkControl = Color("AppDarkControl")
    static let darkText = Color("AppDarkText")
    static let titleText = Color("AppTitleText")
    static let subtleText = Color("AppSubtleText")
    static let iconSoft = Color("AppIconSoft")
    static let danger = Color("AppDangerRed")
    static let success = Color("AppSuccessGreen")
    static let paleSky = Color("AppPaleSky")
    static let blackHaze = Color("AppBlackHaze")
    static let athensGray = Color("AppAthensGray")
    static let screenBackground = Color("AppScreenBackground")
    static let placeholder = Color("AppPlaceholder")
    static let placeholderStroke = Color("AppPlaceholderStroke")
    static let ghost = Color("AppGhost")
    static let disabledText = Color("AppDisabledText")
    static let markerSoft = Color("AppMarkerSoft")
    static let sheetHandle = Color("AppSheetHandle")
    static let sheetHandleAlt = Color("AppSheetHandleAlt")
    static let segmentBackground = Color("AppSegmentBackground")
    static let inputFill = Color("AppInputFill")
    static let iconFill = Color("AppIconFill")
    static let iconStroke = Color("AppIconStroke")
    static let iconCanvas = Color("AppIconCanvas")
    static let chevron = Color("AppChevron")
    static let shadow = Color("AppShadow")
    static let idlePurple = Color("AppIdlePurple")
    static let warningYellow = Color("AppWarningYellow")
    static let textPrimary = Color("AppTextPrimary")
    static let titleBlack = Color("AppTitleBlack")
    static let bikeStroke = Color("AppBikeStroke")
    static let separator = Color("AppSeparator")
    static let selectedSoft = Color("AppSelectedSoft")
    static let selectedMuted = Color("AppSelectedMuted")
    static let accent = darkControl
    static let card = blackHaze
    static let borderSoft = athensGray
    static let primaryButton = darkControl
    static let debt = danger

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
                        .foregroundStyle(AppDesign.black)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(AppDesign.surfaceBackground.opacity(0.98))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .shadow(color: AppDesign.black.opacity(0.16), radius: 10, x: 0, y: 4)
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
