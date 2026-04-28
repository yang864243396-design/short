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
    @State private var hgDialog: HGDialog?
    @State private var rankingSheet: PlayerRankingSheetEntry?
    @State private var controlsVisible = true
    @State private var dragProgress: Double?
    private let onRequestNextDramaFromFeed: (() -> Void)?

    init(
        dramaId: Int64,
        episodeId: Int64?,
        handoffStreamURL: URL? = nil,
        handoffPositionSeconds: Double = 0,
        onRequestNextDramaFromFeed: (() -> Void)? = nil
    ) {
        self.onRequestNextDramaFromFeed = onRequestNextDramaFromFeed
        _vm = StateObject(wrappedValue: PlayerViewModel(
            dramaId: dramaId,
            startEpisodeId: episodeId,
            handoffStreamURL: handoffStreamURL,
            handoffPositionSeconds: handoffPositionSeconds
        ))
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
                        .zIndex(20)
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
                    .zIndex(21)
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

                if !vm.showAd, !needsUnlock, controlsVisible {
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
                            if let badge = (vm.drama ?? vm.current?.drama)?.preferredRankingBadge {
                                RankingBadgeView(info: badge) {
                                    rankingSheet = PlayerRankingSheetEntry(type: badge.type)
                                }
                            }
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
                            if vm.current != nil {
                                sideIcon("text.bubble.fill", label: formatCount(vm.commentCount), on: false) {
                                    if !session.isLoggedIn {
                                        showLogin = true
                                        return
                                    }
                                    showComments = true
                                }
                            }
                            sideIcon("star.fill", label: "收藏", on: vm.collected) {
                                guard session.isLoggedIn else {
                                    showLogin = true
                                    return
                                }
                                Task { await vm.toggleCollect() }
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
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, playerDockTopAlignPadding)
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom))
                }
                }

                if !vm.showAd, !needsUnlock {
                    VStack {
                        Spacer()
                        playerDock
                    }
                    .ignoresSafeArea(edges: .bottom)
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
            .onTapGesture {
                vm.togglePlayPause()
                controlsVisible = true
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 60)
                    .onEnded { value in
                        guard abs(value.translation.height) > abs(value.translation.width) else { return }
                        if value.translation.height < -80 {
                            if !vm.selectRelativeEpisode(offset: 1), vm.isOnLastEpisode {
                                if let onRequestNextDramaFromFeed {
                                    onRequestNextDramaFromFeed()
                                } else {
                                    hgDialog = HGDialog(
                                        title: "提示",
                                        message: "已经是最后一集了",
                                        primaryTitle: "确定",
                                        informStyle: true
                                    )
                                }
                            }
                        } else if value.translation.height > 80 {
                            if !vm.selectRelativeEpisode(offset: -1), vm.isOnFirstEpisode {
                                hgDialog = HGDialog(
                                    title: "提示",
                                    message: "已经是第一集了",
                                    primaryTitle: "确定",
                                    informStyle: true
                                )
                            }
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
        .onChange(of: vm.loadError) { error in
            guard let error else { return }
            hgDialog = HGDialog(
                title: "提示",
                message: error,
                primaryTitle: "确定",
                informStyle: true,
                onPrimary: { vm.loadError = nil }
            )
        }
        .onChange(of: vm.confirmAbandonAd) { show in
            guard show else { return }
            hgDialog = HGDialog(
                title: "提示",
                message: "关闭广告将无法获得本集 10 分钟观看权限，确定要放弃吗？",
                primaryTitle: "继续观看",
                secondaryTitle: "放弃解锁",
                onPrimary: { vm.continueAd() },
                onSecondary: { vm.abandonAdUnlock() }
            )
        }
        .onChange(of: vm.rechargePrompt) { show in
            guard show else { return }
            hgDialog = HGDialog(
                title: "提示",
                message: "金币余额不足，是否前往充值？",
                primaryTitle: "去充值",
                secondaryTitle: "取消",
                onPrimary: {
                    vm.rechargePrompt = false
                    showWallet = true
                },
                onSecondary: { vm.rechargePrompt = false }
            )
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
            VStack(spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(vm.drama?.title ?? vm.current?.drama?.title ?? "")
                            .font(.headline)
                            .foregroundStyle(AppTheme.onSurface)
                            .lineLimit(1)
                        Text("共 \(vm.episodes.count) 集")
                            .font(.caption)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                    Spacer()
                    Button {
                        showEpisodes = false
                    } label: {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(AppTheme.onSurface)
                            .frame(width: 36, height: 36)
                    }
                }
                .padding(16)

                episodeGroupTabs
                    .padding(.bottom, 8)

                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 6), spacing: 8) {
                        ForEach(Array(currentEpisodeGroup.enumerated()), id: \.element.id) { localIndex, ep in
                            let globalIndex = episodeGroupIndex * 40 + localIndex
                            Button {
                                vm.selectEpisode(ep)
                                showEpisodes = false
                            } label: {
                                ZStack(alignment: .topTrailing) {
                                    Text("\(ep.episodeNumber)")
                                        .font(.subheadline.weight(.semibold))
                                        .frame(maxWidth: .infinity, minHeight: 44)
                                        .foregroundStyle(ep.id == vm.current?.id ? AppTheme.primary : AppTheme.onSurface)
                                        .background(ep.id == vm.current?.id ? AppTheme.primary.opacity(0.16) : AppTheme.surfaceHigh)
                                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    if !(ep.isFree ?? true), !(ep.coinUnlocked ?? false) {
                                        Image(systemName: "lock.fill")
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.onSurfaceVariant)
                                            .padding(5)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .id(globalIndex)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .frame(height: 400)
            .background(AppTheme.surface)
            .presentationDetents([.height(400)])
            .presentationDragIndicator(.hidden)
        }
        .sheet(item: $rankingSheet) { entry in
            NavigationStack {
                RankingView(initialType: entry.type)
                    .environmentObject(session)
            }
        }
        .hgDialog($hgDialog)
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

    private var playerDock: some View {
        VStack(spacing: 0) {
            seekBar
                .frame(height: 12)
                .padding(.horizontal, 16)
            Button {
                showEpisodes = true
            } label: {
                Text("选集 \(vm.current.map { "第 \($0.episodeNumber) 集" } ?? "")")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .background(Color(red: 0.145, green: 0.145, blue: 0.145))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.bottom, 6)
        }
        .background(Color.black)
    }

    private var seekBar: some View {
        GeometryReader { geo in
            let progress = dragProgress ?? vm.playbackProgress
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.white.opacity(0.22))
                    .frame(height: 2)
                Capsule()
                    .fill(AppTheme.primaryLight)
                    .frame(width: max(0, min(1, progress)) * geo.size.width, height: 2)
                Circle()
                    .fill(AppTheme.primaryLight)
                    .frame(width: 10, height: 10)
                    .offset(x: max(0, min(1, progress)) * geo.size.width - 5)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        dragProgress = min(1, max(0, value.location.x / max(geo.size.width, 1)))
                    }
                    .onEnded { value in
                        let p = min(1, max(0, value.location.x / max(geo.size.width, 1)))
                        dragProgress = nil
                        Task { await vm.seek(to: p) }
                    }
            )
        }
    }

    /// 与 `playerDock` 自下而上的可视栈高度一致（进度条 12 + 选集 40 + 底部 6），使简介与右侧功能区底缘与进度条顶缘对齐。
    private var playerDockTopAlignPadding: CGFloat { 12 + 40 + 6 }

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

private struct PlayerRankingSheetEntry: Identifiable {
    let type: String
    var id: String { type }
}
