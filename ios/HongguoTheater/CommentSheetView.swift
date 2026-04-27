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
    @State private var hgDialog: HGDialog?
    @State private var expandedRootIds: Set<Int64> = []
    @State private var showLogin = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Text("\(Self.formatCommentCount(initialCommentCount)) 评论")
                    .font(.headline)
                    .foregroundStyle(AppTheme.onSurface)
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(AppTheme.onSurface)
                        .frame(width: 36, height: 36)
                }
            }
            .padding(16)
            .background(AppTheme.surface)

            ZStack {
                if loading, items.isEmpty {
                    ProgressView()
                } else if items.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "text.bubble")
                            .font(.system(size: 64))
                            .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.35))
                        Text("暂无评论")
                            .font(.headline)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                        Text("快来抢占第一条评论")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textHint)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                        ForEach(items) { c in
                            commentThread(c)
                        }
                        if hasMore {
                            Color.clear
                                .frame(height: 1)
                                .onAppear { Task { await loadPage(reset: false) } }
                        }
                    }
                        .padding(16)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            VStack(spacing: 0) {
                if let target = replyTarget {
                    HStack(spacing: 8) {
                        Text("回复 @\(target.name)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                        Spacer()
                        Button("取消") { clearReplyTarget() }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.primary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    .padding(.bottom, 4)
                }
                HStack(spacing: 8) {
                    TextField(replyTarget == nil ? "说点什么…" : "回复 @\(replyTarget?.name ?? "用户")", text: $input)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.onSurface)
                        .padding(.horizontal, 16)
                        .frame(height: 40)
                        .background(AppTheme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    Button("发送") {
                        guard session.isLoggedIn else {
                            showLogin = true
                            return
                        }
                        Task { await post() }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
                    .background(AppTheme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding(8)
            }
            .background(AppTheme.surfaceHigh)
        }
        .background(AppTheme.surface)
        .presentationDetents([.fraction(0.5)])
        .presentationDragIndicator(.hidden)
        .task { await loadPage(reset: true) }
        .onChange(of: actionError) { text in
            guard let text else { return }
            hgDialog = HGDialog(
                title: "提示",
                message: text,
                primaryTitle: "确定",
                informStyle: true,
                onPrimary: { actionError = nil }
            )
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
                .environmentObject(session)
        }
        .hgDialog($hgDialog)
    }

    private func commentThread(_ root: CommentItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            commentRow(root, root: root, isReply: false)
            let replies = root.replies ?? []
            if !expandedRootIds.contains(root.id), hasReplies(root) {
                Button {
                    expandRoot(root)
                } label: {
                    HStack(spacing: 6) {
                        Rectangle()
                            .frame(width: 24, height: 1)
                            .foregroundStyle(AppTheme.outline.opacity(0.5))
                        Text("展开 \(replyExpandCount(root)) 条回复")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                }
                .buttonStyle(.plain)
                .padding(.leading, 52)
            } else if expandedRootIds.contains(root.id), !replies.isEmpty {
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
                .padding(.leading, 52)
            }
        }
        .padding(.vertical, 10)
    }

    private func commentRow(_ comment: CommentItem, root: CommentItem, isReply: Bool) -> some View {
        HStack(alignment: .top, spacing: 10) {
            commentAvatar(comment)
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(commentName(comment, isReply: isReply))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurface)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button {
                        guard session.isLoggedIn else {
                            showLogin = true
                            return
                        }
                        Task { await like(comment) }
                    } label: {
                        HStack(spacing: 2) {
                            Image(systemName: comment.isLiked ? "heart.fill" : "heart")
                                .font(.caption)
                                .foregroundStyle(comment.isLiked ? AppTheme.primary : AppTheme.textHint)
                            Text("\(comment.likesCount)")
                                .foregroundStyle(comment.isLiked ? AppTheme.primary : AppTheme.textHint)
                        }
                        .font(.caption)
                    }
                    .buttonStyle(.plain)
                }
                Text(comment.content)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurface)
                    .lineLimit(isReply ? 4 : nil)
                HStack(spacing: 14) {
                    Text(comment.displayTime)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                    Button("回复") {
                        replyTarget = CommentReplyTarget(root: root, target: comment)
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                }
            }
        }
        .padding(.leading, isReply ? 10 : 0)
    }

    private func commentAvatar(_ comment: CommentItem) -> some View {
        ZStack {
            if let url = ImageURL.resolve(comment.avatar) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        Image(systemName: "person.crop.circle.fill")
                            .resizable()
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                }
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .resizable()
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
        .frame(width: isSmallAvatar(comment) ? 28 : 36, height: isSmallAvatar(comment) ? 28 : 36)
        .clipShape(Circle())
    }

    private func isSmallAvatar(_ comment: CommentItem) -> Bool {
        (comment.parentId ?? 0) > 0
    }

    private func commentName(_ comment: CommentItem, isReply: Bool) -> String {
        if isReply, let to = comment.replyToNickname, !to.isEmpty {
            return "\(comment.displayName) 回复 \(to)"
        }
        return comment.displayName
    }

    private func hasReplies(_ root: CommentItem) -> Bool {
        (root.replyCount ?? 0) > 0 || !(root.replies ?? []).isEmpty || root.hasMoreReplies == true
    }

    private func replyExpandCount(_ root: CommentItem) -> Int {
        max(root.replyCount ?? 0, (root.replies ?? []).count, root.hasMoreReplies == true ? 1 : 0)
    }

    private func expandRoot(_ root: CommentItem) {
        expandedRootIds.insert(root.id)
        if (root.replies ?? []).isEmpty {
            Task { await loadReplies(root: root, reset: true) }
        }
    }

    private func clearReplyTarget() {
        replyTarget = nil
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