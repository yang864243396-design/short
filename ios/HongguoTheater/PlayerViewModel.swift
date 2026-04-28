import AVFoundation
import AVKit
import Combine
import Foundation

@MainActor
final class PlayerViewModel: ObservableObject {
    @Published var player: AVPlayer?
    @Published var episodes: [Episode] = []
    @Published var current: Episode?
    @Published var drama: Drama?
    @Published var liked: Bool = false
    @Published var collected: Bool = false
    @Published var likeCount: Int64 = 0
    @Published var commentCount: Int64 = 0
    @Published var playbackProgress: Double = 0
    @Published var isPlaying: Bool = false
    @Published var loadError: String?
    @Published var busy: Bool = false

    // 贴片：对齐 Android `fetchAndPlayAd` + 正片
    @Published var showAd: Bool = false
    @Published var adPlayer: AVPlayer?
    @Published var adImageURL: URL?
    @Published var adCountdown: Int = 0
    @Published var adErrorHint: String?
    @Published var adCanClose: Bool = false
    @Published var confirmAbandonAd: Bool = false
    @Published var rechargePrompt: Bool = false

    /// 正片资源解析/缓存命中阶段（用于加载态 UI）
    @Published var streamPreparing: Bool = false

    /// 与 Android `AdSkipCache` 快照对齐：用于「有免广次数/权益在期是否免弹解锁二选一」。
    @Published private(set) var adSkipStatusSnapshot: AdSkipStatus?

    let dramaId: Int64
    private let startEpisodeId: Int64?
    private let handoffEpisodeId: Int64?
    private let onRequestNextDramaFromFeed: (() -> Void)?
    private let handoffStreamURL: URL?
    private let handoffPositionSeconds: Double
    var authToken: String?
    private var adEndObserver: NSObjectProtocol?
    private var adTimeoutTask: Task<Void, Never>?
    private var adCountdownTask: Task<Void, Never>?
    private var adItemStatusCancellable: AnyCancellable?
    private var playTask: Task<Void, Never>?
    private var timeObserver: Any?
    /// 与 `timeObserver` 成对记录，避免 `player` 已换新实例却仍向旧/错误的 player 调 `removeTimeObserver` 崩溃。
    private var timeObserverPlayer: AVPlayer?
    private var adGrantsTemporaryUnlock = false
    private var temporaryUnlockExpiry: [Int64: Date] = [:]
    private var mainEndObserver: NSObjectProtocol?

    init(
        dramaId: Int64,
        startEpisodeId: Int64?,
        handoffStreamURL: URL? = nil,
        handoffPositionSeconds: Double = 0,
        onRequestNextDramaFromFeed: (() -> Void)? = nil
    ) {
        self.dramaId = dramaId
        self.startEpisodeId = startEpisodeId
        self.handoffEpisodeId = startEpisodeId
        self.handoffStreamURL = handoffStreamURL
        self.handoffPositionSeconds = handoffPositionSeconds
        self.onRequestNextDramaFromFeed = onRequestNextDramaFromFeed
    }

    private func shouldPaywall(_ ep: Episode) -> Bool {
        let free = ep.isFree ?? true
        let unlocked = ep.coinUnlocked ?? false
        return !free && !unlocked && !isTemporarilyUnlocked(ep.id)
    }

    /// 对齐 Android `PlayerActivity.playEpisode`：`needCoinOrAdDialog = 已登录 && unlockCoins>0 && !coinUnlocked` 才弹「金币/看广告」；
    /// 未登录不弹二选一，直接走片头 `loadAndPlayAd`。
    private func requiresCoinOrAdChoice() -> Bool {
        guard let ep = current else { return false }
        guard let t = authToken, !t.isEmpty else { return false }
        guard (ep.unlockCoins ?? 0) > 0 else { return false }
        guard !(ep.isFree ?? true) else { return false }
        guard !(ep.coinUnlocked ?? false) else { return false }
        guard !isTemporarilyUnlocked(ep.id) else { return false }
        /// 有免广次数/权益可走片头抵扣时，不挡二选一（与 Android `shouldSkipCoinUnlockDialogForAdSkip` + 直走 `loadAndPlayAd`）。
        return !shouldSkipCoinUnlockDialogForAdSkip()
    }

    func needsUnlockGate() -> Bool {
        requiresCoinOrAdChoice()
    }

