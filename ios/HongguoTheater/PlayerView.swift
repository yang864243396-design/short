import AVKit
import SwiftUI

struct PlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var session: SessionStore
    @StateObject private var vm: PlayerViewModel
    @State private var showEpisodes = false
    @State private var showComments = false
    @State private var likeBursts: [LikeBurst] = []

    init(dramaId: Int64, episodeId: Int64?) {
        _vm = StateObject(wrappedValue: PlayerViewModel(dramaId: dramaId, startEpisodeId: episodeId))
    }

    private var needsUnlock: Bool {
        guard let ep = vm.current else { return false }
        let free = ep.isFree ?? true
        let unlocked = ep.coinUnlocked ?? false
        return !free && !unlocked
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
                        ZStack(alignment: .topTrailing) {
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
                            Text("广告 · \(vm.adCountdown) 秒")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(Color.black.opacity(0.45)))
                                .padding(16)
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

                if needsUnlock {
                    Color.black.opacity(0.72).ignoresSafeArea()
                    VStack(spacing: 16) {
                        Text("本集需金币解锁")
                            .font(.headline)
                            .foregroundStyle(.white)
                        if let c = vm.current?.unlockCoins {
                            Text("需要 \(c) 金币")
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                        }
                        Button("使用金币解锁") {
                            Task { await vm.unlock() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppTheme.primary)
                    }
                    .padding()
                }

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
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        VStack(spacing: 14) {
                            if session.isLoggedIn {
                                sideIcon("heart.fill", on: vm.liked) {
                                    Task {
                                        let liked = await vm.toggleLike()
                                        if liked {
                                            addLikeBurst(at: CGPoint(x: proxy.size.width - 48, y: proxy.size.height * 0.58))
                                        }
                                    }
                                }
                                sideIcon("star.fill", on: vm.collected) {
                                    Task { await vm.toggleCollect() }
                                }
                            }
                            if vm.current != nil {
                                sideIcon("text.bubble.fill", on: false) {
                                    showComments = true
                                }
                            }
                            sideIcon("list.bullet", on: false) {
                                showEpisodes = true
                            }
                        }
                    }
                    .padding()
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom))
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
        .alert("提示", isPresented: Binding(
            get: { vm.loadError != nil },
            set: { if !$0 { vm.loadError = nil } }
        )) {
            Button("确定", role: .cancel) { vm.loadError = nil }
        } message: {
            Text(vm.loadError ?? "")
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
                List(vm.episodes) { ep in
                    Button {
                        vm.selectEpisode(ep)
                        showEpisodes = false
                    } label: {
                        HStack {
                            Text("第 \(ep.episodeNumber) 集")
                            Spacer()
                            if !(ep.isFree ?? true), !(ep.coinUnlocked ?? false) {
                                Text("锁")
                                    .foregroundStyle(AppTheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                .navigationTitle("选集")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("关闭") { showEpisodes = false }
                    }
                }
            }
        }
    }

    private func sideIcon(_ name: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.title2)
                .foregroundStyle(on ? AppTheme.primary : .white)
                .padding(10)
                .background(Circle().fill(Color.black.opacity(0.35)))
        }
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
}
