import AVFoundation
import CryptoKit
import Foundation

/// 对齐 Android `ExoPlayerCache`：200MB LRU、目录 `video_cache`、**单一后台 precache**（新开取消旧任务）、
/// 正片优先 **边播边写**（`HGStreamCacheLoader` ≈ `CacheDataSource`），完整文件与后台 `precache` 共用 `ep_*.mp4`。
actor VideoCacheManager {
    static let shared = VideoCacheManager()

    private static let maxBytes: UInt64 = 200 * 1024 * 1024
    private static let cacheMarkerName = ".cache_v4"

    private let directory: URL
    private var exclusivePrecacheTask: Task<Void, Never>?
    private var precacheRunId: UUID?
    /// `AVAssetResourceLoader` 对 delegate 为 **weak**；必须在 App 生命周期内强引用 `HGStreamCacheLoader`，否则会立即释放导致拉流失败、画面卡在「不可播」态。
    private var streamLoadersByEpisodeId: [Int64: HGStreamCacheLoader] = [:]

    private init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        directory = base.appendingPathComponent("video_cache", isDirectory: true)
        ensureCacheDirectory()
    }

    private func ensureCacheDirectory() {
        let marker = directory.appendingPathComponent(Self.cacheMarkerName)
        if FileManager.default.fileExists(atPath: directory.path), !FileManager.default.fileExists(atPath: marker.path) {
            try? FileManager.default.removeItem(at: directory)
        }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if !FileManager.default.fileExists(atPath: marker.path) {
            FileManager.default.createFile(atPath: marker.path, contents: nil)
        }
    }

    /// 对齐 Android `ExoPlayerCache.cancelPrecache`。
    func cancelPrecache() {
        exclusivePrecacheTask?.cancel()
        exclusivePrecacheTask = nil
        precacheRunId = nil
    }

    /// 刷剧列表整表重置（下拉/重新进 Tab）：停掉整文件 precache + 所有边播边写。
    func resetFeedSession() {
        cancelPrecache()
        for (_, loader) in streamLoadersByEpisodeId {
            loader.cancel()
        }
        streamLoadersByEpisodeId.removeAll()
    }

    /// 划到上一部 → 下一部：取消上一部的整文件 precache 与边播边写；当前部在 `playbackAVURLAssetForFeed` 里再起后台 precache。
    func prepareFeedSwitch(fromEpisodeId: Int64?, newEpisodeId: Int64) {
        cancelPrecache()
        guard let old = fromEpisodeId, old > 0, old != newEpisodeId else { return }
        if let loader = streamLoadersByEpisodeId.removeValue(forKey: old) {
            loader.cancel()
        }
    }

    /// 刷剧专用：已落盘则 `file://`；否则 **直链 HTTP** 立即起播（跳过 HEAD + ResourceLoader 探测延迟），并后台 `precache` 同一 `ep_*.mp4`。
    /// 全屏看剧仍用 `playbackAVURLAsset` 保留边播边写。
    func playbackAVURLAssetForFeed(remoteURL: URL, headers: [String: String], episodeId: Int64) async -> AVURLAsset {
        if let file = cachedURL(for: remoteURL, episodeId: episodeId) {
            if episodeId > 0 {
                streamLoadersByEpisodeId.removeValue(forKey: episodeId)
            }
            return AVURLAsset(url: file)
        }
        if episodeId > 0 {
            streamLoadersByEpisodeId.removeValue(forKey: episodeId)
        }
        precache(remoteURL, headers: headers, episodeId: episodeId > 0 ? episodeId : nil)
        return remoteAsset(url: remoteURL, headers: headers)
    }

    /// - Parameters:
    ///   - episodeId: 分集 ID 作为稳定存储键（与全量缓存、边播边写一致）。
    func cachedURL(for remoteURL: URL, episodeId: Int64?) -> URL? {
        let key = Self.storageKey(remoteURL: remoteURL, episodeId: episodeId)
        let url = fileURL(forStorageKey: key)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        touch(url)
        return url
    }

    /// 已缓存则直接本地 `AVURLAsset`；否则在能探测到 `Content-Length` 时用自定义 scheme + ResourceLoader **边播边写**；
    /// 与 Android 一致失败时走直链并 **`precache`（会 cancel 前一个 precache）**。
    func playbackAVURLAsset(remoteURL: URL, headers: [String: String], episodeId: Int64) async -> AVURLAsset {
        if let file = cachedURL(for: remoteURL, episodeId: episodeId) {
            if episodeId > 0 {
                streamLoadersByEpisodeId.removeValue(forKey: episodeId)
            }
            return AVURLAsset(url: file)
        }
        guard episodeId > 0 else {
            precache(remoteURL, headers: headers, episodeId: nil)
            return remoteAsset(url: remoteURL, headers: headers)
        }
        let key = Self.storageKey(remoteURL: remoteURL, episodeId: episodeId)
        let dest = fileURL(forStorageKey: key)
        let partial = directory.appendingPathComponent(key).appendingPathExtension("partial")
        let length = await Self.probeContentLength(url: remoteURL, headers: headers)
        if length > 0 {
            let loader = HGStreamCacheLoader(
                remoteURL: remoteURL,
                headers: headers,
                destinationURL: dest,
                partialURL: partial,
                contentLength: length
            )
            streamLoadersByEpisodeId[episodeId] = loader
            let custom = URL(string: "hgstream://episode/\(episodeId)")!
            let asset = AVURLAsset(url: custom)
            asset.resourceLoader.setDelegate(loader, queue: loader.processingQueue)
            return asset
        }
        streamLoadersByEpisodeId.removeValue(forKey: episodeId)
        precache(remoteURL, headers: headers, episodeId: episodeId)
        return remoteAsset(url: remoteURL, headers: headers)
    }

    /// 兼容旧调用：返回用于 `AVURLAsset(url:)` 的 URL（无 ResourceLoader）。
    func playableURL(for remoteURL: URL, headers: [String: String], episodeId: Int64? = nil) async -> URL {
        if let u = cachedURL(for: remoteURL, episodeId: episodeId) { return u }
        precache(remoteURL, headers: headers, episodeId: episodeId)
        return remoteURL
    }

    /// 对齐 Android `ExoPlayerCache.precache`：先 `cancelPrecache`，再整文件下载；与播放写入同一 `ep_*.mp4`。
    func precache(_ remoteURL: URL, headers: [String: String] = [:], episodeId: Int64? = nil) {
        let key = Self.storageKey(remoteURL: remoteURL, episodeId: episodeId)
        let dest = fileURL(forStorageKey: key)
        if FileManager.default.fileExists(atPath: dest.path) { return }

        cancelPrecache()
        let directory = self.directory
        let maxBytes = Self.maxBytes
        let run = UUID()
        precacheRunId = run
        exclusivePrecacheTask = Task.detached(priority: .utility) {
            defer {
                Task { await VideoCacheManager.shared.finishPrecacheRun(run) }
            }
            var request = URLRequest(url: remoteURL)
            request.timeoutInterval = 60
            for (k, v) in headers {
                request.setValue(v, forHTTPHeaderField: k)
            }
            do {
                let (tempURL, response) = try await URLSession.shared.download(for: request)
                guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else { return }
                if FileManager.default.fileExists(atPath: dest.path) {
                    try? FileManager.default.removeItem(at: dest)
                }
                try FileManager.default.moveItem(at: tempURL, to: dest)
                try? FileManager.default.setAttributes(
                    [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                    ofItemAtPath: dest.path
                )
                Self.evictIfNeeded(directory: directory, maxBytes: maxBytes)
            } catch {}
        }
    }

    func finishPrecacheRun(_ id: UUID) {
        guard precacheRunId == id else { return }
        precacheRunId = nil
        exclusivePrecacheTask = nil
    }

    func runEvictIfNeeded() {
        Self.evictIfNeeded(directory: directory, maxBytes: Self.maxBytes)
    }

    func cancelAll() {
        cancelPrecache()
    }

    func clearAll() {
        cancelPrecache()
        streamLoadersByEpisodeId.removeAll()
        if let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            for file in files {
                try? FileManager.default.removeItem(at: file)
            }
        }
        ensureCacheDirectory()
    }

    private func remoteAsset(url: URL, headers: [String: String]) -> AVURLAsset {
        let options: [String: Any]? = headers.isEmpty ? nil : ["AVURLAssetHTTPHeaderFieldsKey": headers]
        return AVURLAsset(url: url, options: options)
    }

    private func fileURL(forStorageKey key: String) -> URL {
        directory.appendingPathComponent(key).appendingPathExtension("mp4")
    }

    private func touch(_ url: URL) {
        try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: url.path)
    }

    private nonisolated static func probeContentLength(url: URL, headers: [String: String]) async -> Int64 {
        var head = URLRequest(url: url)
        head.httpMethod = "HEAD"
        for (k, v) in headers { head.setValue(v, forHTTPHeaderField: k) }
        do {
            let (_, response) = try await URLSession.shared.data(for: head)
            if let http = response as? HTTPURLResponse, http.statusCode == 200, http.expectedContentLength > 0 {
                return Int64(http.expectedContentLength)
            }
        } catch {}
        var getHead = URLRequest(url: url)
        for (k, v) in headers { getHead.setValue(v, forHTTPHeaderField: k) }
        getHead.setValue("bytes=0-0", forHTTPHeaderField: "Range")
        do {
            let (_, response) = try await URLSession.shared.data(for: getHead)
            if let http = response as? HTTPURLResponse,
               let cr = http.value(forHTTPHeaderField: "Content-Range"),
               let t = HGStreamCacheLoader.parseContentRangeTotal(cr) {
                return t
            }
            if let http = response as? HTTPURLResponse, http.expectedContentLength > 0 {
                return Int64(http.expectedContentLength)
            }
        } catch {}
        return -1
    }

    static func storageKey(remoteURL: URL, episodeId: Int64?) -> String {
        if let id = episodeId, id > 0 {
            return "ep_\(id)"
        }
        if let inferred = inferredEpisodeId(from: remoteURL), inferred > 0 {
            return "ep_\(inferred)"
        }
        return staticCacheKey(remoteURL)
    }

    private static func inferredEpisodeId(from url: URL) -> Int64? {
        let path = url.path
        guard let r = path.range(of: "/stream/", options: .backwards) else { return nil }
        let tail = path[r.upperBound...]
        let num = tail.prefix { $0.isNumber }
        guard !num.isEmpty else { return nil }
        return Int64(num)
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
            if file.lastPathComponent == Self.cacheMarkerName { continue }
            total = total > size(file) ? total - size(file) : 0
            try? FileManager.default.removeItem(at: file)
        }
    }
}
