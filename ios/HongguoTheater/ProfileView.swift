import PhotosUI
import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var tab = 0
    @State private var user: User?
    @State private var history: [WatchHistory] = []
    @State private var collections: [Drama] = []
    @State private var likes: [Episode] = []
    @State private var adSkip: AdSkipStatus?
    @State private var showLogin = false
    @State private var path = NavigationPath()
    @State private var pickerItem: PhotosPickerItem?
    @State private var showAdSkipPicker = false
    @State private var adSkipConfigs: [AdSkipConfig] = []
    @State private var adSkipError: String?
    @State private var adSkipConfirmConfig: AdSkipConfig?
    @State private var adSkipInsufficientMessage: String?
    @State private var hgDialog: HGDialog?
    @State private var selectedAdSkipConfigID: Int64?

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                header
                if session.isLoggedIn {
                    loggedInContent
                } else {
                    notLoggedInContent
                }
            }
            .background(AppTheme.background)
            .navigationBarHidden(true)
            .task { await refreshHeader() }
            .onChange(of: session.isLoggedIn) { on in
                if on { Task { await refreshHeader() } }
            }
            .sheet(isPresented: $showLogin) { LoginView().environmentObject(session) }
            .overlay {
                if showAdSkipPicker {
                    adSkipPickerOverlay
                }
            }
            .onChange(of: adSkipError) { text in
                guard let text else { return }
                hgDialog = HGDialog(
                    title: "提示",
                    message: text,
                    primaryTitle: "确定",
                    informStyle: true,
                    onPrimary: { adSkipError = nil }
                )
            }
            .onChange(of: adSkipInsufficientMessage) { text in
                guard let text else { return }
                hgDialog = HGDialog(
                    title: "解锁失败",
                    message: text,
                    primaryTitle: "去充值",
                    secondaryTitle: "取消",
                    onPrimary: {
                        adSkipInsufficientMessage = nil
                        path.append(WalletDest())
                    },
                    onSecondary: { adSkipInsufficientMessage = nil }
                )
            }
            .navigationDestination(for: PlayerEntry.self) { e in
                PlayerView(dramaId: e.dramaId, episodeId: e.episodeId)
            }
            .navigationDestination(for: WalletDest.self) { _ in
                WalletView()
            }
            .hgDialog($hgDialog)
        }
    }

    private var header: some View {
        HStack(spacing: 16) {
            avatarView
            VStack(alignment: .leading, spacing: 4) {
                Text(session.isLoggedIn ? (user?.displayName ?? "用户") : "未登录")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)
                if session.isLoggedIn {
                    Text("点击头像可更换")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                } else {
                    Text("点击登录享受更多权益")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                }
            }
            Spacer()
            if session.isLoggedIn {
                    Button("退出") { session.logout() }
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
        .padding(20)
        .contentShape(Rectangle())
        .onTapGesture {
            if !session.isLoggedIn { showLogin = true }
        }
    }

    private var loggedInContent: some View {
        VStack(spacing: 0) {
            walletRow
            adSkipRow
            tabBar
            listContent
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var notLoggedInContent: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "person.crop.circle.fill")
                .font(.system(size: 80))
                .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.3))
            Text("登录后查看个人数据")
                .font(.subheadline)
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Button("登录") { showLogin = true }
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 160, height: 40)
                .background(AppTheme.primary)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var walletRow: some View {
        Button {
            path.append(WalletDest())
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color(red: 0.239, green: 0.18, blue: 0.165))
                    Image(systemName: "creditcard.fill")
                        .font(.title3)
                        .foregroundStyle(Color(red: 1, green: 0.557, blue: 0.451))
                }
                .frame(width: 44, height: 44)
                Text("我的钱包")
                    .font(.headline)
                    .foregroundStyle(AppTheme.onSurface)
                Spacer()
                Text("\(user?.coins ?? 0) 金币")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Color(red: 1, green: 0.557, blue: 0.451))
                Text("›")
                    .font(.title2)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(minHeight: 56)
            .background(Color(red: 0.118, green: 0.118, blue: 0.133))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }

    @ViewBuilder
    private var avatarView: some View {
        if #available(iOS 16.0, *) {
            PhotosPicker(selection: $pickerItem, matching: .images) {
                avatarImage
            }
            .onChange(of: pickerItem) { n in
                Task { await uploadAvatar(n) }
            }
        } else { avatarImage }
    }

    private var avatarImage: some View {
        Group {
            if let u = ImageURL.resolve(user?.avatar) {
                AsyncImage(url: u) { p in
                    p.resizable().scaledToFill()
                } placeholder: { Color(white: 0.2) }
                .frame(width: 64, height: 64)
                .clipShape(Circle())
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .resizable()
                    .frame(width: 64, height: 64)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
    }

    private var adSkipRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("免广告")
                .font(.headline)
                .foregroundStyle(AppTheme.onSurface)
            Text(adSkipStatusText)
                .font(.caption)
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Button {
                Task {
                    await loadAdSkipForPicker()
                    showAdSkipPicker = true
                }
            } label: {
                Text(adSkip?.adSkipActive == true ? "购买加油包" : "购买广告跳过卡")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(AppTheme.primary.opacity(0.45), lineWidth: 1)
                    )
            }
            .disabled(resolvePurchaseConfigs().isEmpty)
            .opacity(resolvePurchaseConfigs().isEmpty ? 0.45 : 1)
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 16)
        .background(Color(red: 0.078, green: 0.078, blue: 0.094))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.15), lineWidth: 1)
        )
        .padding(.horizontal)
        .padding(.bottom, 10)
    }

    private var adSkipStatusText: String {
        guard let a = adSkip else { return "免广告信息暂时无法更新，请稍后下拉重试" }
        if a.adSkipActive, let raw = a.adSkipExpiresAt, !raw.isEmpty {
            let text = String(raw.replacingOccurrences(of: "T", with: " ").prefix(19))
            return "免广告至 \(text) · 剩余 \(a.adSkipRemaining) 次"
        }
        return "当前无免广告权益"
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            profileTab("历史", index: 0)
            profileTab("收藏", index: 1)
            profileTab("点赞", index: 2)
        }
        .frame(height: 48)
        .background(AppTheme.background)
        .onAppear { Task { await loadTab() } }
    }

    private func profileTab(_ title: String, index: Int) -> some View {
        Button {
            tab = index
            Task { await loadTab() }
        } label: {
            VStack(spacing: 8) {
                Text(title)
                    .font(.subheadline.weight(tab == index ? .bold : .regular))
                    .foregroundStyle(tab == index ? AppTheme.onSurface : AppTheme.onSurfaceVariant)
                Rectangle()
                    .fill(tab == index ? AppTheme.primary : Color.clear)
                    .frame(height: 3)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var listContent: some View {
        let empty = currentListIsEmpty
        if empty {
            Text("暂无数据")
                .font(.subheadline)
                .foregroundStyle(AppTheme.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    if tab == 0 {
                        listHistory
                    } else if tab == 1 {
                        listCollection
                    } else {
                        listLikes
                    }
                }
                .padding(16)
            }
        }
    }

    private var currentListIsEmpty: Bool {
        switch tab {
        case 0: return history.isEmpty
        case 1: return collections.isEmpty
        case 2: return likes.isEmpty
        default: return true
        }
    }

    private var adSkipPickerOverlay: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { showAdSkipPicker = false }
            VStack(spacing: 0) {
                ZStack {
                    Text(adSkip?.adSkipActive == true ? "选择加油包" : "解锁免广告特权")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 48)
                    HStack {
                        Spacer()
                        Button {
                            showAdSkipPicker = false
                        } label: {
                            Image(systemName: "xmark")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(AppTheme.textHint)
                                .frame(width: 40, height: 40)
                        }
                    }
                }
                .padding(.leading, 12)
                .padding(.top, 8)
                .padding(.trailing, 8)
                .padding(.bottom, 4)

                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 2), spacing: 10) {
                        ForEach(resolvePurchaseConfigs(), id: \.id) { config in
                            adSkipTierCell(config)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                }
                .frame(maxHeight: 300)

                HStack {
                    Text("当前余额: \(adSkip?.coins ?? user?.coins ?? 0) 金币")
                        .font(.caption)
                        .foregroundStyle(Color(red: 0.541, green: 0.541, blue: 0.541))
                    Spacer()
                    Button("去充值") {
                        showAdSkipPicker = false
                        path.append(WalletDest())
                    }
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color(red: 1, green: 0.341, blue: 0.133))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 4)

                Button {
                    guard let selected = selectedAdSkipConfig else { return }
                    showAdSkipPicker = false
                    showAdSkipPayConfirm(selected)
                } label: {
                    Text("立即解锁")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(AppTheme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(selectedAdSkipConfig == nil)
                .opacity(selectedAdSkipConfig == nil ? 0.45 : 1)
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
            .background(Color(red: 0.078, green: 0.078, blue: 0.094))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .frame(maxWidth: 360)
            .padding(.horizontal, 24)
        }
        .zIndex(900)
    }

    private func adSkipTierCell(_ config: AdSkipConfig) -> some View {
        let selected = selectedAdSkipConfigID == config.id
        return Button {
            selectedAdSkipConfigID = config.id
        } label: {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(adSkipTierTitle(config))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(selected ? Color(red: 1, green: 0.341, blue: 0.133) : .white)
                        .lineLimit(1)
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(config.priceCoins)")
                            .font(.title3.weight(.heavy))
                            .foregroundStyle(selected ? Color(red: 1, green: 0.341, blue: 0.133) : .white)
                        Text("金币")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(selected ? Color(red: 1, green: 0.341, blue: 0.133).opacity(0.12) : AppTheme.surfaceHigh)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? Color(red: 1, green: 0.341, blue: 0.133) : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }

    private var selectedAdSkipConfig: AdSkipConfig? {
        let configs = resolvePurchaseConfigs()
        guard !configs.isEmpty else { return nil }
        if let id = selectedAdSkipConfigID,
           let config = configs.first(where: { $0.id == id }) {
            return config
        }
        return configs.first
    }

    private func resolvePurchaseConfigs() -> [AdSkipConfig] {
        guard let a = adSkip else { return adSkipConfigs }
        if a.adSkipActive {
            if let b = a.boosterConfigs, !b.isEmpty { return b }
            let booster = (a.configs ?? adSkipConfigs).filter { $0.typeLower == "booster" }
            if !booster.isEmpty { return booster }
            return a.configs ?? adSkipConfigs
        }
        if let t = a.timeConfigs, !t.isEmpty { return t }
        return a.configs ?? adSkipConfigs
    }

    private func allAdSkipConfigs(_ status: AdSkipStatus) -> [AdSkipConfig] {
        var map: [Int64: AdSkipConfig] = [:]
        for c in status.configs ?? [] { map[c.id] = c }
        for c in status.timeConfigs ?? [] { map[c.id] = c }
        for c in status.boosterConfigs ?? [] { map[c.id] = c }
        return Array(map.values)
    }

    private func adSkipTierTitle(_ c: AdSkipConfig) -> String {
        if c.typeLower == "booster" {
            return "\(c.skipCount) 次免广告"
        }
        if c.durationHours >= 24, c.durationHours % 24 == 0 {
            return "\(c.durationHours / 24)天 · \(c.skipCount) 次"
        }
        return "\(c.durationHours)小时 · \(c.skipCount) 次"
    }

    private func loadAdSkipForPicker() async {
        guard session.isLoggedIn else { return }
        do {
            adSkip = try await APIClient.shared.getAdSkipStatus(token: session.token)
            if let adSkip {
                adSkipConfigs = allAdSkipConfigs(adSkip)
                selectedAdSkipConfigID = resolvePurchaseConfigs().first?.id
            }
        } catch { }
    }

    private func showAdSkipPayConfirm(_ c: AdSkipConfig) {
        adSkipConfirmConfig = c
        hgDialog = HGDialog(
            title: "确认支付",
            message: "购买「\(adSkipTierTitle(c))」\n需支付：\(c.priceCoins) 金币\n当前余额：\(adSkip?.coins ?? user?.coins ?? 0) 金币",
            primaryTitle: "确认支付",
            secondaryTitle: "取消",
            onPrimary: {
                adSkipConfirmConfig = nil
                Task { await validateConfigThenPurchase(c) }
            },
            onSecondary: { adSkipConfirmConfig = nil }
        )
    }

    private func validateConfigThenPurchase(_ selected: AdSkipConfig) async {
        do {
            let latest = try await APIClient.shared.getAdSkipStatus(token: session.token)
            adSkip = latest
            adSkipConfigs = allAdSkipConfigs(latest)
            guard let fresh = allAdSkipConfigs(latest).first(where: { $0.id == selected.id }) else {
                adSkipError = "所选套餐已下架或不存在，已为您同步最新套餐列表。"
                showAdSkipPicker = true
                return
            }
            if fresh.priceCoins != selected.priceCoins
                || fresh.durationHours != selected.durationHours
                || fresh.skipCount != selected.skipCount
            {
                adSkipError = "「\(adSkipTierTitle(fresh))」套餐信息已更新，请按最新价格 \(fresh.priceCoins) 金币重新选择购买。"
                showAdSkipPicker = true
                return
            }
            if latest.coins < fresh.priceCoins {
                adSkipInsufficientMessage = "金币余额不足，无法解锁。\n需要 \(fresh.priceCoins) 金币，当前 \(latest.coins) 金币。"
                return
            }
            await buyAdSkip(fresh)
        } catch {
            adSkipError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    private func buyAdSkip(_ c: AdSkipConfig) async {
        do {
            try await APIClient.shared.purchaseAdSkip(configId: c.id, token: session.token)
            showAdSkipPicker = false
            await refreshHeader()
            NotificationCenter.default.post(name: .hgAdSkipPurchased, object: nil)
        } catch {
            adSkipError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    private var listHistory: some View {
        ForEach(history, id: \.id) { h in
            if let d = h.drama {
                Button { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) } label: {
                    profileListRow(
                        title: d.title ?? "",
                        subtitle: h.isFinished ? "已看完" : String(format: "看到 %02d集", h.lastEpisode),
                        cover: d.coverUrl
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var listCollection: some View {
        ForEach(collections) { d in
            Button { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) } label: {
                profileListRow(
                    title: d.title ?? "",
                    subtitle: "\(d.category ?? "") · \(d.statusText)",
                    cover: d.coverUrl
                )
            }
            .buttonStyle(.plain)
        }
    }

    private var listLikes: some View {
        ForEach(likes) { e in
            Button { path.append(PlayerEntry(dramaId: e.dramaId, episodeId: e.id)) } label: {
                profileListRow(
                    title: e.drama?.title ?? e.title ?? "短剧",
                    subtitle: "第\(e.episodeNumber)集 · \(Self.formatCount(e.likeCount ?? 0))赞",
                    cover: e.drama?.coverUrl
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func profileListRow(title: String, subtitle: String, cover: String?) -> some View {
        HStack(spacing: 12) {
            HGDramaCover(url: ImageURL.resolve(cover), width: 56, height: 72, radius: 4)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.onSurface)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(12)
        .hgCard(radius: 10, fill: AppTheme.surface)
    }

    private func refreshHeader() async {
        if session.isLoggedIn {
            do {
                user = try await APIClient.shared.getProfile(token: session.token)
                adSkip = try? await APIClient.shared.getAdSkipStatus(token: session.token)
            } catch { user = nil }
        } else {
            user = nil
            adSkip = nil
            history = []
            collections = []
            likes = []
        }
        if session.isLoggedIn { await loadTab() }
    }

    private func loadTab() async {
        guard session.isLoggedIn else { return }
        do {
            switch tab {
            case 0: history = try await APIClient.shared.getHistory(token: session.token)
            case 1: collections = try await APIClient.shared.getCollections(token: session.token)
            case 2: likes = try await APIClient.shared.getLikedEpisodes(token: session.token)
            default: break
            }
        } catch { }
    }

    private func uploadAvatar(_ item: PhotosPickerItem?) async {
        guard let item, session.isLoggedIn else { return }
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        do {
            let u = try await APIClient.shared.uploadAvatar(
                imageData: data,
                filename: "avatar.jpg",
                mime: "image/jpeg",
                token: session.token
            )
            self.user = u
        } catch { }
    }

    private static func formatCount(_ count: Int64) -> String {
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(count)"
    }
}

private struct WalletDest: Hashable {}
