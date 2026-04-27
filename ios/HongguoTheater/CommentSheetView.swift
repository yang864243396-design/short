import SwiftUI

struct CommentSheetView: View {
    let episodeId: Int64
    let initialCommentCount: Int64
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    @State private var items: [CommentItem] = []
    @State private var hasMore = true
    @State private var page = 1
    @State private var input = ""
    @State private var loading = false
    @State private var actionError: String?

    var body: some View {
        NavigationStack {
            VStack {
                if loading, items.isEmpty {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else if items.isEmpty {
                    Spacer()
                    Text("暂无评论")
                        .foregroundStyle(.secondary)
                    Spacer()
                } else {
                    List {
                        ForEach(items) { c in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(c.displayName)
                                        .font(.subheadline.weight(.semibold))
                                    Spacer()
                                    if session.isLoggedIn {
                                        Button {
                                            Task { await like(c) }
                                        } label: {
                                            HStack(spacing: 2) {
                                                Image(systemName: c.isLiked ? "heart.fill" : "heart")
                                                    .foregroundStyle(c.isLiked ? Color.red : Color.secondary)
                                                Text("\(c.likesCount)")
                                            }
                                        }
                                    }
                                }
                                Text(c.content)
                                    .font(.subheadline)
                                Text(c.displayTime)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                        if hasMore {
                            Color.clear
                                .onAppear { Task { await loadPage(reset: false) } }
                        }
                    }
                    .listStyle(.plain)
                }
                if session.isLoggedIn {
                    HStack {
                        TextField("说点什么…", text: $input, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                        Button("发送") { Task { await post() } }
                            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                    .padding()
                } else {
                    Text("登录后发表评论")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 8)
                }
            }
            .navigationTitle("\(Self.formatCommentCount(initialCommentCount)) 评论")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .task { await loadPage(reset: true) }
        .alert("提示", isPresented: Binding(
            get: { actionError != nil },
            set: { if !$0 { actionError = nil } }
        )) {
            Button("确定", role: .cancel) { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
    }

    private func loadPage(reset: Bool) async {
        if reset {
            page = 1
            hasMore = true
            items = []
        }
        guard hasMore, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let tok = session.isLoggedIn ? session.token : nil
            let p = try await APIClient.shared.getComments(
                episodeId: episodeId,
                page: page,
                pageSize: 15,
                token: tok
            )
            if p.list.isEmpty, page == 1 { hasMore = false; return }
            if page == 1 { items = p.list } else { items.append(contentsOf: p.list) }
            hasMore = p.hasMore
            page += 1
        } catch {
            if reset {
                actionError = "评论加载失败：\(error.localizedDescription)"
                hasMore = false
            } else {
                actionError = "加载更多失败：\(error.localizedDescription)"
            }
        }
    }

    private func post() async {
        let t = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, session.isLoggedIn else { return }
        do {
            try await APIClient.shared.postComment(
                episodeId: episodeId,
                content: t,
                parentId: nil,
                token: session.token
            )
            input = ""
            await loadPage(reset: true)
        } catch {
            actionError = "发送失败：\(error.localizedDescription)"
        }
    }

    private func like(_ c: CommentItem) async {
        guard session.isLoggedIn else { return }
        do {
            _ = try await APIClient.shared.likeComment(commentId: c.id, token: session.token)
            await loadPage(reset: true)
        } catch {
            actionError = "操作失败：\(error.localizedDescription)"
        }
    }

    private static func formatCommentCount(_ count: Int64) -> String {
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(max(0, count))"
    }
}