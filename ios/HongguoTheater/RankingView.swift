import SwiftUI

struct RankingView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State var initialType: String = "hot"
    @State private var type: String = "hot"
    @State private var items: [RankItem] = []
    @State private var loading = false
    @State private var requestID = 0

    private let types: [(String, String)] = [
        ("hot", "热播榜"),
        ("rising", "飙升榜"),
        ("rating", "好评榜")
    ]

    var body: some View {
        VStack(spacing: 0) {
            topBar
            tabBar
            if loading, items.isEmpty {
                Spacer()
                ProgressView()
                    .tint(AppTheme.primary)
                Spacer()
            } else if items.isEmpty {
                Spacer()
                Text("暂无数据")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                    ForEach(items) { it in
                        if let d = it.drama {
                            NavigationLink(value: PlayerEntry(dramaId: d.id, episodeId: nil)) {
                                rankingRow(it, drama: d)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                    .padding(8)
                }
            }
        }
        .background(AppTheme.background)
        .navigationBarHidden(true)
        .task {
            self.type = initialType
            await load(type, force: true)
        }
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.backward")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)
                    .frame(width: 40, height: 40)
            }
            Text("排行榜")
                .font(.title3.weight(.bold))
                .foregroundStyle(AppTheme.onSurface)
            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 4)
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(types, id: \.0) { item in
                Button {
                    guard type != item.0 else { return }
                    type = item.0
                    Task { await load(item.0, force: true) }
                } label: {
                    VStack(spacing: 8) {
                        Text(item.1)
                            .font(.subheadline.weight(type == item.0 ? .bold : .regular))
                            .foregroundStyle(type == item.0 ? AppTheme.primary : AppTheme.onSurfaceVariant)
                        Rectangle()
                            .fill(type == item.0 ? AppTheme.primary : Color.clear)
                            .frame(height: 3)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                }
                .buttonStyle(.plain)
            }
        }
        .background(AppTheme.background)
    }

    private func rankingRow(_ item: RankItem, drama: Drama) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Text("\(item.rank)")
                .font(.title2.weight(.bold))
                .foregroundStyle(rankColor(item.rank))
                .frame(width: 32)
            HGDramaCover(url: ImageURL.resolve(drama.coverUrl), width: 56, height: 72, radius: 4)
            VStack(alignment: .leading, spacing: 3) {
                Text(drama.title ?? "")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.onSurface)
                    .lineLimit(1)
                Text("\(drama.category ?? "") \(drama.statusText)")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .lineLimit(1)
                if let desc = drama.description, !desc.isEmpty {
                    Text(desc)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                        .lineLimit(1)
                }
                HStack(spacing: 6) {
                    rankTag("热度 \(formatCount(item.heat))")
                    rankTag("点赞 \(formatCount(item.totalLikes ?? 0))")
                }
                .padding(.top, 2)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .hgCard(radius: 10, fill: AppTheme.surface)
        .padding(.horizontal, 8)
    }

    private func rankTag(_ text: String) -> some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(AppTheme.primaryLight)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(Color.black.opacity(0.28))
            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
    }

    private func rankColor(_ rank: Int) -> Color {
        switch rank {
        case 1: return Color(red: 1, green: 0.843, blue: 0)
        case 2: return Color(red: 0.753, green: 0.753, blue: 0.753)
        case 3: return Color(red: 0.804, green: 0.498, blue: 0.196)
        default: return AppTheme.onSurfaceVariant
        }
    }

    private func formatCount(_ count: Int64) -> String {
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(count)"
    }

    private func load(_ t: String, force: Bool = false) async {
        requestID += 1
        let currentRequest = requestID
        loading = true
        defer { loading = false }
        do {
            let tok = session.isLoggedIn ? session.token : nil
            let list = try await APIClient.shared.getRankings(type: t, token: tok)
            guard currentRequest == requestID, type == t else { return }
            items = list
        } catch {
            guard currentRequest == requestID, type == t else { return }
            items = []
        }
    }
}
