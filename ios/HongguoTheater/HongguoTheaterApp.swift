import AVFoundation
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
                    try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [.defaultToSpeaker])
                    try? AVAudioSession.sharedInstance().setActive(true)
                    await VideoCacheManager.shared.runEvictIfNeeded()
                    await AdPreloadManager.shared.warmupIfNeeded(isLoggedIn: session.isLoggedIn)
                }
        }
    }
}
