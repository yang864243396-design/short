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
        guard mediaType != "image", let url = resolveMedia(payload.videoUrl) else { return }
        await VideoCacheManager.shared.precache(url)
    }

    private func resolveMedia(_ raw: String?) -> URL? {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else { return nil }
        if s.hasPrefix("http://") || s.hasPrefix("https://") { return URL(string: s) }
        var origin = AppConfig.publicOrigin
        if origin.hasSuffix("/") == false { origin += "/" }
        let p = s.hasPrefix("/") ? String(s.dropFirst()) : s
        return URL(string: origin + p)
    }
}
