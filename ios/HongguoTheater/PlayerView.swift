import AVKit
import SwiftUI

struct PlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var session: SessionStore
    @StateObject private var vm: PlayerViewModel
    @State private var showEpisodes = false
    @State private var showComments = false
    @State private var likeBursts: [LikeBurst] = []
    @State private var episodeGroupIndex = 0
    @State private var descriptionExpanded = false
    @State private var showWallet = false
    @State private var showLogin = false

    init(dramaId: Int64, episodeId: Int64?) {
        _vm = StateObject(wrappedValue: PlayerViewModel(dramaId: dramaId, startEpisodeId: episodeId))
    }

    private var needsUnlock: Bool {
        vm.needsUnlockGate()
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.black.ignoresSafeArea()
                Group {
                    if vm.showAd, let adp = vm.adPlayer {
                        VideoPlayer(player: adp)
                            .ignoresSafeArea()
                    } else if vm.showAd, let u = vm.adImageURL {
                        ZStack {
                            AsyncImage(url: u) { phase in
                                switch phase {
                                case .empty:
                                    ProgressView().tint(.white)
                                case .success(let img):
                                    img
                                        .resizable()
                                        .scaledToFill()
                                case .failure:
                                    Color(white: 0.12)
                                @unknown default:
                                    Color(white: 0.12)
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                            .clipped()
                        }
                        .ignoresSafeArea()
                    } else if let p = vm.player {
                        VideoPlayer(player: p)
                            .ignoresSafeArea()
                            .disabled(needsUnlock)
                    } else if vm.busy {
                        ProgressView()
                            .tint(.white)
                    }
                }

                if needsUnlock, !vm.showAd {
                    Color.black.opacity(0.72).ignoresSafeArea()
                    VStack(spacing: 14) {
                        Text("解锁观看")
                            .font(.headline)
                            .foregroundStyle(.white)
                        if let c = vm.current?.unlockCoins {
                            Text("本集为付费内容。支付 \(c) 金币可永久解锁并免广告观看；或观看广告获得 10 分钟内观看权限。")
                                .font(.subheadline)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                        }
                        Button("支付 \(vm.current?.unlockCoins ?? 0) 金币") {
                            Task { await vm.unlock() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppTheme.primary)
                        Button("观看广告") {
                            vm.watchAdForTemporaryUnlock()
                        }
                        .buttonStyle(.bordered)
                        .tint(AppTheme.onSurface)
                    }
                    .padding()
                    .frame(maxWidth: 320)
                    .background(AppTheme.surface.opacity(0.96))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .padding()
                }

                if vm.showAd {
                    VStack {
                        HStack {
                            Spacer()
                            VStack(alignment: .trailing, spacing: 8) {
                                Text(vm.adCanClose ? "广告" : "广告 \(vm.adCountdown)s")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Capsule().fill(Color.black.opacity(0.45)))
                                Button(vm.adCanClose ? "关闭广告" : "跳过") {
                                    vm.requestCloseAd()
                                }
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(Capsule().fill(AppTheme.primary.opacity(0.85)))
                            }
                        }
                        .padding(16)
                        Spacer()
                    }
                }

                if !vm.showAd {
                VStack {
                    HStack {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "chevron.backward")
                                .font(.title2.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(10)
                                .background(Circle().fill(Color.black.opacity(0.35)))
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 8)
                    .padding(.top, 8)

                    Spacer()

                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(vm.drama?.title ?? vm.current?.drama?.title ?? "")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .shadow(radius: 4)
                            Text(vm.current.map { "第 \($0.episodeNumber) 集" } ?? "")
                                .font(.subheadline)
                                .foregroundStyle(Color.white.opacity(0.9))
                            descriptionBlock
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        VStack(spacing: 14) {
                            sideIcon("heart.fill", label: formatCount(vm.likeCount), on: vm.liked) {
                                guard session.isLoggedIn else {
                                    showLogin = true
                                    return
                                }
                                Task {
                                    let liked = await vm.toggleLike()
                                    if liked {
                                        addLikeBurst(at: CGPoint(x: proxy.size.width - 48, y: proxy.size.height * 0.58))
                                    }
                                }
                            }
                            sideIcon("star.fill", label: "收藏", on: vm.collected) {
                                guard session.isLoggedIn else {
                                    showLogin = true
                                    return
                                }
                                Task { await vm.toggleCollect() }
                            }
                            if vm.current != nil {
                                sideIcon("text.bubble.fill", label: formatCount(vm.commentCount), on: false) {
                                    if !session.isLoggedIn {
                                        showLogin = true
                                        return
                                    }
                                    showComments = true
                                }
                            }
                            ShareLink(item: shareText) {
                                VStack(spacing: 4) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.title2)
                                        .foregroundStyle(.white)
                                        .padding(10)
                                        .background(Circle().fill(Color.black.opacity(0.35)))
                                    Text("分享")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(.white)
                                }
                            }
                            sideIcon("list.bullet", label: "选集", on: false) {
                                showEpisodes = true
                            }
                        }
                    }
                    .padding()
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom))
                }
                }

                ForEach(likeBursts) { burst in
                    FloatingLikeBurstView(burst: burst) {
                        likeBursts.removeAll { $0.id == burst.id }
                    }
                }
            }
            .contentShape(Rectangle())
            .simultaneousGesture(
                SpatialTapGesture(count: 2).onEnded { value in
                    Task { await likeFromDoubleTap(at: value.location) }
                }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 60)
                    .onEnded { value in
                        guard abs(value.translation.height) > abs(value.translation.width) else { return }
                        if value.translation.height < -80 {
                            vm.selectRelativeEpisode(offset: 1)
                        } else if value.translation.height > 80 {
                            vm.selectRelativeEpisode(offset: -1)
                        }
                    }
            )
        }
        .navigationBarHidden(true)
        .task {
            vm.authToken = session.isLoggedIn ? session.token : nil
            await vm.load()
        }
        .onChange(of: session.isLoggedIn) { on in
            vm.authToken = on ? session.token : nil
            if on {
                Task { await vm.load() }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .hgAdSkipPurchased)) { _ in
            if vm.showAd {
                Task { await vm.finishAdAndPlayMain() }
            }
        }
        .alert("提示", isPresented: Binding(
            get: { vm.loadError != nil },
            set: { if !$0 { vm.loadError = nil } }
        )) {
            Button("确定", role: .cancel) { vm.loadError = nil }
        } message: {
            Text(vm.loadError ?? "")
        }
        .alert("提示", isPresented: $vm.confirmAbandonAd) {
            Button("继续观看") { vm.continueAd() }
            Button("放弃解锁", role: .destructive) { vm.abandonAdUnlock() }
        } message: {
            Text("关闭广告将无法获得本集 10 分钟观看权限，确定要放弃吗？")
        }
        .alert("提示", isPresented: $vm.rechargePrompt) {
            Button("去充值") { showWallet = true }
            Button("取消", role: .cancel) {}
        } message: {
            Text("金币余额不足，是否前往充值？")
        }
        .sheet(isPresented: $showWallet) {
            NavigationStack {
                WalletView()
                    .environmentObject(session)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
                .environmentObject(session)
        }
        .sheet(isPresented: $showComments) {
            Group {
                if let ep = vm.current {
                    CommentSheetView(episodeId: ep.id, initialCommentCount: ep.commentCount ?? 0)
                        .environmentObject(session)
                } else {
                    VStack { Text("分集未就绪") }
                        .onAppear { showComments = false }
                }
            }
        }
        .sheet(isPresented: $showEpisodes) {
            NavigationStack {
                VStack(spacing: 12) {
                    episodeGroupTabs
                    ScrollView {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 6), spacing: 8) {
                            ForEach(currentEpisodeGroup) { ep in
                                Button {
                                    vm.selectEpisode(ep)
                                    showEpisodes = false
                                } label: {
                                    VStack(spacing: 4) {
                                        Text("\(ep.episodeNumber)")
                                            .font(.subheadline.weight(.semibold))
                                        if !(ep.isFree ?? true), !(ep.coinUnlocked ?? false) {
                                            Image(systemName: "lock.fill")
                                                .font(.caption2)
                                                .foregroundStyle(AppTheme.onSurfaceVariant)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 46)
                                    .foregroundStyle(ep.id == vm.current?.id ? AppTheme.primary : AppTheme.onSurface)
                                    .background(ep.id == vm.current?.id ? AppTheme.primary.opacity(0.16) : AppTheme.surfaceHigh)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .navigationTitle("选集")
                .background(AppTheme.background)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("关闭") { showEpisodes = false }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var descriptionBlock: some View {
        let text = vm.drama?.description ?? vm.current?.drama?.description ?? ""
        if !text.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text(text)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.86))
                    .lineLimit(descriptionExpanded ? 4 : 1)
                Button(descriptionExpanded ? "收起" : "展开") {
                    descriptionExpanded.toggle()
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.primaryLight)
            }
        }
    }

    private var shareText: String {
        "\(vm.drama?.title ?? vm.current?.drama?.title ?? "红果剧场") \(vm.current.map { "第 \($0.episodeNumber) 集" } ?? "")"
    }

    private var episodeGroups: [[Episode]] {
        stride(from: 0, to: vm.episodes.count, by: 40).map {
            Array(vm.episodes[$0 ..< min($0 + 40, vm.episodes.count)])
        }
    }

    private var currentEpisodeGroup: [Episode] {
        guard !episodeGroups.isEmpty else { return [] }
        let idx = min(max(0, episodeGroupIndex), episodeGroups.count - 1)
        return episodeGroups[idx]
    }

    private var episodeGroupTabs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(episodeGroups.enumerated()), id: \.offset) { idx, group in
                    Button {
                        episodeGroupIndex = idx
                    } label: {
                        let first = group.first?.episodeNumber ?? idx * 40 + 1
                        let last = group.last?.episodeNumber ?? first
                        Text("\(first)-\(last)")
                            .hgPill(selected: idx == episodeGroupIndex)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
        }
        .padding(.top, 8)
    }

    private func sideIcon(_ name: String, label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: name)
                    .font(.title2)
                    .foregroundStyle(on ? AppTheme.primary : .white)
                    .padding(10)
                    .background(Circle().fill(Color.black.opacity(0.35)))
                Text(label)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.white)
            }
        }
        .buttonStyle(.plain)
    }

    private func likeFromDoubleTap(at point: CGPoint) async {
        guard session.isLoggedIn, !vm.showAd, !needsUnlock, vm.current != nil else { return }
        if vm.liked {
            addLikeBurst(at: point)
            return
        }
        let liked = await vm.toggleLike()
        if liked {
            addLikeBurst(at: point)
        }
    }

    private func addLikeBurst(at point: CGPoint) {
        likeBursts.append(LikeBurst(point: point))
    }

    private func formatCount(_ count: Int64) -> String {
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(count)"
    }
}
