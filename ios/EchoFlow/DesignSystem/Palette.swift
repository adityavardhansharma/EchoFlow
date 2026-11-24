// Sketch only: not part of a buildable project yet. See ios/README.md.
//
// The six Android palettes become accent tints. Surfaces stay system colours so
// Liquid Glass picks up the right content behind it in light and dark.

import SwiftUI

enum Palette: String, CaseIterable, Identifiable {
    case mono, ocean, forest, sunset, lavender, rose

    var id: String { rawValue }

    var title: String {
        switch self {
        case .mono: "Mono"
        case .ocean: "Ocean"
        case .forest: "Forest"
        case .sunset: "Sunset"
        case .lavender: "Lavender"
        case .rose: "Rose"
        }
    }

    /// Tint for the one primary action on a screen: each palette's primary from the Android
    /// theme (ui/theme/Color.kt), so EchoFlow looks like itself on both platforms.
    var accent: Color {
        Color(light: lightHex, dark: darkHex)
    }

    private var lightHex: UInt32 {
        switch self {
        case .mono: 0x1B1B1F
        case .ocean: 0x1660A8
        case .forest: 0x1C6E2E
        case .sunset: 0x9A4500
        case .lavender: 0x6750A4
        case .rose: 0xB01B49
        }
    }

    private var darkHex: UInt32 {
        switch self {
        case .mono: 0xE3E2E9
        case .ocean: 0xA1C9FF
        case .forest: 0x8AD88C
        case .sunset: 0xFFB68C
        case .lavender: 0xD0BCFF
        case .rose: 0xFFB2BD
        }
    }
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}
