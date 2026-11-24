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

    /// Tint for the one primary action on a screen. Light values pass 4.5:1 with white
    /// text; dark values pass with near-black text.
    var accent: Color {
        Color(light: lightHex, dark: darkHex)
    }

    private var lightHex: UInt32 {
        switch self {
        case .mono: 0x1C1C1E
        case .ocean: 0x0062CC
        case .forest: 0x1E7B3A
        case .sunset: 0xC0420B
        case .lavender: 0x5E4BD1
        case .rose: 0xC2185B
        }
    }

    private var darkHex: UInt32 {
        switch self {
        case .mono: 0xF2F2F7
        case .ocean: 0x409CFF
        case .forest: 0x4CD97B
        case .sunset: 0xFF9F5A
        case .lavender: 0xA99BFF
        case .rose: 0xFF7FAE
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
