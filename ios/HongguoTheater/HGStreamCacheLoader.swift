import AVFoundation
import Foundation

/// 对齐 Android Media3 `CacheDataSource`：按 Range 拉流并写入 `partial`，满长后落盘为 `ep_*.mp4`，与全量 `precache` 共用最终文件。
final class HGStreamCacheLoader: NSObject, AVAssetResourceLoaderDelegate {
    let processingQueue = DispatchQueue(label: "com.hongguo.hgstream.loader")

    private let remoteURL: URL
    private let headers: [String: String]
    private let destinationURL: URL
    private let partialURL: URL
    private let contentLength: Int64
    private let session: URLSession

    init(
        remoteURL: URL,
        headers: [String: String],
        destinationURL: URL,
        partialURL: URL,
        contentLength: Int64
    ) {
        self.remoteURL = remoteURL
        self.headers = headers
        self.destinationURL = destinationURL
        self.partialURL = partialURL
        self.contentLength = contentLength
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 600
        self.session = URLSession(configuration: config)
        super.init()
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest) -> Bool {
        processingQueue.async { [weak self] in
            self?.handle(loadingRequest)
        }
        return true
    }

    private func handle(_ loadingRequest: AVAssetResourceLoadingRequest) {
        if loadingRequest.isCancelled { return }

        if FileManager.default.fileExists(atPath: destinationURL.path) {
            fulfillFromDisk(loadingRequest, file: destinationURL)
            return
        }

        if let cir = loadingRequest.contentInformationRequest {
            cir.contentType = "video/mp4"
            cir.contentLength = contentLength
            cir.isByteRangeAccessSupported = true
        }

        guard let dr = loadingRequest.dataRequest else {
            loadingRequest.finishLoading()
            return
        }

        let start = dr.requestedOffset
        var length = dr.requestedLength
        if length == 0 || length == Int.max {
            length = Int(max(0, contentLength - start))
        }
        length = max(1, min(length, Int(max(0, contentLength - start))))

        if let data = readPartialIfComplete(start: start, length: length) {
            dr.respond(with: data)
            tryFinalizeCompleteFile()
            loadingRequest.finishLoading()
            return
        }

        fetchRange(loadingRequest: loadingRequest, start: start, length: length)
    }

    private func readPartialIfComplete(start: Int64, length: Int) -> Data? {
        guard FileManager.default.fileExists(atPath: partialURL.path) else { return nil }
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: partialURL.path),
              let sz = attrs[.size] as? NSNumber, sz.int64Value >= start + Int64(length)
        else { return nil }
        guard let h = try? FileHandle(forReadingFrom: partialURL) else { return nil }
        defer { try? h.close() }
        try? h.seek(toOffset: UInt64(start))
        let data = h.readData(ofLength: length)
        return data.count >= length ? data : nil
    }

    private func fetchRange(loadingRequest: AVAssetResourceLoadingRequest, start: Int64, length: Int) {
        var req = URLRequest(url: remoteURL)
        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let end = start + Int64(length) - 1
        req.setValue("bytes=\(start)-\(end)", forHTTPHeaderField: "Range")

        let task = session.dataTask(with: req) { [weak self] data, response, error in
            guard let self else { return }
            self.processingQueue.async {
                if loadingRequest.isCancelled { return }
                if let error {
                    loadingRequest.finishLoading(with: error)
                    return
                }
                guard let data, !data.isEmpty,
                      let http = response as? HTTPURLResponse,
                      (200 ... 299).contains(http.statusCode) else {
                    let err = NSError(
                        domain: "HGStreamCache",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: "媒体 Range 请求失败"]
                    )
                    loadingRequest.finishLoading(with: err)
                    return
                }
                self.writePartial(data: data, at: start)
                loadingRequest.dataRequest?.respond(with: data)
                self.tryFinalizeCompleteFile()
                loadingRequest.finishLoading()
            }
        }
        task.resume()
    }

    /// 刷剧划走当前条时调用：停止 Range 拉取，避免与旧链路程竞争宽带上继续写 partial。
    func cancel() {
        processingQueue.async { [weak self] in
            guard let self else { return }
            self.session.invalidateAndCancel()
        }
    }

    private func writePartial(data: Data, at offset: Int64) {
        if !FileManager.default.fileExists(atPath: partialURL.path) {
            FileManager.default.createFile(atPath: partialURL.path, contents: nil)
        }
        guard let h = try? FileHandle(forUpdating: partialURL) else { return }
        defer { try? h.close() }
        try? h.seek(toOffset: UInt64(offset))
        do {
            try h.write(contentsOf: data)
            try h.synchronize()
        } catch {}
    }

    private func tryFinalizeCompleteFile() {
        guard contentLength > 0 else { return }
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: partialURL.path),
              let sz = attrs[.size] as? NSNumber, sz.int64Value >= contentLength
        else { return }
        try? FileManager.default.removeItem(at: destinationURL)
        try? FileManager.default.moveItem(at: partialURL, to: destinationURL)
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: destinationURL.path
        )
        Task { await VideoCacheManager.shared.runEvictIfNeeded() }
    }

    private func fulfillFromDisk(_ loadingRequest: AVAssetResourceLoadingRequest, file: URL) {
        let fileLen = (try? file.resourceValues(forKeys: [.fileSizeKey]))?.fileSize.map(Int64.init) ?? contentLength
        if let cir = loadingRequest.contentInformationRequest {
            cir.contentType = "video/mp4"
            cir.contentLength = fileLen
            cir.isByteRangeAccessSupported = true
        }
        guard let dr = loadingRequest.dataRequest else {
            loadingRequest.finishLoading()
            return
        }
        let start = dr.requestedOffset
        var length = dr.requestedLength
        if length == 0 || length == Int.max {
            length = Int(max(0, fileLen - start))
        }
        length = max(1, min(length, Int(max(0, fileLen - start))))
        guard let h = try? FileHandle(forReadingFrom: file) else {
            loadingRequest.finishLoading(with: NSError(domain: "HGStreamCache", code: -2, userInfo: nil))
            return
        }
        defer { try? h.close() }
        try? h.seek(toOffset: UInt64(start))
        let data = h.readData(ofLength: length)
        if !data.isEmpty { dr.respond(with: data) }
        loadingRequest.finishLoading()
    }

    static func parseContentRangeTotal(_ value: String) -> Int64? {
        guard let slash = value.lastIndex(of: "/") else { return nil }
        let tail = value[value.index(after: slash)...].trimmingCharacters(in: .whitespaces)
        return Int64(tail)
    }
}
