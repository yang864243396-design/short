package com.hongguo.theater.ui.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Comment;
import com.hongguo.theater.utils.ImageUrlUtils;
import com.hongguo.theater.utils.LoginHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnReplyClickListener {
        void onReplyClick(Comment root, Comment replyTo);
    }

    public interface OnLoadMoreRepliesListener {
        void onLoadMoreReplies(Comment root);
    }

    private static final int TYPE_ROOT = 0;
    private static final int TYPE_REPLY = 1;
    private static final int TYPE_LOAD_MORE_REPLIES = 2;
    private static final int TYPE_REPLIES_COLLAPSED = 3;

    /** 仅刷新点赞区域，避免整行重绑（Glide、长文本等） */
    private static final String PAYLOAD_LIKE = "like";

    private final Context context;
    private final OnReplyClickListener replyClickListener;
    private final OnLoadMoreRepliesListener loadMoreRepliesListener;
    private final List<Row> rows = new ArrayList<>();
    /** 已展开回复的主评 id；默认全部折叠 */
    private final Set<Long> expandedRootIds = new HashSet<>();
    private final HashSet<Long> likeInflight = new HashSet<>();

    private static final class Row {
        enum Kind { ROOT, REPLY, MORE_REPLIES, COLLAPSED_REPLIES }

        final Kind kind;
        final Comment root;
        final Comment comment;
        boolean loadingMoreReplies;

        static Row rootRow(Comment root) {
            return new Row(Kind.ROOT, root, root, false);
        }

        static Row replyRow(Comment root, Comment reply) {
            return new Row(Kind.REPLY, root, reply, false);
        }

        static Row moreRepliesRow(Comment root) {
            return new Row(Kind.MORE_REPLIES, root, root, false);
        }

        static Row collapsedRepliesRow(Comment root) {
            return new Row(Kind.COLLAPSED_REPLIES, root, root, false);
        }

        private Row(Kind kind, Comment root, Comment comment, boolean loadingMoreReplies) {
            this.kind = kind;
            this.root = root;
            this.comment = comment;
            this.loadingMoreReplies = loadingMoreReplies;
        }
    }

    public CommentAdapter(Context context, OnReplyClickListener replyClickListener,
                          OnLoadMoreRepliesListener loadMoreRepliesListener) {
        this.context = context;
        this.replyClickListener = replyClickListener;
        this.loadMoreRepliesListener = loadMoreRepliesListener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Row row = rows.get(position);
        switch (row.kind) {
            case ROOT:
            case REPLY:
                return row.comment.getId();
            case MORE_REPLIES:
                return -2L * row.root.getId() - 1L;
            case COLLAPSED_REPLIES:
                return -2L * row.root.getId() - 2L;
            default:
                return RecyclerView.NO_ID;
        }
    }

    /**
     * 根据主评 id 取内存中的主评对象；任意属于该楼的行（主评/回复/折叠/更多）均可定位。
     */
    @Nullable
    public Comment findRootComment(long rootId) {
        for (Row r : rows) {
            if (r.root.getId() == rootId) {
                return r.root;
            }
        }
        return null;
    }

    /**
     * 在被回复的评论行正下方插入一条回复（不整表刷新）。
     *
     * @param replyToCommentId 被点的评论 id（主评或子回复）；无效时退化为 {@link #insertReplyUnderRoot}
     */
    public int insertReplyBelowTarget(@NonNull Comment root, long replyToCommentId,
                                      @NonNull Comment newReply) {
        if (replyToCommentId <= 0) {
            return insertReplyUnderRoot(root, newReply);
        }
        ensureExpandedForRoot(root);
        long rootId = root.getId();
        int targetIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.root.getId() != rootId) {
                continue;
            }
            if (r.kind == Row.Kind.ROOT && r.comment.getId() == replyToCommentId) {
                targetIdx = i;
                break;
            }
            if (r.kind == Row.Kind.REPLY && r.comment.getId() == replyToCommentId) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx < 0) {
            return insertReplyUnderRoot(root, newReply);
        }
        int insertAt = targetIdx + 1;
        rows.add(insertAt, Row.replyRow(root, newReply));
        notifyItemInserted(insertAt);
        root.insertReplyLocalAfter(replyToCommentId, newReply);
        return insertAt;
    }

    public int insertReplyUnderRoot(@NonNull Comment root, @NonNull Comment newReply) {
        ensureExpandedForRoot(root);
        long rootId = root.getId();
        int rootIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind == Row.Kind.ROOT && rows.get(i).root.getId() == rootId) {
                rootIdx = i;
                break;
            }
        }
        if (rootIdx < 0) {
            return -1;
        }
        int insertAt = rootIdx + 1;
        for (int i = rootIdx + 1; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind == Row.Kind.ROOT) {
                break;
            }
            if (r.kind == Row.Kind.COLLAPSED_REPLIES) {
                break;
            }
            if (r.kind == Row.Kind.MORE_REPLIES && r.root.getId() == rootId) {
                insertAt = i;
                break;
            }
            if (r.kind == Row.Kind.REPLY && r.root.getId() == rootId) {
                insertAt = i + 1;
            }
        }
        rows.add(insertAt, Row.replyRow(root, newReply));
        notifyItemInserted(insertAt);
        root.appendRepliesLocal(Collections.singletonList(newReply));
        root.setReplyCount(Math.max(root.getReplyCount(), root.getReplies().size()));
        return insertAt;
    }

    private void ensureExpandedForRoot(Comment root) {
        int idx = findCollapsedRow(root.getId());
        if (idx >= 0) {
            expandRootAt(idx, root);
        }
    }

    private void expandRootAt(int collapsedIndex, Comment root) {
        expandedRootIds.add(root.getId());
        rows.remove(collapsedIndex);
        notifyItemRemoved(collapsedIndex);
        List<Comment> reps = new ArrayList<>(root.getReplies());
        int n = reps.size();
        for (int i = 0; i < n; i++) {
            rows.add(collapsedIndex + i, Row.replyRow(root, reps.get(i)));
        }
        if (n > 0) {
            notifyItemRangeInserted(collapsedIndex, n);
        }
        int after = collapsedIndex + n;
        if (root.isHasMoreReplies()) {
            rows.add(after, Row.moreRepliesRow(root));
            notifyItemInserted(after);
        }
    }

    private static boolean hasRepliesToShow(Comment r) {
        return r.getReplyCount() > 0
                || !r.getReplies().isEmpty()
                || r.isHasMoreReplies();
    }

    /** 折叠行文案中的条数：优先接口 reply_count，否则已加载列表长度 */
    private static int expandLabelCount(Comment r) {
        int c = r.getReplyCount();
        int loaded = r.getReplies().size();
        if (c > 0) {
            return Math.max(c, loaded);
        }
        if (loaded > 0) {
            return loaded;
        }
        return r.isHasMoreReplies() ? 1 : 0;
    }

    private void appendRootBlock(Comment r) {
        rows.add(Row.rootRow(r));
        if (!hasRepliesToShow(r)) {
            return;
        }
        if (expandedRootIds.contains(r.getId())) {
            for (Comment rep : r.getReplies()) {
                rows.add(Row.replyRow(r, rep));
            }
            if (r.isHasMoreReplies()) {
                rows.add(Row.moreRepliesRow(r));
            }
        } else {
            rows.add(Row.collapsedRepliesRow(r));
        }
    }

    public void setData(List<Comment> roots) {
        expandedRootIds.clear();
        rows.clear();
        if (roots != null) {
            for (Comment r : roots) {
                appendRootBlock(r);
            }
        }
        notifyDataSetChanged();
    }

    public void appendRoots(List<Comment> roots) {
        if (roots == null || roots.isEmpty()) return;
        int start = rows.size();
        for (Comment r : roots) {
            appendRootBlock(r);
        }
        notifyItemRangeInserted(start, rows.size() - start);
    }

    public void setReplyLoading(long rootId, boolean loading) {
        int idx = findLoadMoreRow(rootId);
        if (idx < 0) return;
        rows.get(idx).loadingMoreReplies = loading;
        notifyItemChanged(idx);
    }

    public void appendRepliesForRoot(Comment root, List<Comment> newReplies, boolean hasMore) {
        int p = findLoadMoreRow(root.getId());
        if (p < 0) return;
        rows.remove(p);
        notifyItemRemoved(p);
        int n = newReplies.size();
        for (int i = 0; i < n; i++) {
            rows.add(p + i, Row.replyRow(root, newReplies.get(i)));
        }
        notifyItemRangeInserted(p, n);
        root.appendRepliesLocal(newReplies);
        root.setHasMoreReplies(hasMore);
        if (hasMore) {
            rows.add(p + n, Row.moreRepliesRow(root));
            notifyItemInserted(p + n);
        }
    }

    private int findLoadMoreRow(long rootId) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind == Row.Kind.MORE_REPLIES && r.root.getId() == rootId) {
                return i;
            }
        }
        return -1;
    }

    private int findCollapsedRow(long rootId) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind == Row.Kind.COLLAPSED_REPLIES && r.root.getId() == rootId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        Row row = rows.get(position);
        switch (row.kind) {
            case ROOT:
                return TYPE_ROOT;
            case REPLY:
                return TYPE_REPLY;
            case COLLAPSED_REPLIES:
                return TYPE_REPLIES_COLLAPSED;
            case MORE_REPLIES:
            default:
                return TYPE_LOAD_MORE_REPLIES;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOAD_MORE_REPLIES) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_comment_load_more, parent, false);
            return new LoadMoreViewHolder(v);
        }
        if (viewType == TYPE_REPLIES_COLLAPSED) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_comment_replies_collapsed, parent, false);
            return new CollapsedRepliesViewHolder(v);
        }
        int layout = viewType == TYPE_ROOT ? R.layout.item_comment : R.layout.item_comment_reply;
        View v = LayoutInflater.from(context).inflate(layout, parent, false);
        return new RowViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (holder instanceof RowViewHolder
                && payloads.size() == 1
                && PAYLOAD_LIKE.equals(payloads.get(0))) {
            Row row = rows.get(position);
            applyLikeButtonUi((RowViewHolder) holder, row.comment);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof CollapsedRepliesViewHolder) {
            CollapsedRepliesViewHolder h = (CollapsedRepliesViewHolder) holder;
            int n = expandLabelCount(row.root);
            h.label.setText(context.getString(R.string.comment_expand_replies, n));
            h.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                expandRootAt(pos, row.root);
            });
            return;
        }
        if (holder instanceof LoadMoreViewHolder) {
            LoadMoreViewHolder h = (LoadMoreViewHolder) holder;
            if (row.loadingMoreReplies) {
                h.text.setText(R.string.loading);
                h.itemView.setOnClickListener(null);
            } else {
                h.text.setText(R.string.comment_load_more_replies);
                h.itemView.setOnClickListener(v -> {
                    if (loadMoreRepliesListener != null) {
                        loadMoreRepliesListener.onLoadMoreReplies(row.root);
                    }
                });
            }
            return;
        }
        RowViewHolder h = (RowViewHolder) holder;
        Comment c = row.comment;
        boolean isReply = row.kind == Row.Kind.REPLY;

        if (isReply) {
            h.content.setMaxLines(4);
            h.content.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            h.content.setMaxLines(Integer.MAX_VALUE);
            h.content.setEllipsize(null);
        }

        if (isReply && !TextUtils.isEmpty(c.getReplyToNickname())) {
            String name = TextUtils.isEmpty(c.getUsername()) ? "" : c.getUsername();
            h.username.setText(context.getString(R.string.comment_reply_chain_format, name, c.getReplyToNickname()));
            h.content.setText(TextUtils.isEmpty(c.getContent()) ? "" : c.getContent());
        } else {
            h.username.setText(c.getUsername());
            String body = c.getContent();
            h.content.setText(body != null ? body : "");
        }

        h.time.setText(c.getDisplayTime());
        bindAvatar(h.avatar, c);

        bindCommentLike(h, c);

        h.btnReply.setOnClickListener(v -> {
            if (replyClickListener != null) {
                replyClickListener.onReplyClick(row.root, row.comment);
            }
        });
    }

    private int findCommentRowPosition(long commentId) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind == Row.Kind.ROOT || r.kind == Row.Kind.REPLY) {
                if (r.comment.getId() == commentId) {
                    return i;
                }
            }
        }
        return RecyclerView.NO_POSITION;
    }

    /** 优先用点击时的 position，仍指向同一评论则 O(1)，否则再线性查找 */
    private int resolveRowPosition(long commentId, int hintPos) {
        if (hintPos != RecyclerView.NO_POSITION && hintPos >= 0 && hintPos < rows.size()) {
            Row r = rows.get(hintPos);
            if ((r.kind == Row.Kind.ROOT || r.kind == Row.Kind.REPLY) && r.comment.getId() == commentId) {
                return hintPos;
            }
        }
        return findCommentRowPosition(commentId);
    }

    private void bindCommentLike(RowViewHolder h, Comment c) {
        applyLikeButtonUi(h, c);
        h.btnLike.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            handleLikeClick(c, pos);
        });
    }

    private void applyLikeButtonUi(RowViewHolder h, Comment c) {
        h.btnLike.setText(c.getLikeCountText());
        int tint = ContextCompat.getColor(context, c.isLiked() ? R.color.primary : R.color.text_hint);
        TextViewCompat.setCompoundDrawableTintList(h.btnLike, ColorStateList.valueOf(tint));
        h.btnLike.setTextColor(tint);
    }

    private void handleLikeClick(Comment c, int positionHint) {
        if (!LoginHelper.requireLogin(context)) {
            return;
        }
        long cid = c.getId();
        if (likeInflight.contains(cid)) {
            return;
        }
        likeInflight.add(cid);
        ApiClient.getService().likeComment(cid).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                likeInflight.remove(cid);
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    Toast.makeText(context, R.string.comment_like_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, Object> data = response.body().getData();
                if (data == null) {
                    Toast.makeText(context, R.string.comment_like_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean liked = Boolean.TRUE.equals(data.get("liked"));
                c.setLiked(liked);
                Object cntObj = data.get("likes_count");
                if (cntObj instanceof Number) {
                    c.setLikeCount(((Number) cntObj).intValue());
                } else {
                    int lc = c.getLikeCount();
                    c.setLikeCount(liked ? lc + 1 : Math.max(0, lc - 1));
                }
                int pos = resolveRowPosition(cid, positionHint);
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos, PAYLOAD_LIKE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                likeInflight.remove(cid);
                Toast.makeText(context, R.string.comment_like_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindAvatar(CircleImageView avatar, Comment c) {
        if (avatar == null) return;
        String url = ImageUrlUtils.resolve(c.getAvatar());
        if (TextUtils.isEmpty(url)) {
            Glide.with(avatar).clear(avatar);
            avatar.setImageResource(R.drawable.ic_profile);
        } else {
            Glide.with(avatar)
                    .load(url)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(avatar);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {
        CircleImageView avatar;
        TextView username, content, time, btnLike, btnReply;

        RowViewHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.comment_avatar);
            username = v.findViewById(R.id.comment_username);
            content = v.findViewById(R.id.comment_content);
            time = v.findViewById(R.id.comment_time);
            btnLike = v.findViewById(R.id.btn_comment_like);
            btnReply = v.findViewById(R.id.btn_reply);
        }
    }

    static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        LoadMoreViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.tv_load_more_replies);
        }
    }

    static class CollapsedRepliesViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        CollapsedRepliesViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_expand_replies);
        }
    }
}
