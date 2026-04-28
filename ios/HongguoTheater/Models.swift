import Foundation

// MARK: - API envelope
struct APIResponse<T: Decodable & Sendable>: Decodable, Sendable {
    let code: Int
    let message: String?
    let data: T?
    var isSuccess: Bool { code == 200 }
}

// MARK: - Home & catalog
struct Category: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let name: String
    let sort: Int?
}

struct HomeData: Codable, Hashable, Sendable {
    let categories: [Category]?
    let mustWatch: [Drama]?
    let recommend: [Drama]?
    let hotRanking: [RankItem]?
}

struct BannerItem: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let title: String?
    let imageUrl: String?
    let linkType: String?
    let linkUrl: String?
    let dramaId: Int64?
    var isDramaLink: Bool { (linkType ?? "").lowercased() == "drama" }
}

struct RankItem: Codable, Hashable, Identifiable, Sendable {
    var id: Int64 { (drama?.id ?? 0) * 10_000 + Int64(rank) }
    let rank: Int
    let drama: Drama?
    let heat: Int64
    let badge: String?
    let totalLikes: Int64?
}

struct DramaRanking: Codable, Hashable, Sendable {
    let list: String?
    let rank: Int?
}

struct Drama: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let title: String?
    let description: String?
    let coverUrl: String?
    let category: String?
    let categoryList: [String]?
    let totalEpisodes: Int?
    let currentEpisode: Int?
    let rating: Double?
    let heat: Int64?
    let status: String?
    let createdAt: String?
    let ranking: DramaRanking?
}

struct Episode: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let dramaId: Int64
    let episodeNumber: Int
    let title: String?
    let videoUrl: String?
    let videoPath: String?
    let isFree: Bool?
    let unlockCoins: Int?
    let coinUnlocked: Bool?
    let likeCount: Int64?
    let commentCount: Int64?
    let viewCount: Int64?
    let streamUrl: String?
    var drama: Drama?
}

extension Episode {
    var isPlayable: Bool {
        let p = (videoPath ?? "").trimmingCharacters(in: .whitespaces)
        let s = (streamUrl ?? "").trimmingCharacters(in: .whitespaces)
        let v = (videoUrl ?? "").trimmingCharacters(in: .whitespaces)
        return !p.isEmpty || !s.isEmpty || !v.isEmpty
    }
}

// MARK: - User & wallet
struct User: Codable, Hashable, Sendable {
    let id: Int64
    let username: String?
    let nickname: String?
    let avatar: String?
    let coins: Int?

    var displayName: String {
        if let n = nickname, !n.isEmpty { return n }
        return username ?? "用户"
    }
}

struct WatchHistory: Codable, Hashable, Identifiable, Sendable {
    var id: String { "\(drama?.id ?? 0)-\(lastEpisode)-\(updatedAt ?? "")" }
    let drama: Drama?
    let lastEpisode: Int
    let progress: Double?
    let isFinished: Bool
    let updatedAt: String?
}

struct WalletBalance: Codable, Hashable, Sendable {
    let coins: Int
    let currencyName: String?
    let coinsPerYuan: Int?
    let balanceYuan: Double?
    let adSkipExpiresAt: String?
    let adSkipActive: Bool?
    let adSkipRemaining: Int?
}

struct WalletTransaction: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let userId: Int64?
    let type: String?
    let amount: Int
    let balanceAfter: Int
    let title: String
    let remark: String?
    let refType: String?
    let createdAt: String?
}

struct WalletTransactionsPage: Codable, Hashable, Sendable {
    let list: [WalletTransaction]
    let total: Int
    let page: Int
    let pageSize: Int
}

struct RechargePackageItem: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let name: String
    let coins: Int
    let bonusCoins: Int
    let priceYuan: Double
}

struct PayProductItem: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let productId: String
    let name: String
    let enabled: Bool
    let sort: Int?
}

struct RechargePackagesEnvelope: Codable, Hashable, Sendable {
    let list: [RechargePackageItem]
    let payOptions: [PayProductItem]
    let lubzfEnabled: Bool
    let simulateAllowed: Bool

