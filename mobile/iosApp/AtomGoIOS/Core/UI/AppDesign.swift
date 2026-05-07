import SwiftUI

enum AppDesign {
    static let pageBackground = Color(red: 247 / 255, green: 248 / 255, blue: 250 / 255)
    static let surfaceBackground = Color.white
    static let cardBackground = Color.white
    static let accent = Color(red: 0.04, green: 0.05, blue: 0.07)
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
