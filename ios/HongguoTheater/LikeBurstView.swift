import SwiftUI

struct LikeBurst: Identifiable, Equatable {
    let id = UUID()
    let point: CGPoint
}

struct FloatingLikeBurstView: View {
    let burst: LikeBurst
    let onFinished: () -> Void

    @State private var appeared = false
    @State private var exiting = false

    var body: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 82, weight: .heavy))
            .foregroundStyle(Color.red)
            .shadow(color: .black.opacity(0.28), radius: 10, x: 0, y: 6)
            .scaleEffect(appeared ? (exiting ? 1.34 : 1.15) : 0.55)
            .opacity(appeared ? (exiting ? 0 : 1) : 0)
            .rotationEffect(.degrees(rotation))
            .offset(y: exiting ? -120 : 0)
            .position(burst.point)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear {
                withAnimation(.easeOut(duration: 0.14)) {
                    appeared = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                    withAnimation(.easeOut(duration: 0.52)) {
                        exiting = true
                    }
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.72) {
                    onFinished()
                }
            }
    }

    private var rotation: Double {
        Double(abs(burst.id.hashValue % 24) - 12)
    }
}