    enum CodingKeys: String, CodingKey {
        case list
        case payOptions
        case lubzfEnabled
        case simulateAllowed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        list = try c.decodeIfPresent([RechargePackageItem].self, forKey: .list) ?? []
        payOptions = try c.decodeIfPresent([PayProductItem].self, forKey: .payOptions) ?? []
        lubzfEnabled = try c.decodeIfPresent(Bool.self, forKey: .lubzfEnabled) ?? false
        simulateAllowed = try c.decodeIfPresent(Bool.self, forKey: .simulateAllowed) ?? false
    }
}

struct RechargeOrderItem: Codable, Hashable, Sendable {
    let id: Int64
    let packageId: Int64
    let coins: Int
    let status: String
    let mchOrderNo: String?
    let payOrderId: String?
}

struct CreateRechargeOrderResponse: Codable, Hashable, Sendable {
    let order: RechargeOrderItem?
    let coins: Int?
    let payUrl: String?
    let mchOrderNo: String?
}

// MARK: - Ad skip
struct AdSkipConfig: Codable, Hashable, Identifiable, Sendable {
    let id: Int64
    let name: String
    let packageType: String?
    let durationHours: Int
    let skipCount: Int
    let priceCoins: Int
    var typeLower: String { (packageType ?? "time").lowercased() }
}

struct AdSkipStatus: Codable, Hashable, Sendable {
    let configs: [AdSkipConfig]?
    let timeConfigs: [AdSkipConfig]?
    let boosterConfigs: [AdSkipConfig]?
    let adSkipActive: Bool
    let adSkipExpiresAt: String?
    let adSkipRemaining: Int
    let coins: Int
}

// MARK: - Auth
struct AuthData: Decodable, Sendable {
    let token: String
    let user: AuthUser?
}

struct AuthUser: Decodable, Sendable {
    let id: Int64
    let nickname: String?
}

// MARK: - Interactions
struct EpisodeInteraction: Decodable, Sendable {
    let liked: Bool
    let collected: Bool
}

struct LikeEpisodeResult: Decodable, Sendable {
    let liked: Bool
}

// MARK: - Comments（对齐 backend `models.Comment` + 列表页）
struct CommentItem: Codable, Identifiable, Sendable {
    let id: Int64
    let userId: Int64?
    let parentId: Int64?
    let replyToUserId: Int64?
    let nickname: String?
    let avatar: String?
    let replyToNickname: String?
    let content: String
    let likesCount: Int
    let liked: Bool?
    var isLiked: Bool { liked ?? false }
    let createdAt: String?
    let timeAgo: String?
    var replies: [CommentItem]?
    let replyCount: Int?
    let hasMoreReplies: Bool?

    var displayName: String { nickname ?? "用户" }
    var displayTime: String {
        if let t = timeAgo, !t.isEmpty { return t }
        return createdAt ?? ""
    }
}

struct CommentListPayload: Codable, Sendable {
    let list: [CommentItem]
    let hasMore: Bool
    let page: Int
    let pageSize: Int
}

struct CommentLikeResult: Decodable, Sendable {
    let liked: Bool
    let likesCount: Int

    enum CodingKeys: String, CodingKey {
        case liked
        case likesCount = "likes_count"
    }
}

// MARK: - 播放前广告（GET ad/video）
struct AdVideoPayload: Decodable, Sendable {
    let skipAd: Bool?
    let duration: Int?
    let mediaType: String?
    let videoUrl: String?
    let imageUrl: String?

    /// 与全局 `JSONDecoder.keyDecodingStrategy = .convertFromSnakeCase` 配合：JSON 的 `skip_ad` 会先规范为 `skipAd` 再匹配。
    /// 若再写 `= "skip_ad"` 会与策略冲突，解码整包失败，`getAdVideoPayload` 恒为 nil（Android Gson 无此问题）。
    enum CodingKeys: String, CodingKey {
        case skipAd, duration, mediaType, videoUrl, imageUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        skipAd = try c.decodeIfPresent(Bool.self, forKey: .skipAd)
        if let d = try? c.decodeIfPresent(Int.self, forKey: .duration) {
            duration = d
        } else if let du = try? c.decodeIfPresent(Double.self, forKey: .duration) {
            duration = Int(du)
        } else {
            duration = nil
        }
        mediaType = try c.decodeIfPresent(String.self, forKey: .mediaType)
        videoUrl = try c.decodeIfPresent(String.self, forKey: .videoUrl)
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl)
    }
}
