import AVFoundation
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
    @State private var collectedEpisodeIds: Set<Int64> = []
    @State private var likeCounts: [Int64: Int64] = [:]
    @State private var likingEpisodeIds: Set<Int64> = []
    @State private var likeBursts: [LikeBurst] = []
    @State private var commentSheetEpisode: Episode?
    @State private var playerEntry: FeedPlayerEntry?
    @State private var expandedEpisodeIds: Set<Int64> = []
    @State private var playbackError: String?
    @State private var playbackProgress: Double = 0
    @State private var timeObserver: Any?
    @State private var rankingSheet: RankingSheetEntry?
    @State private var feedStreamLoading = false
    /// 防止快速滑动时较早发起的 `playbackAVURLAsset` Task 在完成后覆盖当前条目。
    @State private var feedPlayerRebuildToken: Int = 0

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
                            InlineVideoSurface(player: p)
                                .ignoresSafeArea()
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
                        feedProgressBar
                        ForEach(likeBursts) { burst in
                            FloatingLikeBurstView(burst: burst) {
                                likeBursts.removeAll { $0.id == burst.id }
                            }
                        }
                        if feedStreamLoading, !episodes.isEmpty {
                            ZStack {
                                Color.black.opacity(0.35)
                                ProgressView("加载中…")
                                    .tint(.white)
                                    .foregroundStyle(.white)
                            }
                            .ignoresSafeArea()
                            .allowsHitTesting(false)
                        }
                    }
                }
            }
        }
        .onChange(of: currentIndex) { newVal in
            if newVal >= episodes.count - 3, !loading, episodes.count > 0 {
                Task { await loadMore() }
            }
            tryScrollAfterDrama()
            rebuildPlayerForCurrent()
        }
        .onChange(of: parentTab) { new in
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
        .onChange(of: scenePhase) { ph in
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
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemFailedToPlayToEndTime)) { note in
            guard player?.currentItem === note.object as? AVPlayerItem else { return }
            playbackError = ((note.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?.localizedDescription)
                ?? "视频播放失败，请重试"
        }
        .sheet(item: $commentSheetEpisode) { ep in
            CommentSheetView(episodeId: ep.id, initialCommentCount: ep.commentCount ?? 0)
                .environmentObject(session)
        }
        .fullScreenCover(item: $playerEntry) { entry in
            NavigationStack {
                PlayerView(
                    dramaId: entry.dramaId,
                    episodeId: entry.episodeId,
                    handoffStreamURL: entry.streamURL,
                    handoffPositionSeconds: entry.positionSeconds,
                    onRequestNextDramaFromFeed: {
                        let dramaId = entry.dramaId
                        Task { @MainActor in
                            playerEntry = nil
                            await Task.yield()
                            await advanceFeedAfterFullScreenDrama(dramaId: dramaId)
                        }
                    }
                )
                    .environmentObject(session)
            }
        }
        .fullScreenCover(item: $rankingSheet) { entry in
            NavigationStack {
                RankingView(initialType: entry.type)
                    .environmentObject(session)
            }
        }
        .hgDialog(Binding(
            get: {
                playbackError.map {
                    HGDialog(title: "提示", message: $0, primaryTitle: "确定", informStyle: true) {
                        playbackError = nil
                    }
                }
            },
            set: { if $0 == nil { playbackError = nil } }
        ))
    }

    @ViewBuilder
    private func feedOverlay(ep: Episode, size: CGSize) -> some View {
        let dramaTitle = ep.drama?.title ?? "短剧"
        ZStack(alignment: .bottomLeading) {
            Color.clear
                .frame(width: size.width, height: size.height)
            HStack(alignment: .bottom, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    if let badge = ep.drama?.preferredRankingBadge {
                        RankingBadgeView(info: badge) {
                            rankingSheet = RankingSheetEntry(type: badge.type)
                        }
                    }
                    Text(dramaTitle)
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text("第 \(ep.episodeNumber) 集")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.9))
                    feedTags(ep)
                    descriptionBlock(ep)
                    Button {
                        let currentURL = PlaybackURL.url(for: ep)
                        playerEntry = FeedPlayerEntry(
                            dramaId: PlaybackURL.dramaId(episode: ep),
                            episodeId: ep.id,
                            streamURL: currentURL,
                            positionSeconds: player?.currentTime().seconds ?? 0
                        )
                        player?.pause()
                    } label: {
                        Text("看全 \(ep.drama?.totalEpisodes ?? 0) 集")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(AppTheme.primary.opacity(0.85))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(spacing: 14) {
                    HGInteractionButton(
                        icon: "heart.fill",
                        label: likeCountText(ep),
                        active: isLiked(ep)
                    ) {
                        Task {
                            let point = CGPoint(x: size.width - 46, y: size.height * 0.58)
                            if await toggleFeedLike(ep) {
                                addLikeBurst(at: point)
                            }
                        }
                    }
                    .disabled(likingEpisodeIds.contains(ep.id))

                    HGInteractionButton(
                        icon: "text.bubble.fill",
                        label: commentCountText(ep)
                    ) {
                        commentSheetEpisode = ep
                    }

                    HGInteractionButton(
                        icon: "star.fill",
                        label: "收藏",
                        active: isCollected(ep)
                    ) {
                        Task { await toggleFeedCollect(ep) }
                    }

                    ShareLink(item: shareText(ep)) {
                        VStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.title2.weight(.semibold))
                                .foregroundStyle(.white)
                                .frame(width: 46, height: 46)
                                .background(Circle().fill(Color.black.opacity(0.36)))
                            Text("分享")
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

    @ViewBuilder
    private func feedTags(_ ep: Episode) -> some View {
        let tags = resolvedTags(ep)
        if !tags.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(tags, id: \.self) { tag in
                        Text(tag)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurface)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(AppTheme.feedTagFill)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(AppTheme.primary.opacity(0.45), lineWidth: 1))
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func descriptionBlock(_ ep: Episode) -> some View {
        let text = ep.drama?.description ?? ""
        if !text.isEmpty {
            let expanded = expandedEpisodeIds.contains(ep.id)
            VStack(alignment: .leading, spacing: 4) {
                Text(text)
                    .font(.caption)
                    .lineLimit(expanded ? 4 : 1)
                    .foregroundStyle(.white.opacity(0.86))
                Button(expanded ? "收起" : "展开") {
                    if expanded {
                        expandedEpisodeIds.remove(ep.id)
                    } else {
                        expandedEpisodeIds.insert(ep.id)
                    }
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.primaryLight)
            }
        }
    }

    private func rebuildPlayerForCurrent() {
        playerPauseClear()
        playbackProgress = 0
        guard currentIndex < episodes.count else { return }
        let ep = episodes[currentIndex]
        guard let u = PlaybackURL.url(for: ep) else { return }
        var headers: [String: String] = [:]
        if session.isLoggedIn, !session.token.isEmpty {
            headers["Authorization"] = "Bearer \(session.token)"
        }
        feedStreamLoading = true
        feedPlayerRebuildToken += 1
        let rebuildToken = feedPlayerRebuildToken
        Task { @MainActor in
            defer { feedStreamLoading = false }
            let asset = await VideoCacheManager.shared.playbackAVURLAsset(remoteURL: u, headers: headers, episodeId: ep.id)
            guard rebuildToken == feedPlayerRebuildToken,
                  currentIndex < episodes.count,
                  episodes[currentIndex].id == ep.id else { return }
            let item = AVPlayerItem(asset: asset)
            clearTimeObserver()
            player?.pause()
            player = AVPlayer(playerItem: item)
            installTimeObserver(for: item)
            if parentTab == .feed, scenePhase == .active {
                player?.play()
            }
            await prefetchFeedNextOnly(index: currentIndex, headers: headers)
        }
        Task { await loadInteractionIfNeeded(for: ep) }
    }

    private func playerPauseClear() {
        player?.pause()
        clearTimeObserver()
        player = nil
    }

    /// 全屏看剧最后一集结束（或手势切下一部）后回到刷剧：避免在手势/`fullScreenCover` 动画中途同步改状态导致交互假死。
    private func advanceFeedAfterFullScreenDrama(dramaId: Int64) async {
        guard dramaId > 0, !episodes.isEmpty else {
            rebuildPlayerForCurrent()
            resumeFeedPlaybackIfNeeded()
            return
        }
        var advanced = false
        if let next = findNextDramaIndex(after: dramaId) {
            if next < episodes.count, next != currentIndex {
                currentIndex = next
                advanced = true
            }
        }
        if !advanced {
            if !loading { await loadMore() }
            if let next = findNextDramaIndex(after: dramaId), next < episodes.count, next != currentIndex {
                currentIndex = next
                advanced = true
            }
        }
        if !advanced {
            rebuildPlayerForCurrent()
            resumeFeedPlaybackIfNeeded()
        }
    }

    private func resumeFeedPlaybackIfNeeded() {
        guard parentTab == .feed, scenePhase == .active else { return }
        player?.play()
    }

    private var feedProgressBar: some View {
        VStack {
            Spacer()
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle()
                        .fill(Color.white.opacity(0.18))
                    Rectangle()
                        .fill(Color.white.opacity(0.96))
                        .frame(width: geo.size.width * playbackProgress)
                }
            }
            .frame(height: 3)
        }
        .ignoresSafeArea(edges: .bottom)
    }

    private func installTimeObserver(for item: AVPlayerItem) {
        guard let player, timeObserver == nil else { return }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { time in
            let duration = item.duration.seconds
            guard duration.isFinite, duration > 0 else {
                playbackProgress = 0
                return
            }
            playbackProgress = min(1, max(0, time.seconds / duration))
        }
    }

    private func clearTimeObserver() {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
    }

    /// 对齐 Android：全屏 `precacheNextEpisode`；Feed 仅预拉**下一条**。
    private func prefetchFeedNextOnly(index: Int, headers: [String: String]) async {
        let j = index + 1
        guard episodes.indices.contains(j), let url = PlaybackURL.url(for: episodes[j]) else { return }
        let eid = episodes[j].id
        await VideoCacheManager.shared.precache(url, headers: headers, episodeId: eid)
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
                let indexWasInvalid = currentIndex >= episodes.count
                if indexWasInvalid {
                    currentIndex = max(0, episodes.count - 1)
                }
                // 加载更多时不应重建当前播放：会清空 player 且抬高 token，易导致异步任务全部被 guard 丢弃而无法自动播放。
                if !append || indexWasInvalid {
                    rebuildPlayerForCurrent()
                }
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

    private func isCollected(_ ep: Episode) -> Bool {
        collectedEpisodeIds.contains(ep.id)
    }

    private func resolvedTags(_ ep: Episode) -> [String] {
        var tags = ep.drama?.categoryList ?? []
        if tags.isEmpty, let c = ep.drama?.category, !c.isEmpty {
            tags = c.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }
        }
        return Array(tags.filter { !$0.isEmpty }.prefix(4))
    }

    private func shareText(_ ep: Episode) -> String {
        "\(ep.drama?.title ?? "红果剧场") 第 \(ep.episodeNumber) 集"
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

    private func toggleFeedCollect(_ ep: Episode) async {
        guard session.isLoggedIn, !session.token.isEmpty else { return }
        do {
            try await APIClient.shared.collectForEpisodeDrama(episodeId: ep.id, token: session.token)
            if isCollected(ep) {
                collectedEpisodeIds.remove(ep.id)
            } else {
                collectedEpisodeIds.insert(ep.id)
            }
        } catch {}
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
            if interaction.collected {
                collectedEpisodeIds.insert(ep.id)
            } else {
                collectedEpisodeIds.remove(ep.id)
            }
        } catch {}
    }

    private func addLikeBurst(at point: CGPoint) {
        likeBursts.append(LikeBurst(point: point))
    }
}

private struct FeedPlayerEntry: Identifiable {
    let dramaId: Int64
    let episodeId: Int64?
    let streamURL: URL?
    let positionSeconds: Double

    var id: String { "\(dramaId)-\(episodeId ?? 0)" }
}

private struct RankingSheetEntry: Identifiable {
    let type: String
    var id: String { type }
}
