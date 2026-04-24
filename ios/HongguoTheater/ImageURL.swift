import Foundation

enum ImageURL {
    static func resolve(_ path: String?) -> URL? {
        guard let raw = path?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        let origin = AppConfig.publicOrigin.trimmingCharacters(in: .whitespacesAndNewlines)
        let p = raw.hasPrefix("/") ? String(raw.dropFirst()) : raw
        return URL(string: origin + p)
    }
}
