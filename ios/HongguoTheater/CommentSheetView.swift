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
    @State private var replyTarget: CommentReplyTarget?
    @State private var replyPages: [Int64: Int] = [:]
    @State private var loadingReplyRoots: Set<Int64> = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
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
                            commentThread(c)
                                .listRowBackground(AppTheme.background)
                        }
                        if hasMore {
                            Color.clear
                                .onAppear { Task { await loadPage(reset: false) } }
                        }
                    }
                    .listStyle(.plain)
                }
                if session.isLoggedIn {
                    if let target = replyTarget {
                        HStack(spacing: 8) {
                            Text("回复 @\(target.name)")
                                .font(.caption)
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                            Spacer()
                            Button("取消") { replyTarget = nil }
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(AppTheme.primary)
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                        .background(AppTheme.surfaceHigh)
                    }
                    HStack {
                        TextField(replyTarget == nil ? "说点什么…" : "回复 @\(replyTarget?.name ?? "用户")", text: $input, axis: .vertical)
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

    private func commentThread(_ root: CommentItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            commentRow(root, root: root, isReply: false)
            let replies = root.replies ?? []
            if !replies.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(replies) { reply in
                        commentRow(reply, root: root, isReply: true)
                    }
                    if root.hasMoreReplies == true {
                        Button {
                            Task { await loadReplies(root: root, reset: false) }
                        } label: {
                            HStack(spacing: 6) {
                                Rectangle()
                                    .frame(width: 24, height: 1)
                                    .foregroundStyle(AppTheme.outline)
                                Text(loadingReplyRoots.contains(root.id) ? "加载中…" : "展开更多回复")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(AppTheme.onSurfaceVariant)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.leading, 18)
            } else if (root.replyCount ?? 0) > 0 {
                Button {
                    Task { await loadReplies(root: root, reset: true) }
                } label: {
                    HStack(spacing: 6) {
                        Rectangle()
                            .frame(width: 24, height: 1)
                            .foregroundStyle(AppTheme.outline)
                        Text("展开 \(root.replyCount ?? 0) 条回复")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                }
                .buttonStyle(.plain)
                .padding(.leading, 18)
            }
        }
        .padding(.vertical, 6)
    }

    private func commentRow(_ comment: CommentItem, root: CommentItem, isReply: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(comment.displayName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)
                if isReply, let name = comment.replyToNickname, !name.isEmpty {
                    Text("回复 @\(name)")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                }
                Spacer()
                if session.isLoggedIn {
                    Button {
                        Task { await like(comment) }
                    } label: {
                        HStack(spacing: 2) {
                            Image(systemName: comment.isLiked ? "heart.fill" : "heart")
                                .foregroundStyle(comment.isLiked ? Color.red : AppTheme.onSurfaceVariant)
                            Text("\(comment.likesCount)")
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                        }
                        .font(.caption)
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(comment.content)
                .font(.subheadline)
                .foregroundStyle(AppTheme.onSurface)
            HStack(spacing: 14) {
                Text(comment.displayTime)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                if session.isLoggedIn {
                    Button("回复") {
                        replyTarget = CommentReplyTarget(root: root, target: comment)
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.primary)
                }
            }
        }
        .padding(.leading, isReply ? 10 : 0)
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
                parentId: replyTarget?.root.id,
                replyToCommentId: replyTarget?.target.id,
                token: session.token
            )
            input = ""
            replyTarget = nil
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

    private func loadReplies(root: CommentItem, reset: Bool) async {
        guard !loadingReplyRoots.contains(root.id) else { return }
        loadingReplyRoots.insert(root.id)
        defer { loadingReplyRoots.remove(root.id) }
        let nextPage = reset ? 1 : (replyPages[root.id] ?? ((root.replies ?? []).isEmpty ? 1 : 2))
        do {
            let tok = session.isLoggedIn ? session.token : nil
            let p = try await APIClient.shared.getCommentReplies(
                episodeId: episodeId,
                rootId: root.id,
                page: nextPage,
                pageSize: 5,
                token: tok
            )
            guard let idx = items.firstIndex(where: { $0.id == root.id }) else { return }
            var updated = items[idx]
            let existing = reset ? [] : (updated.replies ?? [])
            updated.replies = existing + p.list
            replyPages[root.id] = nextPage + 1
            items[idx] = updated
        } catch {
            actionError = "回复加载失败：\(error.localizedDescription)"
        }
    }

    private static func formatCommentCount(_ count: Int64) -> String {
        if count >= 10_000 {
            return String(format: "%.1fw", Double(count) / 10_000.0)
        }
        return "\(max(0, count))"
    }
}

private struct CommentReplyTarget {
    let root: CommentItem
    let target: CommentItem

    var name: String { target.displayName }
}