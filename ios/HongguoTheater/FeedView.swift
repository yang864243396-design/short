import AVFoundation
import AVKit
import SwiftUI

/// 刷剧：竖向分页 + 单路 `AVPlayer` 置于底层，透明翻页；对齐 `getFeed` 与 Tab/前后台生命周期。
struct FeedView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.scenePhase) private var scenePhase
    @Binding var scrollAfterDramaId: Int64
    @Binding var parentTab: MainTab

    @State private var episodes: [Episode] = []
    @State private var currentIndex: Int = 0
    @State private var page: Int = 1
    @State private var loading = false
    @State private var initialLoaded = false
    @State private var player: AVPlayer?
    @State private var loadError: String?
    @State private var loadMoreError: String?
    @State private var likedEpisodeIds: Set<Int64> = []
    @State private var likeCounts: [Int64: Int64] = [:]
    @State private var likingEpisodeIds: Set<Int64> = []
    @State private var likeBursts: [LikeBurst] = []
    @State private var commentSheetEpisode: Episode?

    private static let pageSize = 10

    var body: some View {
        GeometryReader { g in
            ZStack {
                Color.black.ignoresSafeArea()
                if episodes.isEmpty, loading {
                    ProgressView()
                        .tint(.white)
                } else if episodes.isEmpty, loadError != nil {
                    VStack {
                        Text(loadError ?? "暂无内容")
                            .foregroundStyle(.white)
                        Button("重试") { Task { await loadFeed(reset: true) } }
                            .buttonStyle(.borderedProminent)
                            .tint(AppTheme.primary)
                    }
                } else {
                    ZStack(alignment: .bottom) {
                        if let p = player {
                            VideoPlayer(player: p)
                                .ignoresSafeArea()
                                .allowsHitTesting(false)
                        }
                        VerticalPagingScrollView(
                            currentIndex: $currentIndex,
                            count: episodes.count,
                            pageWidth: g.size.width,
                            pageHeight: g.size.height
                        ) { i in
                            if i < episodes.count {
                                AnyView(feedOverlay(ep: episodes[i], size: g.size))
                            } else {
                                AnyView(EmptyView())
                            }
                        }
                        if let tip = loadMoreError {
                            HStack(alignment: .top, spacing: 8) {
                                Text(tip)
                                    .font(.caption)
                                    .foregroundStyle(.white)
                                Spacer(minLength: 0)
                                Button("关闭") { loadMoreError = nil }
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(AppTheme.primary)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.black.opacity(0.78))
                        }
                        ForEach(likeBursts) { burst in
                            FloatingLikeBurstView(burst: burst) {
                                likeBursts.removeAll { $0.id == burst.id }
                            }
                        }
                    }
                }
            }
        }
        .onChange(of: currentIndex) { _, newVal in
            if newVal >= episodes.count - 3, !loading, episodes.count > 0 {
                Task { await loadMore() }
            }
            tryScrollAfterDrama()
            rebuildPlayerForCurrent()
        }
        .onChange(of: parentTab) { _, new in
            if new == .feed {
                if !initialLoaded {
                    initialLoaded = true
                    Task { await loadFeed(reset: true) }
                } else {
                    player?.play()
                }
            } else {
                player?.pause()
            }
        }
        .onChange(of: scenePhase) { _, ph in
            if ph == .active, parentTab == .feed {
                player?.play()
            } else if ph != .active {
                // 含 .inactive / .background，避免来电、控制中心、切 App 时仍外放
                player?.pause()
            }
        }
        .onAppear {
            if parentTab == .feed, !initialLoaded {
                initialLoaded = true
                Task { await loadFeed(reset: true) }
            }
        }
        .sheet(item: $commentSheetEpisode) { ep in
            CommentSheetView(episodeId: ep.id, initialCommentCount: ep.commentCount ?? 0)
                .environmentObject(session)
        }
    }

    @ViewBuilder
    private func feedOverlay(ep: Episode, size: CGSize) -> some View {
        let dramaTitle = ep.drama?.title ?? "短剧"
        ZStack(alignment: .bottomLeading) {
            Color.clear
                .frame(width: size.width, height: size.height)
            HStack(alignment: .bottom, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(dramaTitle)
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text("第 \(ep.episodeNumber) 集")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.9))
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(spacing: 14) {
                    Button {
                        Task {
                            let point = CGPoint(x: size.width - 46, y: size.height * 0.58)
                            if await toggleFeedLike(ep) {
                                addLikeBurst(at: point)
                            }
                        }
                    } label: {
                        VStack(spacing: 3) {
                            Image(systemName: "heart.fill")
                                .font(.title2)
                                .foregroundStyle(isLiked(ep) ? Color.red : .white)
                                .padding(10)
                                .background(Circle().fill(Color.black.opacity(0.35)))
                            Text(likeCountText(ep))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                    .disabled(likingEpisodeIds.contains(ep.id))

                    Button {
                        commentSheetEpisode = ep
                    } label: {
                        VStack(spacing: 3) {
                            Image(systemName: "text.bubble.fill")
                                .font(.title2)
                                .foregroundStyle(.white)
                                .padding(10)
                                .background(Circle().fill(Color.black.opacity(0.35)))
                            Text(commentCountText(ep))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom))
        }
        .contentShape(Rectangle())
        .simultaneousGesture(
            SpatialTapGesture(count: 2).onEnded { value in
                handleFeedDoubleTap(ep, location: value.location)
            }
        )
    }

    private func rebuildPlayerForCurrent() {
        player?.pause()
        player = nil
        guard currentIndex < episodes.count else { return }
        let ep = episodes[currentIndex]
        guard let u = PlaybackURL.url(for: ep) else { return }
        var headers: [String: String] = [:]
        if session.isLoggedIn, !session.token.isEmpty {
            headers["Authorization"] = "Bearer \(session.token)"
        }
        let asset = AVURLAsset(url: u, options: [AVURLAssetHTTPHeaderFieldsKey: headers])
        let item = AVPlayerItem(asset: asset)
        player = AVPlayer(playerItem: item)
        if parentTab == .feed, scenePhase == .active {
            player?.play()
        }
        Task { await loadInteractionIfNeeded(for: ep) }
    }

    private func tryScrollAfterDrama() {
        let target = scrollAfterDramaId
        guard target > 0, !episodes.isEmpty else { return }
        if let next = findNextDramaIndex(after: target) {
            if next < episodes.count, next != currentIndex {
                currentIndex = next
            }
        }
        scrollAfterDramaId = 0
    }

    private func findNextDramaIndex(after dramaId: Int64) -> Int? {
        var last: Int = -1
        for (i, ep) in episodes.enumerated() {
            let did = PlaybackURL.dramaId(episode: ep)
            if did == dramaId { last = i }
        }
        guard last >= 0, last + 1 < episodes.count else { return nil }
        return last + 1
    }

    private func loadFeed(reset: Bool) async {
        if reset {
            page = 1
            episodes = []
            currentIndex = 0
            loadMoreError = nil
        }
        await loadPage(append: !reset)
    }

    private func loadMore() async {
        guard !loading else { return }
        page += 1
        await loadPage(append: true)
    }

    private func loadPage(append: Bool) async {
        loading = true
        defer { loading = false }
        do {
            let token = session.isLoggedIn ? session.token : nil
            let list = try await APIClient.shared.getFeed(
                page: page,
                pageSize: Self.pageSize,
                episodeNumber: 1,
                token: token
            )
            if append {
                episodes.append(contentsOf: list)
            } else {
                episodes = list
                likedEpisodeIds.removeAll()
                likeCounts.removeAll()
            }
            loadError = nil
            loadMoreError = nil
            if !episodes.isEmpty {
                if currentIndex >= episodes.count { currentIndex = max(0, episodes.count - 1) }
                rebuildPlayerForCurrent()
                tryScrollAfterDrama()
            }
        } catch {
            if append, !episodes.isEmpty {
                page = max(1, page - 1)
                loadMoreError = "加载更多失败：\(error.localizedDescription)"
            } else {
                loadError = error.localizedDescription
            }
        }
    }

    private func isLiked(_ ep: Episode) -> Bool {
        likedEpisodeIds.contains(ep.id)
    }

    private func likeCountText(_ ep: Episode) -> String {
        let count = likeCounts[ep.id] ?? ep.likeCount ?? 0
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(count)"
    }

    private func commentCountText(_ ep: Episode) -> String {
        let count = ep.commentCount ?? 0
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(count)"
    }

    private func handleFeedDoubleTap(_ ep: Episode, location: CGPoint) {
        guard session.isLoggedIn else { return }
        if isLiked(ep) {
            addLikeBurst(at: location)
            return
        }
        Task {
            if await toggleFeedLike(ep) {
                addLikeBurst(at: location)
            }
        }
    }

    private func toggleFeedLike(_ ep: Episode) async -> Bool {
        guard session.isLoggedIn, !session.token.isEmpty else { return false }
        guard !likingEpisodeIds.contains(ep.id) else { return false }
        likingEpisodeIds.insert(ep.id)
        defer { likingEpisodeIds.remove(ep.id) }
        do {
            let oldLiked = isLiked(ep)
            let result = try await APIClient.shared.likeEpisode(episodeId: ep.id, token: session.token)
            if result.liked {
                likedEpisodeIds.insert(ep.id)
            } else {
                likedEpisodeIds.remove(ep.id)
            }
            if oldLiked != result.liked {
                let base = likeCounts[ep.id] ?? ep.likeCount ?? 0
                likeCounts[ep.id] = max(0, base + (result.liked ? 1 : -1))
            }
            return result.liked
        } catch {
            return false
        }
    }

    private func loadInteractionIfNeeded(for ep: Episode) async {
        guard session.isLoggedIn, !session.token.isEmpty else { return }
        do {
            let interaction = try await APIClient.shared.getEpisodeInteraction(episodeId: ep.id, token: session.token)
            if interaction.liked {
                likedEpisodeIds.insert(ep.id)
            } else {
                likedEpisodeIds.remove(ep.id)
            }
        } catch {}
    }

    private func addLikeBurst(at point: CGPoint) {
        likeBursts.append(LikeBurst(point: point))
    }
}
