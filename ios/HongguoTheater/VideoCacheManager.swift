import CryptoKit
import Foundation

actor VideoCacheManager {
    static let shared = VideoCacheManager()

    private let maxBytes: UInt64 = 200 * 1024 * 1024
    private let directory: URL
    private var prefetchTasks: [String: Task<Void, Never>] = [:]

    private init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        directory = base.appendingPathComponent("video_cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func cachedURL(for remoteURL: URL) -> URL? {
        let url = fileURL(for: remoteURL)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        touch(url)
        return url
    }

    func playableURL(for remoteURL: URL, headers: [String: String]) async -> URL {
        if let cached = cachedURL(for: remoteURL) {
            return cached
        }
        prefetch(remoteURL, headers: headers)
        return remoteURL
    }

    func prefetch(_ remoteURL: URL, headers: [String: String] = [:]) {
        let key = cacheKey(remoteURL)
        if FileManager.default.fileExists(atPath: fileURL(for: remoteURL).path) { return }
        if prefetchTasks[key] != nil { return }

        prefetchTasks[key] = Task.detached(priority: .utility) { [directory, maxBytes] in
            var request = URLRequest(url: remoteURL)
            request.timeoutInterval = 30
            for (k, v) in headers {
                request.setValue(v, forHTTPHeaderField: k)
            }
            do {
                let (tempURL, response) = try await URLSession.shared.download(for: request)
                guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else { return }
                let destination = directory.appendingPathComponent(Self.staticCacheKey(remoteURL)).appendingPathExtension("mp4")
                if FileManager.default.fileExists(atPath: destination.path) {
                    try? FileManager.default.removeItem(at: destination)
                }
                try FileManager.default.moveItem(at: tempURL, to: destination)
                try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: destination.path)
                Self.evictIfNeeded(directory: directory, maxBytes: maxBytes)
            } catch {}
        }
    }

    func cancelAll() {
        prefetchTasks.values.forEach { $0.cancel() }
        prefetchTasks.removeAll()
    }

    func clearAll() {
        cancelAll()
        if let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            for file in files {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    private func fileURL(for remoteURL: URL) -> URL {
        directory.appendingPathComponent(cacheKey(remoteURL)).appendingPathExtension("mp4")
    }

    private func cacheKey(_ url: URL) -> String {
        Self.staticCacheKey(url)
    }

    private func touch(_ url: URL) {
        try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: url.path)
    }

    private static func staticCacheKey(_ url: URL) -> String {
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func evictIfNeeded(directory: URL, maxBytes: UInt64) {
        guard var files = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey],
            options: []
        ) else { return }

        func size(_ url: URL) -> UInt64 {
            let values = try? url.resourceValues(forKeys: [.fileSizeKey])
            return UInt64(values?.fileSize ?? 0)
        }

        var total = files.reduce(UInt64(0)) { $0 + size($1) }
        guard total > maxBytes else { return }

        files.sort {
            let lhs = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            let rhs = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            return lhs < rhs
        }

        for file in files where total > maxBytes {
            total = total > size(file) ? total - size(file) : 0
            try? FileManager.default.removeItem(at: file)
        }
    }
}
