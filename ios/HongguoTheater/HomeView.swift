import SwiftUI

/// 对齐 Android 首页主结构：搜索、Banner、今日热播榜、剧集 Tab、剧集列表。
struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    var onOpenFeedAfterDrama: (Int64) -> Void = { _ in }

    @State private var home: HomeData?
    @State private var banners: [BannerItem] = []
    @State private var dramList: [Drama] = []
    @State private var category: String = ""
    @State private var page = 1
    @State private var hasMore = true
    @State private var loading = false
    @State private var refreshing = false
    @State private var error: String?
    @State private var bannerIndex = 0

    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                searchBar
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        bannerSection
                        hotRankRow
                        categoryChips
                        errorBanner
                        dramaGrid
                        if loading, dramList.isEmpty {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding()
                        }
                        loadMoreTrigger
                    }
                    .padding(.vertical, 8)
                }
                .refreshable { await refreshAll() }
            }
            .background(AppTheme.background)
            .navigationBarHidden(true)
            .navigationDestination(for: PlayerEntry.self) { entry in
                PlayerView(dramaId: entry.dramaId, episodeId: entry.episodeId)
            }
            .navigationDestination(for: SearchNav.self) { _ in
                SearchView()
            }
            .navigationDestination(for: RankingNav.self) { r in
                RankingView(initialType: r.type)
            }
        }
        .tint(AppTheme.onSurface)
        .task { await refreshAll() }
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Text("搜索短剧…")
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Spacer()
        }
        .padding(12)
        .background(AppTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal)
        .onTapGesture { path.append(SearchNav()) }
    }

    @ViewBuilder
    private var bannerSection: some View {
        if !banners.isEmpty {
            TabView(selection: $bannerIndex) {
                ForEach(Array(banners.enumerated()), id: \.element.id) { idx, b in
                    Group {
                        if let u = ImageURL.resolve(b.imageUrl) {
                            AsyncImage(url: u) { p in
                                p.resizable().scaledToFill()
                            } placeholder: {
                                Color(white: 0.2)
                            }
                        } else { Color(white: 0.2) }
                    }
                    .frame(height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .onTapGesture {
                        if b.isDramaLink, b.dramaId != nil, b.dramaId! > 0 {
                            path.append(PlayerEntry(dramaId: b.dramaId!, episodeId: nil))
                        }
                    }
                    .tag(idx)
                }
            }
            .frame(height: 160)
            .tabViewStyle(.page(indexDisplayMode: .never))
            .overlay(alignment: .bottom) {
                HStack(spacing: 5) {
                    ForEach(0 ..< banners.count, id: \.self) { idx in
                        Capsule()
                            .fill(idx == bannerIndex ? AppTheme.primary : Color.white.opacity(0.35))
                            .frame(width: idx == bannerIndex ? 16 : 6, height: 6)
                    }
                }
                .padding(.bottom, 10)
            }
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private var hotRankRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("今日热播榜")
                    .font(.headline)
                Spacer()
                Button("查看完整榜单") {
                    path.append(RankingNav(type: "hot"))
                }
                .font(.subheadline)
                .foregroundStyle(AppTheme.primary)
            }
            .padding(.horizontal)
            let items = Array((home?.hotRanking ?? []).prefix(10))
            if items.isEmpty {
                Text("暂无榜单数据")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .padding(.horizontal)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(items) { it in
                            if let d = it.drama {
                                rankingCoverCard(rank: it.rank, drama: d)
                                    .onTapGesture { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) }
                            }
                        }
                    }
                    .padding(.horizontal)
                }
            }
        }
    }

    @ViewBuilder
    private var categoryChips: some View {
        let cats = home?.categories ?? []
        if !cats.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip("推荐", sel: category.isEmpty) {
                        category = ""
                        Task { await resetDramaList() }
                    }
                    ForEach(cats) { c in
                        chip(c.name, sel: category == c.name) {
                            category = c.name
                            Task { await resetDramaList() }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private func chip(_ title: String, sel: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .hgPill(selected: sel)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var errorBanner: some View {
        if let error {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(AppTheme.primary)
                Text(error)
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Spacer()
                Button("重试") { Task { await refreshAll() } }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.primary)
            }
            .padding(12)
            .hgCard(radius: 10, fill: AppTheme.surfaceHigh)
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private var dramaGrid: some View {
        LazyVStack(alignment: .leading, spacing: 12) {
            ForEach(dramList) { d in
                dramaMiniRow(d)
                    .onTapGesture { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) }
            }
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private var loadMoreTrigger: some View {
        Group {
            if hasMore, !dramList.isEmpty {
                Color.clear
                    .frame(height: 1)
                    .onAppear { Task { await loadDramasMore() } }
            }
        }
    }

    private func dramaMiniRow(_ d: Drama) -> some View {
        HStack(alignment: .top, spacing: 12) {
            HGDramaCover(url: ImageURL.resolve(d.coverUrl), width: 80, height: 106, radius: 6)
            VStack(alignment: .leading, spacing: 6) {
                Text(d.title ?? "")
                    .font(.headline)
                    .foregroundStyle(AppTheme.onSurface)
                    .lineLimit(1)
                HStack(alignment: .center, spacing: 6) {
                    tagRow(d.categoryTags)
                    Spacer(minLength: 6)
                    Text("\(d.heatText)热度")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                        .lineLimit(1)
                }
                Text(d.statusText)
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                if let desc = d.description, !desc.isEmpty {
                    Text(desc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                        .lineLimit(2)
                }
            }
            Spacer()
        }
        .padding(12)
        .hgCard(radius: 10, fill: AppTheme.surface)
    }

    private func tagRow(_ tags: [String]) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(tags.prefix(2)), id: \.self) { tag in
                Text(tag)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.primaryLight)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(AppTheme.primary.opacity(0.12))
                    .clipShape(Capsule())
            }
        }
    }

    private func rankingCoverCard(rank: Int, drama: Drama) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .topLeading) {
                HGDramaCover(url: ImageURL.resolve(drama.coverUrl), width: 110, height: 150, radius: 8)
                Text("\(rank)")
                    .font(.caption.weight(.heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(AppTheme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .padding(6)
            }
            Text(drama.title ?? "")
                .lineLimit(1)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.onSurface)
            Text(drama.statusText)
                .lineLimit(1)
                .font(.caption2)
                .foregroundStyle(AppTheme.onSurfaceVariant)
        }
        .frame(width: 110, alignment: .leading)
    }

    private func refreshAll() async {
        refreshing = true
        error = nil
        defer { refreshing = false }
        do {
            let tok = session.isLoggedIn ? session.token : nil
            let h = try await APIClient.shared.getHome(token: tok)
            home = h
            let b = (try? await APIClient.shared.getBanners(token: tok)) ?? []
            self.banners = b
            await resetDramaList()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func resetDramaList() async {
        page = 1
        hasMore = true
        dramList = []
        await loadDramasMore()
    }

    private func loadDramasMore() async {
        guard hasMore, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let tok = session.isLoggedIn ? session.token : nil
            let list = try await APIClient.shared.getDramas(
                category: category.isEmpty ? nil : category,
                page: page,
                pageSize: 20,
                token: tok
            )
            if list.isEmpty { hasMore = false; return }
            if page == 1 { dramList = list } else { dramList.append(contentsOf: list) }
            page += 1
        } catch {
            hasMore = false
        }
    }
}

struct PlayerEntry: Hashable {
    let dramaId: Int64
    let episodeId: Int64?
}

private struct SearchNav: Hashable {}
private struct RankingNav: Hashable {
    let type: String
}

extension Drama {
    var statusText: String {
        if (status ?? "") == "completed" {
            if let t = totalEpisodes, t > 0 { return "\(t)集全" }
        }
        if let t = totalEpisodes, t > 0 { return "更新至\(t)集" }
        return status ?? ""
    }

    var heatText: String {
        let value = heat ?? 0
        if value >= 10_000 {
            return String(format: "%.1fw", Double(value) / 10_000.0)
        }
        return "\(value)"
    }

    var categoryTags: [String] {
        if let list = categoryList, !list.isEmpty {
            return list.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
        if let category, !category.isEmpty {
            return category
                .split(separator: ",")
                .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        }
        return []
    }
}

