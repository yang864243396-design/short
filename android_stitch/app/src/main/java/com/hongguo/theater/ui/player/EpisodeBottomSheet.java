package com.hongguo.theater.ui.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Episode;

import java.util.ArrayList;
import java.util.List;

public class EpisodeBottomSheet extends BottomSheetDialogFragment {

    private static final int GROUP_SIZE = 40;
    private String dramaTitle;
    private List<Episode> episodes;
    private int currentIndex;
    private OnEpisodeClickListener listener;

    public interface OnEpisodeClickListener {
        void onEpisodeClick(int position);
    }

    public static EpisodeBottomSheet newInstance(String title, List<Episode> episodes, int currentIndex) {
        EpisodeBottomSheet sheet = new EpisodeBottomSheet();
        sheet.dramaTitle = title;
        sheet.episodes = episodes != null ? episodes : new ArrayList<>();
        sheet.currentIndex = currentIndex;
        return sheet;
    }

    public void setOnEpisodeClickListener(OnEpisodeClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_episodes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.sheet_title);
        TextView tvCount = view.findViewById(R.id.sheet_count);
        TabLayout tabLayout = view.findViewById(R.id.tab_groups);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_episodes);
        View btnClose = view.findViewById(R.id.btn_close);

        tvTitle.setText(dramaTitle != null ? dramaTitle : "");
        tvCount.setText(String.format("共 %d 集", episodes.size()));
        btnClose.setOnClickListener(v -> dismiss());

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 6));

        int totalGroups = (int) Math.ceil(episodes.size() / (double) GROUP_SIZE);
        for (int i = 0; i < totalGroups; i++) {
            int start = i * GROUP_SIZE + 1;
            int end = Math.min((i + 1) * GROUP_SIZE, episodes.size());
            tabLayout.addTab(tabLayout.newTab().setText(start + "-" + end));
        }

        EpisodeGridAdapter adapter = new EpisodeGridAdapter(requireContext(), episodes, currentIndex, pos -> {
            if (listener != null) listener.onEpisodeClick(pos);
            dismiss();
        });
        recyclerView.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int groupStart = tab.getPosition() * GROUP_SIZE;
                adapter.setGroupOffset(groupStart);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (tabLayout.getTabCount() > 0) {
            tabLayout.selectTab(tabLayout.getTabAt(0));
        }
    }
}