    func load() async {
        let token = authToken
        busy = true
        defer { busy = false }
        do {
            let d = try await APIClient.shared.getDramaDetail(id: dramaId, token: token)
            self.drama = d
            var eps = try await APIClient.shared.getDramaEpisodes(dramaId: dramaId, token: token)
            eps = eps.filter(\.isPlayable)
            self.episodes = eps
            if let sid = startEpisodeId, let pick = eps.first(where: { $0.id == sid }) {
                self.current = pick
            } else {
                self.current = eps.first
            }
            if self.current == nil { loadError = "暂无可播分集" }
            syncCountsFromCurrent()
            await refreshAdSkipSnapshotIfLoggedIn()
            startPlaybackPipeline()
        } catch {
            loadError = error.localizedDescription
            adSkipStatusSnapshot = nil
        }
    }

    func selectEpisode(_ ep: Episode) {
        playTask?.cancel()
        Task { await VideoCacheManager.shared.cancelPrecache() }
        clearTimeObserver()
        clearAd()
        current = ep
        syncCountsFromCurrent()
        Task { @MainActor in
            await self.refreshAdSkipSnapshotIfLoggedIn()
            self.startPlaybackPipeline()
        }
    }

    @discardableResult
    func selectRelativeEpisode(offset: Int) -> Bool {
        guard let cur = current, let idx = episodes.firstIndex(where: { $0.id == cur.id }) else { return false }
        let next = idx + offset
        guard episodes.indices.contains(next) else { return false }
        selectEpisode(episodes[next])
        return true
    }

    var isOnLastEpisode: Bool {
        guard let cur = current, let idx = episodes.firstIndex(where: { $0.id == cur.id }) else { return false }
        return idx == episodes.count - 1
    }

    var isOnFirstEpisode: Bool {
        guard let cur = current, let idx = episodes.firstIndex(where: { $0.id == cur.id }) else { return false }
        return idx == 0
    }

    func toggleLike() async -> Bool {
        guard let t = authToken, !t.isEmpty, let ep = current else { return false }
        do {
            let result = try await APIClient.shared.likeEpisode(episodeId: ep.id, token: t)
            let wasLiked = liked
            liked = result.liked
            if wasLiked != result.liked {
                likeCount = max(0, likeCount + (result.liked ? 1 : -1))
            }
            return result.liked
        } catch {
            return false
        }
    }

    func toggleCollect() async {
        guard let t = authToken, !t.isEmpty, let ep = current else { return }
        do {
            try await APIClient.shared.collectForEpisodeDrama(episodeId: ep.id, token: t)
            collected.toggle()
        } catch {}
    }

    func unlock() async {
        guard let t = authToken, !t.isEmpty, let ep = current else { return }
        do {
            try await APIClient.shared.unlockEpisodeWithCoins(episodeId: ep.id, token: t)
            var eps = try await APIClient.shared.getDramaEpisodes(dramaId: dramaId, token: t)
            eps = eps.filter(\.isPlayable)
            self.episodes = eps
            if let fresh = eps.first(where: { $0.id == ep.id }) {
                self.current = fresh
            }
            syncCountsFromCurrent()
            await refreshAdSkipSnapshotIfLoggedIn()
            startPlaybackPipeline()
        } catch {
            let msg = error.localizedDescription
            if msg.contains("金币不足") {
                rechargePrompt = true
            } else {
                loadError = msg
            }
        }
    }

    func watchAdForTemporaryUnlock() {
        guard current != nil else { return }
        playTask?.cancel()
        playTask = Task { @MainActor in
            await self.runAdThenMain(grantsTemporaryUnlock: true)
        }
    }

