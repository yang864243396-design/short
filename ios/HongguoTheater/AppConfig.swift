import Foundation

/// 与 Android `BuildConfig.BASE_URL` 一致：必须在末尾带 `/`。
enum AppConfig {
    static var apiBase: String {
        (Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        ?? "http://192.168.20.2:8080/api/v1/"
    }

    /// 去掉 `/api/v1/`，用于拼接图片相对路径（对齐 Android `ImageUrlUtils`）
    static var publicOrigin: String {
        var s = apiBase
        if s.hasSuffix("/api/v1/") {
            s = String(s.dropLast("/api/v1/".count))
        } else if s.hasSuffix("api/v1/") {
            s = String(s.dropLast("api/v1/".count))
        }
        if s.hasSuffix("/") == false { s += "/" }
        return s
    }

    static func streamURLString(episodeId: Int64) -> String {
        apiBase + "stream/\(episodeId)"
    }
}
