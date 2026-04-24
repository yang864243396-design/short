package com.hongguo.theater.ui.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Comment;
import com.hongguo.theater.model.CommentPage;
import com.hongguo.theater.utils.LoginHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentBottomSheet extends BottomSheetDialogFragment {

    private static final int COMMENT_PAGE_SIZE = 15;
    private static final int REPLY_PAGE_SIZE = 5;
    /** 距列表末尾提前触发主评分页，减少等待感 */
    private static final int COMMENT_PRELOAD_FROM_END = 3;

    public interface OnCommentPostedListener {
        void onCommentPosted();
    }

    private long episodeId;
    private RecyclerView recyclerView;
    private LinearLayoutManager commentsLayoutManager;
    private View layoutCommentEmpty;
    private View layoutReplyBanner;
    private TextView tvReplyBanner;
    private CommentAdapter adapter;
    private EditText editComment;
    private OnCommentPostedListener commentPostedListener;
    private boolean commentsLoadFinished;

    private int nextPageToLoad = 1;
    private boolean hasMoreFromServer = true;
    private boolean loadingMore = false;
    private Call<?> commentsInflight;

    private final Map<Long, Integer> replyNextPageByRoot = new HashMap<>();
    private final Set<Long> loadingReplyRootIds = new HashSet<>();
    private int commentDataGen = 0;

    private long replyParentId;
    private long replyToCommentId;
    private String replyNickname = "";

    public void setOnCommentPostedListener(OnCommentPostedListener listener) {
        this.commentPostedListener = listener;
    }

    public static CommentBottomSheet newInstance(long episodeId) {
        CommentBottomSheet sheet = new CommentBottomSheet();
        Bundle args = new Bundle();
        args.putLong("episode_id", episodeId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            episodeId = getArguments().getLong("episode_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

        recyclerView = view.findViewById(R.id.recycler_comments);
        layoutCommentEmpty = view.findViewById(R.id.layout_comment_empty);
        layoutReplyBanner = view.findViewById(R.id.layout_reply_banner);
        tvReplyBanner = view.findViewById(R.id.tv_reply_banner);
        commentsLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(commentsLayoutManager);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setHorizontalScrollBarEnabled(false);
        recyclerView.setHasFixedSize(false);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        adapter = new CommentAdapter(requireContext(), (root, replyTo) -> {
            replyParentId = root.getId();
            replyToCommentId = replyTo.getId();
            replyNickname = replyTo.getUsername();
            if (replyNickname == null) {
                replyNickname = "";
            }
            layoutReplyBanner.setVisibility(View.VISIBLE);
            tvReplyBanner.setText(getString(R.string.comment_replying_banner, replyNickname));
            editComment.setHint(getString(R.string.comment_reply_hint_format, replyNickname));
            editComment.requestFocus();
        }, this::loadMoreReplies);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loadingMore || !hasMoreFromServer || nextPageToLoad < 2) {
                    return;
                }
                LinearLayoutManager lm = commentsLayoutManager;
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (total > 0 && lastVisible >= total - COMMENT_PRELOAD_FROM_END) {
                    loadNextCommentsPage();
                }
            }
        });

        editComment = view.findViewById(R.id.edit_comment);
        view.findViewById(R.id.btn_send).setOnClickListener(v -> sendComment());
        view.findViewById(R.id.btn_cancel_reply).setOnClickListener(v -> clearReplyTarget());

        reloadComments();
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) return;
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        int half = requireContext().getResources().getDisplayMetrics().heightPixels / 2;
        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        lp.height = half;
        sheet.setLayoutParams(lp);
        BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(half);
        behavior.setMaxHeight(half);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onDestroyView() {
        if (commentsInflight != null) {
            commentsInflight.cancel();
            commentsInflight = null;
        }
        recyclerView = null;
        commentsLayoutManager = null;
        super.onDestroyView();
    }

    private void clearReplyTarget() {
        replyParentId = 0L;
        replyToCommentId = 0L;
        replyNickname = "";
        if (layoutReplyBanner != null) {
            layoutReplyBanner.setVisibility(View.GONE);
        }
        if (editComment != null) {
            editComment.setHint(R.string.comment_hint);
        }
    }

    private void updateCommentEmptyState() {
        if (layoutCommentEmpty == null || recyclerView == null) return;
        if (!commentsLoadFinished) {
            layoutCommentEmpty.setVisibility(View.GONE);
            return;
        }
        boolean empty = adapter.getItemCount() == 0;
        layoutCommentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void reloadComments() {
        commentDataGen++;
        replyNextPageByRoot.clear();
        loadingReplyRootIds.clear();
        if (commentsInflight != null) {
            commentsInflight.cancel();
            commentsInflight = null;
        }
        loadingMore = false;
        nextPageToLoad = 1;
        hasMoreFromServer = true;
        commentsLoadFinished = false;

        Call<ApiResponse<CommentPage>> call = ApiClient.getService()
                .getComments(episodeId, 1, COMMENT_PAGE_SIZE);
        commentsInflight = call;
        call.enqueue(new Callback<ApiResponse<CommentPage>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<CommentPage>> c,
                                   @NonNull Response<ApiResponse<CommentPage>> response) {
                if (commentsInflight != c) return;
                commentsInflight = null;
                if (!isAdded()) return;
                commentsLoadFinished = true;
                loadingMore = false;
                if (recyclerView == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    CommentPage page = response.body().getData();
                    if (page != null) {
                        adapter.setData(page.getList());
                        hasMoreFromServer = page.isHasMore();
                        nextPageToLoad = hasMoreFromServer ? 2 : 1;
                    } else {
                        adapter.setData(null);
                        hasMoreFromServer = false;
                    }
                }
                updateCommentEmptyState();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<CommentPage>> c, @NonNull Throwable t) {
                if (commentsInflight != c) return;
                commentsInflight = null;
                if (!isAdded()) return;
                commentsLoadFinished = true;
                loadingMore = false;
                updateCommentEmptyState();
            }
        });
    }

    private void loadNextCommentsPage() {
        if (loadingMore || !hasMoreFromServer || nextPageToLoad < 2) return;
        loadingMore = true;
        Call<ApiResponse<CommentPage>> call = ApiClient.getService()
                .getComments(episodeId, nextPageToLoad, COMMENT_PAGE_SIZE);
        commentsInflight = call;
        final int requestedPage = nextPageToLoad;
        call.enqueue(new Callback<ApiResponse<CommentPage>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<CommentPage>> c,
                                   @NonNull Response<ApiResponse<CommentPage>> response) {
                if (commentsInflight != c) return;
                commentsInflight = null;
                if (!isAdded()) return;
                loadingMore = false;
                if (recyclerView == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    CommentPage page = response.body().getData();
                    if (page != null) {
                        adapter.appendRoots(page.getList());
                        hasMoreFromServer = page.isHasMore();
                        if (hasMoreFromServer) {
                            nextPageToLoad = requestedPage + 1;
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<CommentPage>> c, @NonNull Throwable t) {
                if (commentsInflight != c) return;
                commentsInflight = null;
                if (!isAdded()) return;
                loadingMore = false;
            }
        });
    }

    private void loadMoreReplies(Comment root) {
        final int gen = commentDataGen;
        long rid = root.getId();
        if (loadingReplyRootIds.contains(rid)) return;
        int page = replyNextPageByRoot.getOrDefault(rid, 2);
        loadingReplyRootIds.add(rid);
        adapter.setReplyLoading(rid, true);

        ApiClient.getService().getCommentReplies(episodeId, rid, page, REPLY_PAGE_SIZE)
                .enqueue(new Callback<ApiResponse<CommentPage>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<CommentPage>> call,
                                           @NonNull Response<ApiResponse<CommentPage>> response) {
                        if (!isAdded() || gen != commentDataGen) return;
                        loadingReplyRootIds.remove(rid);
                        if (recyclerView == null) return;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            CommentPage cp = response.body().getData();
                            if (cp != null) {
                                adapter.appendRepliesForRoot(root, cp.getList(), cp.isHasMore());
                                if (cp.isHasMore()) {
                                    replyNextPageByRoot.put(rid, page + 1);
                                } else {
                                    replyNextPageByRoot.remove(rid);
                                }
                                return;
                            }
                        }
                        if (recyclerView != null) {
                            adapter.setReplyLoading(rid, false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<CommentPage>> call, @NonNull Throwable t) {
                        if (!isAdded() || gen != commentDataGen) return;
                        loadingReplyRootIds.remove(rid);
                        if (recyclerView != null) {
                            adapter.setReplyLoading(rid, false);
                        }
                    }
                });
    }

    private void sendComment() {
        if (!LoginHelper.requireLogin(requireContext())) return;
        String text = editComment.getText().toString().trim();
        if (text.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("content", text);
        final boolean postingReply = replyParentId > 0;
        final long savedParentId = replyParentId;
        final long savedReplyToId = replyToCommentId;
        if (replyParentId > 0) {
            body.put("parent_id", replyParentId);
            // 始终带上被回复的评论 id，便于服务端解析主评；避免仅依赖 parent_id 时偶发丢失导致回复被存成主评
            long effectiveReplyTo = replyToCommentId > 0 ? replyToCommentId : replyParentId;
            body.put("reply_to_comment_id", effectiveReplyTo);
        }

        ApiClient.getService().postComment(episodeId, body)
                .enqueue(new Callback<ApiResponse<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Comment>> call,
                                   @NonNull Response<ApiResponse<Comment>> response) {
                if (!isAdded()) return;
                if (getView() == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Comment posted = response.body().getData();
                    editComment.setText("");
                    clearReplyTarget();
                    Toast.makeText(requireContext(), R.string.comment_post_success, Toast.LENGTH_SHORT).show();
                    if (postingReply && posted != null) {
                        Comment root = adapter.findRootComment(savedParentId);
                        if (root != null) {
                            long anchorId = savedReplyToId > 0 ? savedReplyToId : savedParentId;
                            int pos = adapter.insertReplyBelowTarget(root, anchorId, posted);
                            if (pos >= 0 && recyclerView != null && commentsLayoutManager != null) {
                                recyclerView.post(() -> {
                                    if (recyclerView == null || commentsLayoutManager == null) return;
                                    commentsLayoutManager.scrollToPositionWithOffset(pos, 0);
                                });
                            }
                        }
                    } else {
                        reloadComments();
                    }
                    if (commentPostedListener != null) {
                        commentPostedListener.onCommentPosted();
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Comment>> call, @NonNull Throwable t) {}
        });
    }
}
