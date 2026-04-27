import SwiftUI

/// 与 Android `colors.xml` 主色、深色面大致对齐
enum AppTheme {
    static let background = Color(red: 0.055, green: 0.055, blue: 0.055)
    static let surface = Color(red: 0.102, green: 0.102, blue: 0.102)
    static let surfaceHigh = Color(red: 0.125, green: 0.125, blue: 0.122)
    static let surfaceHighest = Color(red: 0.149, green: 0.149, blue: 0.149)
    static let primary = Color(red: 1, green: 0.24, blue: 0)
    static let primaryVariant = Color(red: 1, green: 0.365, blue: 0.18)
    static let primaryLight = Color(red: 1, green: 0.561, blue: 0.439)
    static let onSurface = Color.white
    static let onSurfaceVariant = Color(red: 0.678, green: 0.667, blue: 0.667)
    static let textHint = Color(red: 0.463, green: 0.459, blue: 0.459)
    static let outline = Color(red: 0.463, green: 0.459, blue: 0.459)
    static let outlineVariant = Color(red: 0.282, green: 0.282, blue: 0.278)
    static let error = Color(red: 1, green: 0.431, blue: 0.518)
    static let overlayDark = Color.black.opacity(0.7)
    static let feedTagFill = Color(red: 0.118, green: 0.106, blue: 0.294).opacity(0.35)
    static let walletAccent = Color(red: 1, green: 0.302, blue: 0)

    static let cardRadius: CGFloat = 14
    static let pillRadius: CGFloat = 22
    static let pagePadding: CGFloat = 16
}

struct HGCardModifier: ViewModifier {
    var radius: CGFloat = AppTheme.cardRadius
    var fill: Color = AppTheme.surface

    func body(content: Content) -> some View {
        content
            .background(fill)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

struct HGPillModifier: ViewModifier {
    var selected: Bool = false

    func body(content: Content) -> some View {
        content
            .font(.subheadline.weight(selected ? .semibold : .regular))
            .foregroundStyle(selected ? AppTheme.primary : AppTheme.onSurface)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(selected ? AppTheme.primary.opacity(0.2) : AppTheme.surfaceHigh)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(selected ? AppTheme.primary : AppTheme.outlineVariant, lineWidth: 1))
    }
}

struct HGInteractionButton: View {
    let icon: String
    var label: String? = nil
    var active: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(active ? AppTheme.primary : AppTheme.onSurface)
                    .frame(width: 46, height: 46)
                    .background(Circle().fill(Color.black.opacity(0.36)))
                if let label, !label.isEmpty {
                    Text(label)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

struct HGDramaCover: View {
    let url: URL?
    var width: CGFloat
    var height: CGFloat
    var radius: CGFloat = 8

    var body: some View {
        ZStack {
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty:
                        placeholder
                    case .failure:
                        placeholder
                    @unknown default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(
                colors: [AppTheme.surfaceHigh, AppTheme.surfaceHighest],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            VStack(spacing: 6) {
                Image(systemName: "play.rectangle.fill")
                    .font(.title3)
                    .foregroundStyle(AppTheme.primary.opacity(0.82))
                Text("暂无封面")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
    }
}

extension View {
    func hgCard(radius: CGFloat = AppTheme.cardRadius, fill: Color = AppTheme.surface) -> some View {
        modifier(HGCardModifier(radius: radius, fill: fill))
    }

    func hgPill(selected: Bool = false) -> some View {
        modifier(HGPillModifier(selected: selected))
    }
}
