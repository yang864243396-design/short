import SwiftUI

struct RankingView: View {
    @EnvironmentObject private var session: SessionStore
    @State var initialType: String = "hot"
    @State private var type: String = "hot"
    @State private var items: [RankItem] = []
    @State private var loading = false
    @State private var path = NavigationPath()

    private let types: [(String, String)] = [
        ("hot", "热播榜"),
        ("rising", "飙升榜"),
        ("rating", "好评榜")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Picker("榜单", selection: $type) {
                ForEach(types, id: \.0) { p in
                    Text(p.1).tag(p.0)
                }
            }
            .pickerStyle(.segmented)
            .padding()
            .onChange(of: type) { _, n in
                Task { await load(n) }
            }
            if loading, items.isEmpty {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                List {
                    ForEach(items) { it in
                        if let d = it.drama {
                            Button {
                                path.append(PlayerEntry(dramaId: d.id, episodeId: nil))
                            } label: {
                                HStack {
                                    Text("\(it.rank)")
                                        .font(.headline)
                                        .foregroundStyle(AppTheme.primary)
                                        .frame(width: 32)
                                    if let u = ImageURL.resolve(d.coverUrl) {
                                        AsyncImage(url: u) { p in
                                            p.resizable().scaledToFill()
                                        } placeholder: { Color(white: 0.2) }
                                        .frame(width: 48, height: 64)
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                    }
                                    VStack(alignment: .leading) {
                                        Text(d.title ?? "")
                                        Text("热度 \(it.heat)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .background(AppTheme.background)
        .navigationTitle("排行榜")
        .task {
            self.type = initialType
            await load(type)
        }
        .navigationDestination(for: PlayerEntry.self) { e in
            PlayerView(dramaId: e.dramaId, episodeId: e.episodeId)
        }
    }

    private func load(_ t: String) async {
        loading = true
        defer { loading = false }
        do {
            let tok = session.isLoggedIn ? session.token : nil
            items = try await APIClient.shared.getRankings(type: t, token: tok)
        } catch { items = [] }
    }
}
