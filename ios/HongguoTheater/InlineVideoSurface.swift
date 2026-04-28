import AVFoundation
import SwiftUI
import UIKit

/// 使用 `AVPlayerLayer` 全屏铺满，不嵌入 `AVPlayerViewController`。
/// SwiftUI `VideoPlayer` 在叠加透明交互层、或 `allowsHitTesting(false)` 等情况下，系统层易显示「不可播放」占位，且与自定义 ResourceLoader 失败态叠加时更难排查。
struct InlineVideoSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerHostView {
        let v = PlayerLayerHostView()
        v.playerLayer.player = player
        v.playerLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PlayerLayerHostView, context: Context) {
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
        }
    }
}

final class PlayerLayerHostView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
