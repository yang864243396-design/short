package com.hongguo.theater.ui.profile;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** 免广告档位网格：列间距与行间距（2 列）。 */
public final class AdSkipTierGridSpacingDecoration extends RecyclerView.ItemDecoration {

    private final int gapPx;
    private final int spanCount;

    public AdSkipTierGridSpacingDecoration(int spanCount, int gapPx) {
        this.spanCount = spanCount;
        this.gapPx = gapPx;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (!(lm instanceof GridLayoutManager)) return;
        int span = ((GridLayoutManager) lm).getSpanCount();
        if (span != spanCount) return;

        RecyclerView.Adapter<?> a = parent.getAdapter();
        int n = a != null ? a.getItemCount() : 0;
        if (n <= 0) return;

        int col = pos % spanCount;
        int half = gapPx / 2;
        outRect.left = col == 0 ? 0 : half;
        outRect.right = col == spanCount - 1 ? 0 : half;

        int row = pos / spanCount;
        int rows = (n + spanCount - 1) / spanCount;
        outRect.top = row == 0 ? 0 : half;
        outRect.bottom = row < rows - 1 ? half : 0;
    }
}
