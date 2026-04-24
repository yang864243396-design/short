import Foundation

enum PlaybackURL {
    /// 对齐 Android `ApiClient.getStreamUrl(Episode)`
    static func url(for episode: Episode) -> URL? {
        if let s = episode.streamUrl, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            if t.hasPrefix("http://") || t.hasPrefix("https://") {
                return URL(string: t)
            }
            let origin = AppConfig.publicOrigin.trimmingCharacters(in: .whitespacesAndNewlines)
            let path = t.hasPrefix("/") ? String(t.dropFirst()) : t
            return URL(string: origin + path)
        }
        if let v = episode.videoUrl, !v.isEmpty, let u = URL(string: v) {
            return u
        }
        return URL(string: AppConfig.streamURLString(episodeId: episode.id))
    }

    static func dramaId(episode: Episode) -> Int64 {
        if episode.dramaId > 0 { return episode.dramaId }
        if let d = episode.drama { return d.id }
        return 0
    }
}
