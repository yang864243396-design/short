import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var keyword = ""
    @State private var history: [String] = []
    @State private var hotRanks: [RankItem] = []
    @State private var results: [Drama] = []
    @State private var loading = false
    @State private var fullScreenPlayer: PlayerEntry?

    var body: some View {
        VStack(spacing: 0) {
            searchHeader
            if results.isEmpty {
                defaultPanel
            } else {
                resultsPanel
            }
        }
        .background(AppTheme.background)
        .task {
            await loadHistory()
            await loadHotRanks()
        }
        .overlay {
            if loading { ProgressView() }
        }
        .navigationBarBackButtonHidden(true)
        .navigationBarHidden(true)
        .fullScreenCover(item: $fullScreenPlayer) { entry in
            NavigationStack {
                PlayerView(dramaId: entry.dramaId, episodeId: entry.episodeId)
                    .environmentObject(session)
            }
        }
    }

    private var searchHeader: some View {
        HStack(spacing: 8) {
            Button {
                handleNavigateUp()
            } label: {
                Image(systemName: "chevron.backward")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)
                    .frame(width: 40, height: 40)
            }
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AppTheme.textHint)
                TextField("搜索短剧…", text: $keyword)
                    .foregroundStyle(AppTheme.onSurface)
                    .submitLabel(.search)
                    .onSubmit { Task { await runSearch() } }
                    .onChange(of: keyword) { value in
                        if value.isEmpty { results = [] }
                    }
            }
            .padding(.horizontal, 14)
            .frame(height: 40)
            .background(AppTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private var defaultPanel: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if !history.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("搜索历史")
                                .font(.headline)
                                .foregroundStyle(AppTheme.onSurface)
                            Spacer()
                            if session.isLoggedIn {
                                Button {
                                    Task { await clearHistory() }
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundStyle(AppTheme.onSurfaceVariant)
                                }
                            }
                        }
                        FlowLayout(spacing: 8, rowSpacing: 8) {
                            ForEach(history, id: \.self) { h in
                                Button {
                                    keyword = h
                                    Task { await runSearch() }
                                } label: {
                                    Text(h)
                                        .hgPill()
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("今日热播榜")
                        .font(.headline)
                        .foregroundStyle(AppTheme.onSurface)
                    ForEach(Array(hotRanks.prefix(10))) { item in
                        if let d = item.drama {
                            Button {
                                fullScreenPlayer = PlayerEntry(dramaId: d.id, episodeId: nil)
                            } label: {
                                HStack(spacing: 10) {
                                    Text("\(item.rank)")
                                        .font(.headline)
                                        .foregroundStyle(AppTheme.primary)
                                        .frame(width: 28)
                                    dramaRow(d)
                                }
                                .padding(8)
                                .hgCard(radius: 10, fill: AppTheme.surface)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    private var resultsPanel: some View {
        List {
            ForEach(results) { d in
                Button {
                    fullScreenPlayer = PlayerEntry(dramaId: d.id, episodeId: nil)
                } label: {
                    dramaRow(d)
                }
                .buttonStyle(.plain)
                .listRowBackground(AppTheme.background)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func handleNavigateUp() {
        if !results.isEmpty {
            results = []
            keyword = ""
        } else {
            dismiss()
        }
    }

    private struct FlowLayout: Layout {
        var spacing: CGFloat
        var rowSpacing: CGFloat

        func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
            let maxWidth = proposal.width ?? 0
            var x: CGFloat = 0
            var y: CGFloat = 0
            var rowHeight: CGFloat = 0

            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                if x > 0, x + size.width > maxWidth {
                    x = 0
                    y += rowHeight + rowSpacing
                    rowHeight = 0
                }
                x += size.width + spacing
                rowHeight = max(rowHeight, size.height)
            }
            return CGSize(width: maxWidth, height: y + rowHeight)
        }

        func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
            var x = bounds.minX
            var y = bounds.minY
            var rowHeight: CGFloat = 0

            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                if x > bounds.minX, x + size.width > bounds.maxX {
                    x = bounds.minX
                    y += rowHeight + rowSpacing
                    rowHeight = 0
                }
                subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
                rowHeight = max(rowHeight, size.height)
            }
        }
    }

    private func dramaRow(_ d: Drama) -> some View {
        HStack {
            HGDramaCover(url: ImageURL.resolve(d.coverUrl), width: 48, height: 64, radius: 4)
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
