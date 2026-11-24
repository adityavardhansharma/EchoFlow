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

    /// Tint for the one primary action on a screen. Values stay close to the system colours
    /// (Ocean is system blue, Lavender is system indigo) so the app feels native.
    var accent: Color {
        Color(light: lightHex, dark: darkHex)
    }

    private var lightHex: UInt32 {
        switch self {
        case .mono: 0x000000
        case .ocean: 0x007AFF
        case .forest: 0x248A3D
        case .sunset: 0xE8590C
        case .lavender: 0x5856D6
        case .rose: 0xE0245E
        }
    }

    private var darkHex: UInt32 {
        switch self {
        case .mono: 0xFFFFFF
        case .ocean: 0x0A84FF
        case .forest: 0x30D158
        case .sunset: 0xFF9F0A
        case .lavender: 0x7D7AFF
        case .rose: 0xFF375F
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
