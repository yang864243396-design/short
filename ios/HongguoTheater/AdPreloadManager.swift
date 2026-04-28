import Foundation

actor AdPreloadManager {
    static let shared = AdPreloadManager()

    private var warming = false

    func warmupIfNeeded(isLoggedIn: Bool) async {
        guard !isLoggedIn, !warming else { return }
        warming = true
        defer { warming = false }
        guard let payload = await APIClient.shared.getAdVideoPayload(episodeId: nil, token: nil) else { return }
        let mediaType = (payload.mediaType ?? "video").lowercased()
        guard mediaType != "image", let url = PlaybackURL.adVideoAbsoluteURL(payload.videoUrl) else { return }
        await VideoCacheManager.shared.precache(url)
    }
}
