import SwiftUI
import UIKit

/// 与 Android `AppUpdateHelper` 对齐：营销版本 x.y.z 比较
enum AppVersionUpdate {
    private static let semverPattern = #"^\d+\.\d+\.\d+$"#

    static func shouldPrompt(local: String, remote: String) -> Bool {
        let r = remote.trimmingCharacters(in: .whitespacesAndNewlines)
        guard r.range(of: semverPattern, options: .regularExpression) != nil else { return false }
        let l = local.trimmingCharacters(in: .whitespacesAndNewlines)
        if l.range(of: semverPattern, options: .regularExpression) == nil {
            return true
        }
        return l != r
    }
}

struct AppUpdateGateState: Identifiable {
    let id = UUID()
    let version: String
    let notes: String
    let force: Bool
    let installURL: URL
}

struct AppUpdateGateView: View {
    let gate: AppUpdateGateState
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.82).ignoresSafeArea()
            VStack(spacing: 18) {
                Text("版本更新")
                    .font(.title2.weight(.semibold))
                Text("新版本 \(gate.version)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if !gate.notes.isEmpty {
                    ScrollView {
                        Text(gate.notes)
                            .font(.body)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 160)
                }
                Button {
                    UIApplication.shared.open(gate.installURL)
                } label: {
                    Text("立即更新")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                if !gate.force {
                    Button("关闭", role: .cancel) { onClose() }
                }
            }
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(uiColor: .secondarySystemBackground)))
            .padding(28)
        }
        .interactiveDismissDisabled(gate.force)
    }
}
