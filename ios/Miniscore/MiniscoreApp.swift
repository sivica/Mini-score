import SwiftUI

@main
struct MiniscoreApp: App {
    @StateObject private var store = GameStore()
    var body: some Scene {
        WindowGroup { GamesView(store: store).tint(Palette.accent) }
    }
}

enum Palette {
    static let background = adaptive(light: 0xF7F8FA, dark: 0x1B1B21)
    static let surface = adaptive(light: 0xE9EDF0, dark: 0x28282F)
    static let header = adaptive(light: 0xF0F2F4, dark: 0x202026)
    static let text = adaptive(light: 0x202229, dark: 0xF6F6FA)
    static let secondary = adaptive(light: 0x4B515C, dark: 0xD6D6E0)
    static let accent = adaptive(light: 0x176456, dark: 0xCDB8F5)
    static let selection = adaptive(light: 0xE6DFF1, dark: 0x362D43)
    static let divider = adaptive(light: 0xCFD2D8, dark: 0x45454F)
    static let logo = Color(red: 98/255, green: 190/255, blue: 175/255)
    private static func adaptive(light: UInt, dark: UInt) -> Color {
        Color(uiColor: UIColor { traits in
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: Double((rgb >> 16) & 255)/255, green: Double((rgb >> 8) & 255)/255,
                           blue: Double(rgb & 255)/255, alpha: 1)
        })
    }
}

struct MiniscoreLogo: View {
    var size: CGFloat = 38
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.25).fill(Palette.logo)
            Image("ScoreMark").resizable().scaledToFit().padding(size * 0.22)
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Miniscore")
        .accessibilityAddTraits(.isImage)
    }
}
