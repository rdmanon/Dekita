import SwiftUI
import UIKit
import SharedLogic

/// Colors from the iOS artboards. Light values are the design's; dark values keep the same hue
/// at a contrast of at least 4.5:1 and need designer review, as the design has no dark mode.
enum Palette {
    static let card = Color(.secondarySystemGroupedBackground)
    static let screen = Color(.systemGroupedBackground)
    static let tileIcon = Color(light: 0xFDE8DD, dark: 0x4A2A1C)
    /// The filled call-to-action color (#C2410C). White text on it is 5.2:1.
    static let action = Color(light: 0xC2410C, dark: 0xC2410C)
    /// Tinted icons and links (#A93A0B), such as the menu row's add button. 6.4:1 on white.
    static let link = Color(light: 0xA93A0B, dark: 0xFFB59A)
    /// Inline errors and the error card's text.
    static let error = Color(light: 0xB3261E, dark: 0xFFB4AB)

    struct BadgeColors {
        let background: Color
        let foreground: Color
    }

    static func badge(_ badge: StoreBadge) -> BadgeColors {
        switch badge {
        case .openForLunch:
            return BadgeColors(background: Color(light: 0xE3F5E9, dark: 0x1E3B2A), foreground: Color(light: 0x1B6B3A, dark: 0x9BE3B4))
        case .pickupOnly:
            return BadgeColors(background: Color(light: 0xE5EEFF, dark: 0x1E2A4A), foreground: Color(light: 0x1D4ED8, dark: 0xA9C4FF))
        case .inStoreOnly:
            return BadgeColors(background: Color(light: 0xF1E8FF, dark: 0x34204A), foreground: Color(light: 0x6B21A8, dark: 0xDDC2FF))
        case .full:
            return BadgeColors(background: Color(light: 0xFFE5E3, dark: 0x4A1E1C), foreground: Color(light: 0xB3261E, dark: 0xFFB4AB))
        default: // .paused
            return BadgeColors(background: Color(light: 0xECECEE, dark: 0x3A3A3C), foreground: Color(light: 0x545458, dark: 0xC7C7CC))
        }
    }
}

extension Color {
    /// A color that follows light and dark mode, from 0xRRGGBB values.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
