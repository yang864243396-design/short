import Foundation

enum ImageURL {
    /// 对齐 Android `ImageUrlUtils.resolve`：绝对 URL、`//` 协议相对、再拼 `BASE_URL` 去 `/api/v1/` 后的站点根。
    static func resolve(_ path: String?) -> URL? {
        guard let raw = path?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        if raw.hasPrefix("//") {
            let api = AppConfig.apiBase.trimmingCharacters(in: .whitespacesAndNewlines)
            if api.lowercased().hasPrefix("https") {
                return URL(string: "https:" + raw)
            }
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