    func togglePlayPause() {
        guard let player else { return }
        if player.rate > 0 {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    func seek(to progress: Double) async {
        guard let player, let duration = player.currentItem?.duration.seconds, duration.isFinite, duration > 0 else { return }
        let clamped = min(1, max(0, progress))
        playbackProgress = clamped
        let target = CMTime(seconds: duration * clamped, preferredTimescale: 600)
        await player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    // MARK: - 广告 + 正片
    private func startPlaybackPipeline() {
        playTask?.cancel()
        playTask = Task { @MainActor in
            await self.runAdThenMain(grantsTemporaryUnlock: false)
        }
    }

    private func runAdThenMain(grantsTemporaryUnlock: Bool) async {
        clearAd()
        player?.pause()
        clearTimeObserver()
        player = nil
        isPlaying = false
        guard let ep = current else { return }
        /// 对齐 Android：仅当应弹「金币/看广告」且用户尚未点「观看广告」流水线时阻塞；未登录/免广抵扣路径不 return。
        if requiresCoinOrAdChoice(), !grantsTemporaryUnlock { return }
        if Task.isCancelled { return }
        /// 已通过付费墙：`player` 已清空，`playMainStream` 较晚才把 `streamPreparing` 置为 true，中间会 `await getAdVideoPayload`，
        /// 若没有此处则会出现「仅黑屏、无加载」——金币解锁后即为此路径。
        streamPreparing = true
        // 对齐 Android `loadAndPlayAd()`：已登录且本集仍在 `EpisodeAdTempUnlock` 窗口内则直接起正片，不请求 `ad/video`。
        if let t = authToken, !t.isEmpty, isTemporarilyUnlocked(ep.id) {
            await playMainStream()
            return
        }
        /// 对齐 Android `fetchAndPlayAd`：`episode_id` 带当前分集 id（未登录也同样传，便于服务端片头与统计）。
        let eid: Int64? = ep.id > 0 ? ep.id : nil
        if let p = await APIClient.shared.getAdVideoPayload(episodeId: eid, token: authToken) {
            if p.skipAd == true {
                if grantsTemporaryUnlock || shouldPaywall(ep) {
                    grantTemporaryUnlock(ep.id)
                }
                await playMainStream()
                return
            }
            let dur = max(1, p.duration ?? 15)
            let mt = (p.mediaType ?? "video").lowercased()
            if Task.isCancelled {
                streamPreparing = false
                return
            }
            adGrantsTemporaryUnlock = grantsTemporaryUnlock || shouldPaywall(ep)
            if mt == "image", let iu = ImageURL.resolve(p.imageUrl) {
                await runImageAd(iu, seconds: dur)
            } else if let vu = PlaybackURL.adVideoAbsoluteURL(p.videoUrl) {
                // 片长时间约 dur 秒，超时取 2~3 倍作为兜底，与常见贴片长度同量级
                let cap = min(120, max(30, dur * 3))
                await runVideoAd(url: vu, durationSeconds: dur, maxWaitSeconds: cap)
            } else {
                await playMainStream()
            }
        } else {
            await playMainStream()
        }
    }

    /// 对齐 Android `PlayerActivity`：贴片期间后台 `precache` 当前待播正片，缩短关贴后起播等待。
    private func precacheCurrentEpisodeWhileAdPlaying() async {
        guard let ep = current else { return }
        guard let u = PlaybackURL.url(for: ep) else { return }
        var headers: [String: String] = [:]
        if let t = authToken, !t.isEmpty { headers["Authorization"] = "Bearer \(t)" }
        await VideoCacheManager.shared.precache(u, headers: headers, episodeId: ep.id)
    }

    private func runImageAd(_ url: URL, seconds: Int) async {
        await precacheCurrentEpisodeWhileAdPlaying()
        streamPreparing = false
        showAd = true
        adCanClose = false
        adImageURL = url
        adCountdown = max(1, seconds)
        while adCountdown > 0, !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if Task.isCancelled {
                clearAd()
                return
            }
            adCountdown -= 1
        }
        adCanClose = true
        if Task.isCancelled {
            clearAd()
            return
        }
        // 图片广告与 Android 一致：倒计时结束后变为“关闭广告”，由用户关闭后获得本集临时权限。
    }

    private func runVideoAd(url: URL, durationSeconds: Int, maxWaitSeconds: Int) async {
        await precacheCurrentEpisodeWhileAdPlaying()
        adTimeoutTask?.cancel()
        adTimeoutTask = nil
        adCountdownTask?.cancel()
        adCountdownTask = nil
        streamPreparing = false
        showAd = true
        adCanClose = false
        adCountdown = max(0, durationSeconds)
        adPlayer?.pause()
        adPlayer = nil
        if let o = adEndObserver { NotificationCenter.default.removeObserver(o) }
        adItemStatusCancellable?.cancel()
        adItemStatusCancellable = nil
        let opts: [String: Any] = [
            AVURLAssetAllowsExpensiveNetworkAccessKey: true,
            AVURLAssetAllowsCellularAccessKey: true,
        ]
        let asset = AVURLAsset(url: url, options: opts)
        let item = AVPlayerItem(asset: asset)
        adItemStatusCancellable = item.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self, status == .failed else { return }
                Task { @MainActor in await self.adPlaybackFailedFallbackToMain() }
            }
        let pl = AVPlayer(playerItem: item)
        adPlayer = pl
        pl.play()
        isPlaying = false
        let waitSec = max(20, min(120, maxWaitSeconds))
        adCountdownTask = Task { @MainActor in
            while self.adCountdown > 0, !Task.isCancelled, self.showAd {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if !Task.isCancelled, self.showAd {
                    self.adCountdown -= 1
                }
            }
            if !Task.isCancelled, self.showAd {
                self.adCanClose = true
            }
        }
        adTimeoutTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(waitSec) * 1_000_000_000)
            if !Task.isCancelled { await onAdVideoEnded() }
        }
        adEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.onAdVideoEnded()
            }
        }
    }

    private func adPlaybackFailedFallbackToMain() async {
        guard showAd else { return }
        adItemStatusCancellable?.cancel()
        adItemStatusCancellable = nil
        adTimeoutTask?.cancel()
        adTimeoutTask = nil
        adCountdownTask?.cancel()
        adCountdownTask = nil
        if let o = adEndObserver {
            NotificationCenter.default.removeObserver(o)
            adEndObserver = nil
        }
        adPlayer?.pause()
        adPlayer = nil
        adImageURL = nil
        adCountdown = 0
        adCanClose = false
        adGrantsTemporaryUnlock = false
        showAd = false
        await playMainStream()
    }

    private func onAdVideoEnded() async {
        // 防「播完 + 超时」或重复通知双进
        guard showAd else { return }
        await finishAdAndPlayMain()
    }

    func finishAdAndPlayMain() async {
        guard showAd else { return }
        if adGrantsTemporaryUnlock, let ep = current {
            grantTemporaryUnlock(ep.id)
        }
        clearAd()
        await playMainStream()
    }

    func requestCloseAd() {
        if adCanClose {
            Task { @MainActor in
                await finishAdAndPlayMain()
            }
        } else {
            confirmAbandonAd = true
            if adPlayer?.rate != 0 {
                adPlayer?.pause()
            }
        }
    }

    func continueAd() {
        confirmAbandonAd = false
        if showAd, adImageURL == nil {
            adPlayer?.play()
        }
    }

    func abandonAdUnlock() {
        confirmAbandonAd = false
        clearAd()
        Task { await VideoCacheManager.shared.cancelPrecache() }
        clearTimeObserver()
        player?.pause()
        player = nil
        isPlaying = false
    }

    private func playMainStream() async {
        if Task.isCancelled { return }
        clearAd()
        guard let ep = current else { return }
        let u: URL?
        if ep.id == handoffEpisodeId, let handoffStreamURL {
            u = handoffStreamURL
        } else {
            u = PlaybackURL.url(for: ep)
        }
        guard let u else {
            /// 由 `runAdThenMain` 等路径先置 `streamPreparing = true` 时：无可用拉流 URL 须清掉加载态。
            streamPreparing = false
            return
        }
        var headers: [String: String] = [:]
        if let t = authToken, !t.isEmpty {
            headers["Authorization"] = "Bearer \(t)"
        }
        streamPreparing = true
        defer { streamPreparing = false }
        let asset = await VideoCacheManager.shared.playbackAVURLAsset(remoteURL: u, headers: headers, episodeId: ep.id)
        let item = AVPlayerItem(asset: asset)
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] note in
            Task { @MainActor in
                let error = (note.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?.localizedDescription
                self?.loadError = error ?? "视频播放失败，请重试"
            }
        }
        clearTimeObserver()
        player?.pause()
        player = AVPlayer(playerItem: item)
        installTimeObserver()
        if let o = mainEndObserver {
            NotificationCenter.default.removeObserver(o)
            mainEndObserver = nil
        }
        mainEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.onMainVideoPlayedToEnd()
            }
        }
        if ep.id == handoffEpisodeId, handoffPositionSeconds > 0 {
            let target = CMTime(seconds: handoffPositionSeconds, preferredTimescale: 600)
            await player?.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
        }
        player?.play()
        isPlaying = true
        if let t = authToken, !t.isEmpty {
            if let i = try? await APIClient.shared.getEpisodeInteraction(episodeId: ep.id, token: t) {
                self.liked = i.liked
                self.collected = i.collected
            }
            _ = try? await APIClient.shared.recordHistory(episodeId: ep.id, token: t)
        }
        await prefetchNextEpisodeOnly()
    }

    private func onMainVideoPlayedToEnd() {
        guard !showAd else { return }
        guard current != nil else { return }
        playbackProgress = 1
        isPlaying = false
        if !selectRelativeEpisode(offset: 1), isOnLastEpisode {
            if let cb = onRequestNextDramaFromFeed {
                Task { @MainActor in cb() }
            }
        }
    }

    private func clearAd() {
        adItemStatusCancellable?.cancel()
        adItemStatusCancellable = nil
        adTimeoutTask?.cancel()
        adTimeoutTask = nil
        adCountdownTask?.cancel()
        adCountdownTask = nil
        if let o = adEndObserver {
            NotificationCenter.default.removeObserver(o)
            adEndObserver = nil
        }
        adPlayer?.pause()
        adPlayer = nil
        adImageURL = nil
        adCountdown = 0
        adCanClose = false
        adGrantsTemporaryUnlock = false
        showAd = false
    }

    private func installTimeObserver() {
        removePeriodicTimeObserverOnly()
        guard let player else { return }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            Task { @MainActor in
                guard let self else { return }
                let duration = player.currentItem?.duration.seconds ?? 0
                guard duration.isFinite, duration > 0 else {
                    self.playbackProgress = 0
                    return
                }
                self.playbackProgress = min(1, max(0, time.seconds / duration))
            }
        }
        timeObserverPlayer = player
    }

    private func removePeriodicTimeObserverOnly() {
        if let token = timeObserver, let owner = timeObserverPlayer {
            owner.removeTimeObserver(token)
        }
        timeObserver = nil
        timeObserverPlayer = nil
    }

    private func clearTimeObserver() {
        removePeriodicTimeObserverOnly()
        if let o = mainEndObserver {
            NotificationCenter.default.removeObserver(o)
            mainEndObserver = nil
        }
        playbackProgress = 0
        isPlaying = false
    }

    private func grantTemporaryUnlock(_ episodeId: Int64) {
        temporaryUnlockExpiry[episodeId] = Date().addingTimeInterval(10 * 60)
    }

    private func syncCountsFromCurrent() {
        likeCount = current?.likeCount ?? 0
        commentCount = current?.commentCount ?? 0
    }

    private func prefetchNextEpisodeOnly() async {
        guard let cur = current, let idx = episodes.firstIndex(where: { $0.id == cur.id }) else { return }
        let next = idx + 1
        guard episodes.indices.contains(next), let url = PlaybackURL.url(for: episodes[next]) else { return }
        var hdr: [String: String] = [:]
        if let t = authToken, !t.isEmpty { hdr["Authorization"] = "Bearer \(t)" }
        let eid = episodes[next].id
        await VideoCacheManager.shared.precache(url, headers: hdr, episodeId: eid)
    }

    // MARK: - 免广告（对齐 Android `AdSkipCache`）

    private func refreshAdSkipSnapshotIfLoggedIn() async {
        if let t = authToken, !t.isEmpty {
            adSkipStatusSnapshot = try? await APIClient.shared.getAdSkipStatus(token: t)
        } else {
            adSkipStatusSnapshot = nil
        }
    }

    /// 个人中心等购买免广成功后刷新并重走播放流水线。
    func refreshAdSkipAndRetryPipeline() async {
        await refreshAdSkipSnapshotIfLoggedIn()
        startPlaybackPipeline()
    }

    /// 对齐 `AdSkipCache.shouldSkipCoinUnlockDialogForAdSkip`。
    private func shouldSkipCoinUnlockDialogForAdSkip() -> Bool {
        guard let t = authToken, !t.isEmpty else { return false }
        guard let st = adSkipStatusSnapshot else { return false }
        guard isAdSkipEffectivelyActive(st) else { return false }
        let rem = st.adSkipRemaining
        if rem > 0 { return true }
        if rem < 0 { return true }
        return false
    }

    private func isAdSkipEffectivelyActive(_ st: AdSkipStatus) -> Bool {
        guard st.adSkipActive else { return false }
        guard let raw = st.adSkipExpiresAt?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return true
        }
        guard let exp = Self.parseAdSkipExpiresAt(raw) else { return st.adSkipActive }
        return exp > Date()
    }

    private static func parseAdSkipExpiresAt(_ raw: String) -> Date? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty { return nil }
        let isoFrac = ISO8601DateFormatter()
        isoFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = isoFrac.date(from: s) { return d }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        if let d = iso.date(from: s) { return d }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        if let d = df.date(from: s) { return d }
        df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        if let d = df.date(from: s) { return d }
        if s.count >= 19, let idx = s.firstIndex(of: "+"), idx > s.startIndex {
            df.dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
            if let d = df.date(from: s) { return d }
        }
        return nil
    }

    private func isTemporarilyUnlocked(_ episodeId: Int64) -> Bool {
        guard let exp = temporaryUnlockExpiry[episodeId] else { return false }
        if Date() >= exp {
            temporaryUnlockExpiry.removeValue(forKey: episodeId)
            return false
        }
        return true
    }

}
