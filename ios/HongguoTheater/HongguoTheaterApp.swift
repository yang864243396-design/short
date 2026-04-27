import SwiftUI

@main
struct HongguoTheaterApp: App {
    @StateObject private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .environmentObject(session)
                .tint(AppTheme.primary)
                .preferredColorScheme(.dark)
                .task {
                    await AdPreloadManager.shared.warmupIfNeeded(isLoggedIn: session.isLoggedIn)
                }
        }
    }
}
