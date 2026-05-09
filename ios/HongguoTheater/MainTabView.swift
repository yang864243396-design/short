import Combine
import SwiftUI

/// 对齐 Android `MainActivity` 三 Tab：首页、刷剧、我的
struct MainTabView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var tab: MainTab = .home
    @State var feedScrollAfterDrama: Int64 = 0
    @State private var showLogin = false
    @State private var appUpdateGate: AppUpdateGateState?

    var body: some View {
        TabView(selection: $tab) {
            HomeView { dramaId in
                feedScrollAfterDrama = dramaId
                tab = .feed
            }
            .tabItem { Label("首页", systemImage: "house.fill") }
            .tag(MainTab.home)

            FeedView(scrollAfterDramaId: $feedScrollAfterDrama, parentTab: $tab)
                .tabItem { Label("播放", systemImage: "play.rectangle.fill") }
                .tag(MainTab.feed)

            ProfileView()
                .tabItem { Label("个人中心", systemImage: "person.fill") }
                .tag(MainTab.profile)
        }
        .onReceive(NotificationCenter.default.publisher(for: .hgAuthRequired)) { _ in
            session.logout()
            showLogin = true
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
                .environmentObject(session)
        }
        .fullScreenCover(item: $appUpdateGate) { gate in
            AppUpdateGateView(gate: gate) { appUpdateGate = nil }
        }
        .task {
            await coldStartReleaseCheck()
        }
    }

    private func coldStartReleaseCheck() async {
        let local = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        guard let payload = await APIClient.shared.fetchReleaseCheckOptional(platform: "ios") else { return }
        guard AppVersionUpdate.shouldPrompt(local: local, remote: payload.version) else { return }
        let raw = (payload.installUrl ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty, let url = URL(string: raw) else { return }
        let notes = (payload.releaseNotes ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        await MainActor.run {
            appUpdateGate = AppUpdateGateState(
                version: payload.version,
                notes: notes,
                force: payload.forceUpdate,
                installURL: url
            )
        }
    }
}

enum MainTab: Hashable {
    case home, feed, profile
}
