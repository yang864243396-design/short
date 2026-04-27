import Combine
import SwiftUI

/// 对齐 Android `MainActivity` 三 Tab：首页、刷剧、我的
struct MainTabView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var tab: MainTab = .home
    @State var feedScrollAfterDrama: Int64 = 0
    @State private var showLogin = false

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
    }
}

enum MainTab: Hashable {
    case home, feed, profile
}
