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
    /// 对齐 Android `PlayerActivity`：整页（视频区 + 底部选集条）跟手竖滑 + 松手吸附与切集动画。
    @State private var episodeSlideOffset: CGFloat = 0
    @State private var episodeSlideAnimating = false
    @State private var episodeSlideVerticalLocked: Bool?
    @State private var episodeSlideBaseOffset: CGFloat = 0
    @State private var episodeSlideLastTransY: CGFloat = 0
    @State private var episodeSlideLastTime: CFTimeInterval?
    @State private var episodeSlideEndVelocityY: CGFloat = 0
    private let onRequestNextDramaFromFeed: (() -> Void)?

    private static let episodeSnapDistanceFraction: CGFloat = 0.20
    private static let episodeSnapVelocityPoints: CGFloat = 380

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
            handoffPositionSeconds: handoffPositionSeconds,
            onRequestNextDramaFromFeed: onRequestNextDramaFromFeed
        ))
    }

    private var needsUnlock: Bool {
        vm.needsUnlockGate()
    }

    /// 对齐 Android：`loadAndPlayAd` 开始后应关掉二选一浮层；`streamPreparing` 已为 true 时仍显示蒙层会把加载态/贴片挡死，看起来像「按钮无反应」。
    private var unlockGateOverlayVisible: Bool {
        needsUnlock && !vm.showAd && !vm.streamPreparing
    }

    var body: some View {
        GeometryReader { proxy in
            let pageH = proxy.size.height
            ZStack {
                Color.black.ignoresSafeArea()
                ZStack {
                    Group {
                        if vm.showAd, let adp = vm.adPlayer {
                            InlineVideoSurface(player: adp)
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
                            InlineVideoSurface(player: p)
                                .ignoresSafeArea()
                        } else if vm.streamPreparing {
                            ProgressView("加载中…")
                                .tint(.white)
                                .foregroundStyle(.white)
                        } else if vm.busy {
                            ProgressView()
                                .tint(.white)
                        }
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
                .offset(y: episodeSlideOffset)
                .contentShape(Rectangle())
                .simultaneousGesture(episodeSwipeGesture(pageHeight: pageH))
                .simultaneousGesture(
                    SpatialTapGesture(count: 2).onEnded { value in
                        guard !needsUnlock else { return }
                        Task { await likeFromDoubleTap(at: value.location) }
                    }
                )
                .onTapGesture {
                    guard !needsUnlock else { return }
                    if vm.showAd {
                        if let ap = vm.adPlayer {
                            if ap.rate > 0 { ap.pause() } else { ap.play() }
                        }
                    } else {
                        vm.togglePlayPause()
                    }
                    controlsVisible = true
                }

                /// 与 Android `HgDialog.showConfirm` + `dialog_hg_sheet` 一致：主按钮在上（支付金币）、次按钮在下（观看广告），深底圆角面板。
                if unlockGateOverlayVisible {
                    Color.black.opacity(0.55)
                        .ignoresSafeArea()
                        .allowsHitTesting(true)
                        .zIndex(50)
                    PlayerCoinOrAdUnlockPanel(
                        unlockCoins: vm.current?.unlockCoins ?? 0,
                        onPayCoins: {
                            Task { await vm.unlock() }
                        },
                        onWatchAd: {
                            vm.watchAdForTemporaryUnlock()
                        }
                    )
                    .zIndex(51)
                }
            }
        }
        .navigationBarHidden(true)
        .task {
            vm.authToken = session.isLoggedIn ? session.token : nil
            await vm.load()
        }
        .onChange(of: session.isLoggedIn) { on in
            vm.authToken = on ? session.token : nil
            Task { await vm.load() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .hgAdSkipPurchased)) { _ in
            Task { @MainActor in
                if vm.showAd {
                    await vm.finishAdAndPlayMain()
                } else {
                    await vm.refreshAdSkipAndRetryPipeline()
                }
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

    private func episodeSwipeGesture(pageHeight: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 16)
            .onChanged { value in
                guard !episodeSlideAnimating else { return }
                guard !needsUnlock, !vm.showAd else { return }
                let t = value.translation
                if episodeSlideVerticalLocked == nil {
                    guard hypot(t.width, t.height) >= 14 else { return }
                    if abs(t.width) > abs(t.height) {
                        episodeSlideVerticalLocked = false
                        return
                    }
                    episodeSlideVerticalLocked = true
                    episodeSlideBaseOffset = episodeSlideOffset
                    episodeSlideLastTransY = t.height
                    episodeSlideLastTime = CFAbsoluteTimeGetCurrent()
                    episodeSlideEndVelocityY = 0
                }
                guard episodeSlideVerticalLocked == true else { return }
                let raw = episodeSlideBaseOffset + value.translation.height
                episodeSlideOffset = clampEpisodeSlideOffset(raw, height: pageHeight)
                let now = CFAbsoluteTimeGetCurrent()
                if let prevT = episodeSlideLastTime {
                    let dy = value.translation.height - episodeSlideLastTransY
                    let dt = max(now - prevT, 1e-5)
                    episodeSlideEndVelocityY = dy / CGFloat(dt)
                }
                episodeSlideLastTransY = value.translation.height
                episodeSlideLastTime = now
            }
            .onEnded { _ in
                let locked = episodeSlideVerticalLocked
                episodeSlideVerticalLocked = nil
                episodeSlideLastTransY = 0
                episodeSlideLastTime = nil
                guard locked == true else { return }
                guard !episodeSlideAnimating else { return }
                settleEpisodeVerticalSwipe(pageHeight: pageHeight)
            }
    }

    private func clampEpisodeSlideOffset(_ raw: CGFloat, height: CGFloat) -> CGFloat {
        let maxO = height * 1.15
        var y = min(maxO, max(-maxO, raw))
        if vm.isOnFirstEpisode, y > 0 {
            y = rubberBandOverscroll(y, range: height * 0.4)
        }
        return y
    }

    private func rubberBandOverscroll(_ overscroll: CGFloat, range: CGFloat) -> CGFloat {
        guard overscroll > 0, range > 1 else { return overscroll }
        return range * (1 - CGFloat(exp(-Double(overscroll / range))))
    }

    private func settleEpisodeVerticalSwipe(pageHeight: CGFloat) {
        let h = pageHeight
        let ty = episodeSlideOffset
        let threshold = h * Self.episodeSnapDistanceFraction
        let vy = episodeSlideEndVelocityY
        let wantNext = ty < -threshold || vy < -Self.episodeSnapVelocityPoints
        let wantPrev = ty > threshold || vy > Self.episodeSnapVelocityPoints
        if wantNext, wantPrev {
            springBackEpisodeSlide(height: h)
            return
        }
        if wantNext {
            handleSwipeWantNext(height: h)
            return
        }
        if wantPrev {
            handleSwipeWantPrev(height: h)
            return
        }
        springBackEpisodeSlide(height: h)
    }

    private func handleSwipeWantNext(height: CGFloat) {
        if vm.isOnLastEpisode {
            if let cb = onRequestNextDramaFromFeed {
                swipeExitThenReset(height: height, exitDy: -height) {
                    cb()
                }
            } else {
                hgDialog = HGDialog(
                    title: "提示",
                    message: "已经是最后一集了",
                    primaryTitle: "确定",
                    informStyle: true
                )
                springBackEpisodeSlide(height: height)
            }
            return
        }
        swipeAnimatedChangeEpisode(swipeUp: true, height: height)
    }

    private func handleSwipeWantPrev(height: CGFloat) {
        if vm.isOnFirstEpisode {
            hgDialog = HGDialog(
                title: "提示",
                message: "已经是第一集了",
                primaryTitle: "确定",
                informStyle: true
            )
            springBackEpisodeSlide(height: height)
            return
        }
        swipeAnimatedChangeEpisode(swipeUp: false, height: height)
    }

    private func swipeAnimatedChangeEpisode(swipeUp: Bool, height: CGFloat) {
        episodeSlideAnimating = true
        let exit: CGFloat = swipeUp ? -height : height
        let durOut = settleDuration(from: episodeSlideOffset, to: exit, height: height)
        withAnimation(Self.episodeSwitchCurve(duration: durOut)) {
            episodeSlideOffset = exit
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + durOut) {
            if swipeUp {
                _ = vm.selectRelativeEpisode(offset: 1)
                episodeSlideOffset = height
            } else {
                _ = vm.selectRelativeEpisode(offset: -1)
                episodeSlideOffset = -height
            }
            let durIn = settleDuration(from: episodeSlideOffset, to: 0, height: height)
            withAnimation(Self.episodeSwitchCurve(duration: durIn)) {
                episodeSlideOffset = 0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + durIn) {
                episodeSlideAnimating = false
            }
        }
    }

    private func swipeExitThenReset(height: CGFloat, exitDy: CGFloat, completion: @escaping () -> Void) {
        episodeSlideAnimating = true
        let durOut = settleDuration(from: episodeSlideOffset, to: exitDy, height: height)
        withAnimation(Self.episodeSwitchCurve(duration: durOut)) {
            episodeSlideOffset = exitDy
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + durOut) {
            var t = Transaction()
            t.disablesAnimations = true
            withTransaction(t) {
                episodeSlideOffset = 0
            }
            episodeSlideAnimating = false
            completion()
        }
    }

    private func springBackEpisodeSlide(height: CGFloat) {
        let ty = episodeSlideOffset
        guard abs(ty) > 1.5 else {
            episodeSlideOffset = 0
            return
        }
        episodeSlideAnimating = true
        let dur = settleDuration(from: ty, to: 0, height: height)
        withAnimation(Self.episodeSwitchCurve(duration: dur)) {
            episodeSlideOffset = 0
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + dur) {
            episodeSlideAnimating = false
        }
    }

    private func settleDuration(from: CGFloat, to: CGFloat, height: CGFloat) -> Double {
        let span = abs(to - from) / max(height, 1)
        return Double(min(0.42, max(0.16, 0.16 + span * 0.32)))
    }

    private static func episodeSwitchCurve(duration: Double) -> Animation {
        .timingCurve(0.4, 0, 0.2, 1, duration: duration)
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

// MARK: - 解锁二选一（对齐 Android `dialog_hg_sheet` + HgDialog.showConfirm 主上/次下）

private struct PlayerCoinOrAdUnlockPanel: View {
    let unlockCoins: Int
    let onPayCoins: () -> Void
    let onWatchAd: () -> Void

    private var message: String {
        "本集为付费内容。支付 \(unlockCoins) 金币可永久解锁并免广告观看；或观看广告获得 10 分钟内观看权限。"
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("解锁观看")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AppTheme.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Text(message)
                .font(.system(size: 15))
                .foregroundStyle(Color(red: 0.631, green: 0.631, blue: 0.631))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .frame(maxWidth: .infinity)
                .padding(.top, 14)
            Button {
                onPayCoins()
            } label: {
                Text("支付 \(unlockCoins) 金币")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color(red: 0.102, green: 0.102, blue: 0.102))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .padding(.horizontal, 16)
                    .background(Color(red: 1, green: 0.549, blue: 0.451))
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 24)
            Button {
                onWatchAd()
            } label: {
                Text("观看广告")
                    .font(.system(size: 15))
                    .foregroundStyle(Color(red: 0.922, green: 0.922, blue: 0.961))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .padding(.horizontal, 16)
                    .background(Color(red: 0.173, green: 0.173, blue: 0.18))
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.top, 28)
        .padding(.bottom, 24)
        .background(Color(red: 0.11, green: 0.11, blue: 0.118))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.horizontal, 24)
    }
}

private struct PlayerRankingSheetEntry: Identifiable {
    let type: String
    var id: String { type }
}
