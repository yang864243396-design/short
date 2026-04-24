import AVFoundation
import AVKit
import Foundation

@MainActor
final class PlayerViewModel: ObservableObject {
    @Published var player: AVPlayer?
    @Published var episodes: [Episode] = []
    @Published var current: Episode?
    @Published var drama: Drama?
    @Published var liked: Bool = false
    @Published var collected: Bool = false
    @Published var loadError: String?
    @Published var busy: Bool = false

    // 贴片：对齐 Android `fetchAndPlayAd` + 正片
    @Published var showAd: Bool = false
    @Published var adPlayer: AVPlayer?
    @Published var adImageURL: URL?
    @Published var adCountdown: Int = 0
    @Published var adErrorHint: String?

    let dramaId: Int64
    private let startEpisodeId: Int64?
    var authToken: String?
    private var adEndObserver: NSObjectProtocol?
    private var adTimeoutTask: Task<Void, Never>?
    private var playTask: Task<Void, Never>?

    init(dramaId: Int64, startEpisodeId: Int64?) {
        self.dramaId = dramaId
        self.startEpisodeId = startEpisodeId
    }

    private func shouldPaywall(_ ep: Episode) -> Bool {
        let free = ep.isFree ?? true
        let unlocked = ep.coinUnlocked ?? false
        return !free && !unlocked
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
            startPlaybackPipeline()
        } catch {
            loadError = error.localizedDescription
        }
    }

    func selectEpisode(_ ep: Episode) {
        playTask?.cancel()
        clearAd()
        current = ep
        startPlaybackPipeline()
    }

    func toggleLike() async {
        guard let t = authToken, !t.isEmpty, let ep = current else { return }
        do {
            try await APIClient.shared.likeEpisode(episodeId: ep.id, token: t)
            liked.toggle()
        } catch {}
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
            startPlaybackPipeline()
        } catch {
            loadError = error.localizedDescription
        }
    }

    // MARK: - 广告 + 正片
    private func startPlaybackPipeline() {
        playTask?.cancel()
        playTask = Task { @MainActor in
            await self.runAdThenMain()
        }
    }

    private func runAdThenMain() async {
        clearAd()
        player?.pause()
        player = nil
        guard let ep = current, !shouldPaywall(ep) else { return }
        if Task.isCancelled { return }
        let eid: Int64? = (authToken != nil && !authToken!.isEmpty) ? ep.id : nil
        if let p = await APIClient.shared.getAdVideoPayload(episodeId: eid, token: authToken) {
            if p.skipAd == true {
                await playMainStream()
                return
            }
            let dur = max(1, p.duration ?? 15)
            let mt = (p.mediaType ?? "video").lowercased()
            if Task.isCancelled { return }
            if mt == "image", let iu = resolveMedia(p.imageUrl) {
                await runImageAd(iu, seconds: dur)
            } else if let vu = resolveMedia(p.videoUrl) {
                // 片长时间约 dur 秒，超时取 2~3 倍作为兜底，与常见贴片长度同量级
                let cap = min(120, max(30, dur * 3))
                await runVideoAd(url: vu, maxWaitSeconds: cap)
            } else {
                await playMainStream()
            }
        } else {
            await playMainStream()
        }
    }

    private func resolveMedia(_ raw: String?) -> URL? {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else { return nil }
        if s.hasPrefix("http://") || s.hasPrefix("https://") { return URL(string: s) }
        var origin = AppConfig.publicOrigin
        if origin.hasSuffix("/") == false { origin += "/" }
        let p = s.hasPrefix("/") ? String(s.dropFirst()) : s
        return URL(string: origin + p)
    }

    private func runImageAd(_ url: URL, seconds: Int) async {
        showAd = true
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
        if Task.isCancelled {
            clearAd()
            return
        }
        adImageURL = nil
        showAd = false
        await playMainStream()
    }

    private func runVideoAd(url: URL, maxWaitSeconds: Int) async {
        adTimeoutTask?.cancel()
        adTimeoutTask = nil
        showAd = true
        adPlayer?.pause()
        adPlayer = nil
        if let o = adEndObserver { NotificationCenter.default.removeObserver(o) }
        let item = AVPlayerItem(url: url)
        let pl = AVPlayer(playerItem: item)
        adPlayer = pl
        pl.play()
        let waitSec = max(20, min(120, maxWaitSeconds))
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

    private func onAdVideoEnded() async {
        // 防「播完 + 超时」或重复通知双进
        guard showAd else { return }
        clearAd()
        await playMainStream()
    }

    private func playMainStream() async {
        if Task.isCancelled { return }
        clearAd()
        guard let ep = current, let u = PlaybackURL.url(for: ep) else { return }
        var headers: [String: String] = [:]
        if let t = authToken, !t.isEmpty {
            headers["Authorization"] = "Bearer \(t)"
        }
        let asset = AVURLAsset(url: u, options: [AVURLAssetHTTPHeaderFieldsKey: headers])
        let item = AVPlayerItem(asset: asset)
        player = AVPlayer(playerItem: item)
        player?.play()
        if let t = authToken, !t.isEmpty {
            if let i = try? await APIClient.shared.getEpisodeInteraction(episodeId: ep.id, token: t) {
                self.liked = i.liked
                self.collected = i.collected
            }
            _ = try? await APIClient.shared.recordHistory(episodeId: ep.id, token: t)
        }
    }

    private func clearAd() {
        adTimeoutTask?.cancel()
        adTimeoutTask = nil
        if let o = adEndObserver {
            NotificationCenter.default.removeObserver(o)
            adEndObserver = nil
        }
        adPlayer?.pause()
        adPlayer = nil
        adImageURL = nil
        adCountdown = 0
        showAd = false
    }

}
