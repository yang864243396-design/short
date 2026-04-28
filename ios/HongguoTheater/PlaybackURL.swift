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

    /// 对齐 Android `PlayerActivity.fetchAndPlayAd`：`http(s)` 原样，否则 `BASE_URL` 去掉 `/api/v1/` 后与 `video_url` 拼接。
    static func adVideoAbsoluteURL(_ path: String?) -> URL? {
        guard let raw = path?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        if raw.hasPrefix("//") {
            let api = AppConfig.apiBase.trimmingCharacters(in: .whitespacesAndNewlines)
            if api.lowercased().hasPrefix("https") { return URL(string: "https:" + raw) }
            return URL(string: "http:" + raw)
        }
        var base = AppConfig.apiBase
        if base.hasSuffix("/api/v1/") {
            base = String(base.dropLast("/api/v1/".count))
        } else if base.hasSuffix("api/v1/") {
            base = String(base.dropLast("api/v1/".count))
        }
        base = base.trimmingCharacters(in: .whitespacesAndNewlines)
        while base.hasSuffix("/") { base.removeLast() }
        var rel = raw
        if !rel.hasPrefix("/") { rel = "/" + rel }
        return URL(string: base + rel)
    }
}
