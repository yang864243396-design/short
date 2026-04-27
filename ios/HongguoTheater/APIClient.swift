import Foundation

extension Notification.Name {
    /// 与 Android `TokenInterceptor` 在 401 时登出并提示登录 对齐
    static let hgAuthRequired = Notification.Name("hgAuthRequired")
}

enum APIError: LocalizedError {
    case badURL
    case http(Int, String?)
    case decode
    case business(String)
    case noData

    var errorDescription: String? {
        switch self {
        case .badURL: return "无效地址"
        case .http(let c, let m): return m ?? "HTTP \(c)"
        case .decode: return "数据解析失败"
        case .business(let m): return m
        case .noData: return "暂无数据"
        }
    }
}

/// 与 Android `ApiClient` + `TokenInterceptor` 行为对齐：Bearer、ad-skip 不走缓存
final class APIClient: @unchecked Sendable {
    static let shared = APIClient()

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    private init() {}

    private func buildRequest(
        path: String,
        method: String,
        query: [String: String]? = nil,
        jsonBody: [String: Any]? = nil,
        token: String?
    ) throws -> URLRequest {
        var base = AppConfig.apiBase
        if base.hasSuffix("/") == false { base += "/" }
        let p = path.hasPrefix("/") ? String(path.dropFirst()) : path
        var comp = URLComponents(string: base + p)
        if let q = query, !q.isEmpty {
            comp?.queryItems = q.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = comp?.url else { throw APIError.badURL }
        var req = URLRequest(url: url)
        req.httpMethod = method
        if let t = token, !t.isEmpty {
            req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        if path.contains("ad-skip") {
            req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        }
        if let body = jsonBody {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }
        return req
    }

    private func data(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (d, r) = try await URLSession.shared.data(for: request)
        guard let http = r as? HTTPURLResponse else { throw APIError.http(-1, nil) }
        return (d, http)
    }

    /// 用于 401 时由界面层配合 `SessionStore` 清理登录态
    func fetchResponse<T: Decodable & Sendable>(
        path: String,
        method: String = "GET",
        query: [String: String]? = nil,
        jsonBody: [String: Any]? = nil,
        token: String?
    ) async throws -> APIResponse<T> {
        let req = try buildRequest(path: path, method: method, query: query, jsonBody: jsonBody, token: token)
        let (d, http) = try await data(req)
        if http.statusCode == 401 {
            Task { @MainActor in
                NotificationCenter.default.post(name: .hgAuthRequired, object: nil)
            }
            throw APIError.http(401, "未授权")
        }
        if (200 ... 299).contains(http.statusCode) == false {
            throw APIError.http(http.statusCode, String(data: d, encoding: .utf8))
        }
        return try decoder.decode(APIResponse<T>.self, from: d)
    }

    func uploadAvatar(imageData: Data, filename: String, mime: String, token: String) async throws -> User {
        let base = AppConfig.apiBase
        let urlString = base.hasSuffix("/") ? base + "user/avatar" : base + "/user/avatar"
        guard let url = URL(string: urlString) else { throw APIError.badURL }
        let boundary = "Boundary-\(UUID().uuidString)"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mime)\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        let (d, http) = try await data(req)
        if http.statusCode == 401 {
            Task { @MainActor in
                NotificationCenter.default.post(name: .hgAuthRequired, object: nil)
            }
            throw APIError.http(401, "未授权")
        }
        if (200 ... 299).contains(http.statusCode) == false {
            throw APIError.http(http.statusCode, String(data: d, encoding: .utf8))
        }
        let res: APIResponse<User> = try decoder.decode(APIResponse<User>.self, from: d)
        guard res.isSuccess, let u = res.data else {
            throw APIError.business(res.message ?? "上传失败")
        }
        return u
    }
}

// MARK: - API 封装（与 Android `ApiService` 对应）
extension APIClient {
    func getHome(token: String?) async throws -> HomeData {
        let r: APIResponse<HomeData> = try await fetchResponse(path: "home", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getBanners(token: String?) async throws -> [BannerItem] {
        let r: APIResponse<[BannerItem]> = try await fetchResponse(path: "banners", token: token)
        guard r.isSuccess, let d = r.data else { return [] }
        return d
    }

    func getDramas(category: String?, page: Int, pageSize: Int, token: String?) async throws -> [Drama] {
        var q: [String: String] = ["page": "\(page)", "page_size": "\(pageSize)"]
        if let c = category, !c.isEmpty { q["category"] = c }
        let r: APIResponse<[Drama]> = try await fetchResponse(path: "dramas", query: q, token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getDramaDetail(id: Int64, token: String?) async throws -> Drama {
        let r: APIResponse<Drama> = try await fetchResponse(path: "dramas/\(id)", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getDramaEpisodes(dramaId: Int64, token: String?) async throws -> [Episode] {
        let r: APIResponse<[Episode]> = try await fetchResponse(path: "dramas/\(dramaId)/episodes", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func search(keyword: String, token: String?) async throws -> [Drama] {
        let r: APIResponse<[Drama]> = try await fetchResponse(
            path: "search",
            query: ["keyword": keyword],
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "搜索失败") }
        return d
    }

    func getSearchHistory(token: String?) async throws -> [String] {
        let r: APIResponse<[String]> = try await fetchResponse(path: "search/history", token: token)
        guard r.isSuccess, let d = r.data else { return [] }
        return d
    }

    func clearSearchHistory(token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(path: "search/history", method: "DELETE", token: token)
        guard r.isSuccess else { throw APIError.business(r.message ?? "清空失败") }
    }

    func getRankings(type: String, token: String?) async throws -> [RankItem] {
        let r: APIResponse<[RankItem]> = try await fetchResponse(
            path: "rankings",
            query: ["type": type],
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getFeed(page: Int, pageSize: Int, episodeNumber: Int, token: String?) async throws -> [Episode] {
        let r: APIResponse<[Episode]> = try await fetchResponse(
            path: "feed",
            query: ["page": "\(page)", "page_size": "\(pageSize)", "episode_number": "\(episodeNumber)"],
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func login(email: String, password: String) async throws -> AuthData {
        let r: APIResponse<AuthData> = try await fetchResponse(
            path: "auth/login",
            method: "POST",
            jsonBody: ["email": email, "password": password],
            token: nil
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "登录失败") }
        return d
    }

    func register(email: String, password: String, code: String, nickname: String?) async throws -> AuthData {
        var b: [String: Any] = ["email": email, "password": password, "code": code]
        if let n = nickname, !n.isEmpty { b["nickname"] = n }
        let r: APIResponse<AuthData> = try await fetchResponse(
            path: "auth/register",
            method: "POST",
            jsonBody: b,
            token: nil
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "注册失败") }
        return d
    }

    func sendRegisterCode(email: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "auth/send-register-code",
            method: "POST",
            jsonBody: ["email": email],
            token: nil
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "发送失败") }
    }

    func getProfile(token: String) async throws -> User {
        let r: APIResponse<User> = try await fetchResponse(path: "user/profile", token: token)
        guard r.isSuccess, let u = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return u
    }

    func getHistory(token: String) async throws -> [WatchHistory] {
        let r: APIResponse<[WatchHistory]> = try await fetchResponse(path: "user/history", token: token)
        guard r.isSuccess, let d = r.data else { return [] }
        return d
    }

    func getCollections(token: String) async throws -> [Drama] {
        let r: APIResponse<[Drama]> = try await fetchResponse(path: "user/collections", token: token)
        guard r.isSuccess, let d = r.data else { return [] }
        return d
    }

    func getLikedEpisodes(token: String) async throws -> [Episode] {
        let r: APIResponse<[Episode]> = try await fetchResponse(path: "user/likes", token: token)
        guard r.isSuccess, let d = r.data else { return [] }
        return d
    }

    func getWallet(token: String) async throws -> WalletBalance {
        let r: APIResponse<WalletBalance> = try await fetchResponse(path: "user/wallet", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getWalletTransactions(page: Int, pageSize: Int, type: String?, token: String) async throws -> WalletTransactionsPage {
        var q: [String: String] = ["page": "\(page)", "page_size": "\(pageSize)"]
        if let t = type, !t.isEmpty { q["type"] = t }
        let r: APIResponse<WalletTransactionsPage> = try await fetchResponse(
            path: "user/wallet/transactions",
            query: q,
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func getRechargePackages(token: String) async throws -> RechargePackagesEnvelope {
        let r: APIResponse<RechargePackagesEnvelope> = try await fetchResponse(path: "recharge-packages", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func createRechargeOrder(packageId: Int64, productId: String?, token: String) async throws -> CreateRechargeOrderResponse {
        var b: [String: Any] = ["package_id": Int(packageId)]
        if let pid = productId, !pid.isEmpty { b["product_id"] = pid }
        let r: APIResponse<CreateRechargeOrderResponse> = try await fetchResponse(
            path: "recharge-orders",
            method: "POST",
            jsonBody: b,
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "下单失败") }
        return d
    }

    func queryRechargeOrder(mchOrderNo: String?, payOrderId: String?, token: String) async throws -> CreateRechargeOrderResponse {
        var q: [String: String] = [:]
        if let m = mchOrderNo, !m.isEmpty { q["mch_order_no"] = m }
        if let p = payOrderId, !p.isEmpty { q["pay_order_id"] = p }
        let r: APIResponse<CreateRechargeOrderResponse> = try await fetchResponse(
            path: "recharge-orders/query",
            query: q,
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "查询失败") }
        return d
    }

    func simulateRechargePay(orderId: Int64, token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "recharge-orders/\(orderId)/simulate-pay",
            method: "POST",
            token: token
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "模拟支付失败") }
    }

    func getAdSkipStatus(token: String) async throws -> AdSkipStatus {
        let r: APIResponse<AdSkipStatus> = try await fetchResponse(path: "user/ad-skip", token: token)
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func purchaseAdSkip(configId: Int64, token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "user/ad-skip/purchase",
            method: "POST",
            jsonBody: ["config_id": Int(configId)],
            token: token
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "购买失败") }
    }

    func getEpisodeInteraction(episodeId: Int64, token: String) async throws -> EpisodeInteraction {
        let r: APIResponse<EpisodeInteraction> = try await fetchResponse(
            path: "episodes/\(episodeId)/interaction",
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func likeEpisode(episodeId: Int64, token: String) async throws -> LikeEpisodeResult {
        let r: APIResponse<LikeEpisodeResult> = try await fetchResponse(
            path: "episodes/\(episodeId)/like",
            method: "POST",
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "操作失败") }
        return d
    }

    func collectForEpisodeDrama(episodeId: Int64, token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "episodes/\(episodeId)/collect",
            method: "POST",
            token: token
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "操作失败") }
    }

    func recordHistory(episodeId: Int64, token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "episodes/\(episodeId)/history",
            method: "POST",
            token: token
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "记录失败") }
    }

    func unlockEpisodeWithCoins(episodeId: Int64, token: String) async throws {
        let r: APIResponse<OptionalEmpty> = try await fetchResponse(
            path: "episodes/\(episodeId)/unlock-coins",
            method: "POST",
            token: token
        )
        guard r.isSuccess else { throw APIError.business(r.message ?? "解锁失败") }
    }

    /// 播放前广告；失败或 400 无广告时返回 `nil`（与 Android 失败则直播正片 一致）
    func getAdVideoPayload(episodeId: Int64?, token: String?) async -> AdVideoPayload? {
        do {
            var q: [String: String] = [:]
            if let e = episodeId, e > 0 { q["episode_id"] = "\(e)" }
            let r: APIResponse<AdVideoPayload> = try await fetchResponse(
                path: "ad/video",
                query: q.isEmpty ? nil : q,
                token: token
            )
            guard r.isSuccess, let d = r.data else { return nil }
            return d
        } catch {
            return nil
        }
    }

    func getComments(episodeId: Int64, page: Int, pageSize: Int, token: String?) async throws -> CommentListPayload {
        let r: APIResponse<CommentListPayload> = try await fetchResponse(
            path: "episodes/\(episodeId)/comments",
            query: ["page": "\(page)", "page_size": "\(pageSize)"],
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "加载失败") }
        return d
    }

    func postComment(episodeId: Int64, content: String, parentId: Int64?, token: String) async throws {
        var b: [String: Any] = ["content": content]
        if let p = parentId, p > 0 { b["parent_id"] = Int(p) }
        let r: APIResponse<CommentItem> = try await fetchResponse(
            path: "episodes/\(episodeId)/comments",
            method: "POST",
            jsonBody: b,
            token: token
        )
        guard r.isSuccess, r.data != nil else { throw APIError.business(r.message ?? "发送失败") }
    }

    func likeComment(commentId: Int64, token: String) async throws -> CommentLikeResult {
        let r: APIResponse<CommentLikeResult> = try await fetchResponse(
            path: "comments/\(commentId)/like",
            method: "POST",
            token: token
        )
        guard r.isSuccess, let d = r.data else { throw APIError.business(r.message ?? "操作失败") }
        return d
    }
}

/// 用于 `data` 为 null 的响应
struct OptionalEmpty: Decodable, Sendable {}
