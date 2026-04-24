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

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if session.isLoggedIn {
                        adSkipRow
                    }
                }
                .padding(.top)
                Picker("", selection: $tab) {
                    Text("历史").tag(0)
                    Text("收藏").tag(1)
                    Text("点赞").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .onChange(of: tab) { _, _ in Task { await loadTab() } }
                .onAppear { Task { await loadTab() } }

                if session.isLoggedIn {
                    if tab == 0 {
                        VStack(alignment: .leading, spacing: 10) { listHistory }
                    } else if tab == 1 {
                        VStack(alignment: .leading, spacing: 10) { listCollection }
                    } else {
                        VStack(alignment: .leading, spacing: 10) { listLikes }
                    }
                } else {
                    Text("登录后查看")
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding()
                }
            }
            .background(AppTheme.background)
            .navigationTitle("我的")
            .task { await refreshHeader() }
            .onChange(of: session.isLoggedIn) { _, on in
                if on { Task { await refreshHeader() } }
            }
            .sheet(isPresented: $showLogin) { LoginView().environmentObject(session) }
            .sheet(isPresented: $showAdSkipPicker) { adSkipSheet }
            .alert("提示", isPresented: Binding(
                get: { adSkipError != nil },
                set: { if !$0 { adSkipError = nil } }
            )) {
                Button("确定", role: .cancel) { adSkipError = nil }
            } message: {
                Text(adSkipError ?? "")
            }
            .navigationDestination(for: PlayerEntry.self) { e in
                PlayerView(dramaId: e.dramaId, episodeId: e.episodeId)
            }
            .navigationDestination(for: WalletDest.self) { _ in
                WalletView()
            }
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            if session.isLoggedIn {
                HStack(spacing: 16) {
                    avatarView
                    VStack(alignment: .leading, spacing: 4) {
                        Text(user?.displayName ?? "用户")
                            .font(.title3.weight(.semibold))
                        Text("\(user?.coins ?? 0) 金币")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                    Spacer()
                    Button("退出") { session.logout() }
                        .font(.subheadline)
                }
                .padding(.horizontal)
                Button {
                    path.append(WalletDest())
                } label: {
                    HStack {
                        Text("我的钱包")
                        Spacer()
                        Text("›")
                    }
                    .padding()
                    .background(AppTheme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .padding(.horizontal)
            } else {
                Button { showLogin = true } label: {
                    HStack {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.largeTitle)
                        Text("点击登录")
                            .font(.title3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                }
            }
        }
    }

    @ViewBuilder
    private var avatarView: some View {
        if #available(iOS 16.0, *) {
            PhotosPicker(selection: $pickerItem, matching: .images) {
                avatarImage
            }
            .onChange(of: pickerItem) { _, n in
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
            if let a = adSkip {
                if a.adSkipActive {
                    Text("免广权益：剩余 \(a.adSkipRemaining) 次 · 到期见服务端")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                } else {
                    Text("当前无免广告权益")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                }
            }
            Button {
                Task {
                    await loadAdSkipForPicker()
                    showAdSkipPicker = true
                }
            } label: {
                Text("购买免广告 / 加油包")
                    .frame(maxWidth: .infinity)
                    .padding(10)
                    .background(AppTheme.primary.opacity(0.2))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding(.horizontal)
    }

    private var adSkipSheet: some View {
        NavigationStack {
            List {
                ForEach(resolvePurchaseConfigs(), id: \.id) { c in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(c.name)
                            Text("\(c.priceCoins) 金币")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button("购买") { Task { await buyAdSkip(c) } }
                            .tint(AppTheme.primary)
                    }
                }
            }
            .navigationTitle("选择套餐")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { showAdSkipPicker = false }
                }
            }
        }
    }

    private func resolvePurchaseConfigs() -> [AdSkipConfig] {
        guard let a = adSkip else { return adSkipConfigs }
        if a.adSkipActive, let b = a.boosterConfigs, !b.isEmpty { return b }
        if let t = a.timeConfigs, !t.isEmpty { return t }
        return a.configs ?? adSkipConfigs
    }

    private func loadAdSkipForPicker() async {
        guard session.isLoggedIn else { return }
        do {
            adSkip = try await APIClient.shared.getAdSkipStatus(token: session.token)
            adSkipConfigs = adSkip?.configs ?? []
        } catch { }
    }

    private func buyAdSkip(_ c: AdSkipConfig) async {
        do {
            try await APIClient.shared.purchaseAdSkip(configId: c.id, token: session.token)
            showAdSkipPicker = false
            await refreshHeader()
        } catch {
            adSkipError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    private var listHistory: some View {
        ForEach(history, id: \.id) { h in
            if let d = h.drama {
                Button { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) } label: {
                    HStack {
                        if let u = ImageURL.resolve(d.coverUrl) {
                            AsyncImage(url: u) { p in
                                p.resizable().scaledToFill()
                            } placeholder: { Color(white: 0.2) }
                            .frame(width: 48, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                        }
                        VStack(alignment: .leading) {
                            Text(d.title ?? "")
                            Text(h.isFinished ? "已看完" : "看到第 \(h.lastEpisode) 集")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private var listCollection: some View {
        ForEach(collections) { d in
            Button { path.append(PlayerEntry(dramaId: d.id, episodeId: nil)) } label: {
                HStack {
                    if let u = ImageURL.resolve(d.coverUrl) {
                        AsyncImage(url: u) { p in
                            p.resizable().scaledToFill()
                        } placeholder: { Color(white: 0.2) }
                        .frame(width: 48, height: 64)
                    }
                    Text(d.title ?? "")
                }
            }
        }
    }

    private var listLikes: some View {
        ForEach(likes) { e in
            Button { path.append(PlayerEntry(dramaId: e.dramaId, episodeId: e.id)) } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(e.drama?.title ?? "短剧")
                        Text("第 \(e.episodeNumber) 集")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func refreshHeader() async {
        if session.isLoggedIn {
            do {
                user = try await APIClient.shared.getProfile(token: session.token)
                adSkip = try? await APIClient.shared.getAdSkipStatus(token: session.token)
            } catch { user = nil }
        } else { user = nil }
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
}

private struct WalletDest: Hashable {}
