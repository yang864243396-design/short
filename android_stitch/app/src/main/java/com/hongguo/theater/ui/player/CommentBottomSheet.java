package com.hongguo.theater.ui.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Comment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentBottomSheet extends BottomSheetDialogFragment {

    private long episodeId;
    private RecyclerView recyclerView;
    private CommentAdapter adapter;
    private EditText editComment;

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
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CommentAdapter(requireContext());
        recyclerView.setAdapter(adapter);

        editComment = view.findViewById(R.id.edit_comment);
        view.findViewById(R.id.btn_send).setOnClickListener(v -> sendComment());

        loadComments();
    }

    private void loadComments() {
        ApiClient.getService().getComments(episodeId, 1, 50)
                .enqueue(new Callback<ApiResponse<List<Comment>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Comment>>> call,
                                   @NonNull Response<ApiResponse<List<Comment>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    adapter.setData(response.body().getData());
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Comment>>> call, @NonNull Throwable t) {}
        });
    }

    private void sendComment() {
        String text = editComment.getText().toString().trim();
        if (text.isEmpty()) return;

        Map<String, String> body = new HashMap<>();
        body.put("content", text);

        ApiClient.getService().postComment(episodeId, body)
                .enqueue(new Callback<ApiResponse<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Comment>> call,
                                   @NonNull Response<ApiResponse<Comment>> response) {
                if (!isAdded()) return;
                editComment.setText("");
                loadComments();
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Comment>> call, @NonNull Throwable t) {}
        });
    }
}
