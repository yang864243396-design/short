import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var keyword = ""
    @State private var history: [String] = []
    @State private var hotRanks: [RankItem] = []
    @State private var results: [Drama] = []
    @State private var loading = false
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if !history.isEmpty {
                    Section {
                        ForEach(history, id: \.self) { h in
                            Button(h) {
                                keyword = h
                                Task { await runSearch() }
                            }
                        }
                    } header: {
                        HStack {
                            Text("搜索历史")
                            Spacer()
                            if session.isLoggedIn {
                                Button("清空", role: .destructive) { Task { await clearHistory() } }
                            }
                        }
                    }
                }
                if results.isEmpty, !hotRanks.isEmpty {
                    Section("热播榜") {
                        ForEach(Array(hotRanks.prefix(10))) { item in
                            if let d = item.drama {
                                Button {
                                    path.append(PlayerEntry(dramaId: d.id, episodeId: nil))
                                } label: {
                                    HStack(spacing: 10) {
                                        Text("\(item.rank)")
                                            .font(.headline)
                                            .foregroundStyle(AppTheme.primary)
                                            .frame(width: 28)
                                        dramaRow(d)
                                    }
                                }
                            }
                        }
                    }
                }
                if !results.isEmpty {
                    Section("结果") {
                        ForEach(results) { d in
                            Button {
                                path.append(PlayerEntry(dramaId: d.id, episodeId: nil))
                            } label: {
                                dramaRow(d)
                            }
                        }
                    }
                }
            }
            .searchable(text: $keyword, prompt: "搜索短剧…")
            .onSubmit(of: .search) { Task { await runSearch() } }
            .scrollContentBackground(.hidden)
            .background(AppTheme.background)
            .task {
                await loadHistory()
                await loadHotRanks()
            }
            .overlay {
                if loading { ProgressView() }
            }
            .navigationTitle("搜索")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: PlayerEntry.self) { e in
                PlayerView(dramaId: e.dramaId, episodeId: e.episodeId)
            }
        }
    }

    private func dramaRow(_ d: Drama) -> some View {
        HStack {
            if let u = ImageURL.resolve(d.coverUrl) {
                AsyncImage(url: u) { p in
                    p.resizable().scaledToFill()
                } placeholder: { Color(white: 0.2) }
                .frame(width: 48, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(d.title ?? "")
                    .foregroundStyle(AppTheme.onSurface)
                Text(d.statusText)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
    }

    private func loadHistory() async {
        guard session.isLoggedIn else { return }
        do {
            history = try await APIClient.shared.getSearchHistory(token: session.token)
        } catch { history = [] }
    }

    private func clearHistory() async {
        do {
            try await APIClient.shared.clearSearchHistory(token: session.token)
            history = []
        } catch {}
    }

    private func loadHotRanks() async {
        do {
            hotRanks = try await APIClient.shared.getRankings(
                type: "hot",
                token: session.isLoggedIn ? session.token : nil
            )
        } catch { hotRanks = [] }
    }

    private func runSearch() async {
        let k = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !k.isEmpty else { return }
        loading = true
        defer { loading = false }
        do {
            let list = try await APIClient.shared.search(
                keyword: k,
                token: session.isLoggedIn ? session.token : nil
            )
            self.results = list
        } catch { self.results = [] }
    }
}
