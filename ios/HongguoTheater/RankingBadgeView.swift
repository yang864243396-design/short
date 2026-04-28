import SwiftUI

struct RankingBadgeInfo: Hashable {
    let type: String
    let title: String
    let rank: Int

    var text: String { "\(title) 第\(rank)" }
}

struct RankingBadgeView: View {
    let info: RankingBadgeInfo
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(info.text)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color(red: 0.118, green: 0.106, blue: 0.294).opacity(0.5))
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(AppTheme.primary.opacity(0.6), lineWidth: 1)
                )
                .shadow(color: AppTheme.primary.opacity(0.4), radius: 3)
        }
        .buttonStyle(.plain)
    }
}

extension Drama {
    var preferredRankingBadge: RankingBadgeInfo? {
        guard let type = ranking?.list, let rank = ranking?.rank, rank > 0 else { return nil }
        switch type {
        case "hot":
            return RankingBadgeInfo(type: "hot", title: "热播榜", rank: rank)
        case "rising":
            return RankingBadgeInfo(type: "rising", title: "飙升榜", rank: rank)
        case "rating":
            return RankingBadgeInfo(type: "rating", title: "好评榜", rank: rank)
        default:
            return nil
        }
    }
}
