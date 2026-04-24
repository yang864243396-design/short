package com.hongguo.theater.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.AdSkipStatus;
import com.hongguo.theater.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 免广告特权档位：纵向胶囊列表，单选（设计稿：未选深灰底 / 选中橙描边与强调字色）。
 */
public class AdSkipTierPickerAdapter extends RecyclerView.Adapter<AdSkipTierPickerAdapter.VH> {

    /** 仅刷新选中描边与文字色，避免整项重绑造成迟滞。 */
    private static final Object PAYLOAD_SELECTION = new Object();

    private final List<AdSkipStatus.Config> items = new ArrayList<>();
    private int selectedPosition = 0;

    public void setData(List<AdSkipStatus.Config> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        selectedPosition = 0;
        if (selectedPosition >= items.size()) {
            selectedPosition = items.isEmpty() ? 0 : items.size() - 1;
        }
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        if (position < 0 || position >= items.size()) return;
        int old = selectedPosition;
        if (old == position) return;
        selectedPosition = position;
        notifyItemChanged(old, PAYLOAD_SELECTION);
        notifyItemChanged(position, PAYLOAD_SELECTION);
    }

    public AdSkipStatus.Config getSelectedConfig() {
        if (items.isEmpty()) return null;
        if (selectedPosition < 0 || selectedPosition >= items.size()) return null;
        return items.get(selectedPosition);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ad_skip_tier_pill, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object p : payloads) {
                if (p == PAYLOAD_SELECTION) {
                    applyRowStyle(h, position == selectedPosition);
                    return;
                }
            }
        }
        onBindViewHolder(h, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AdSkipStatus.Config c = items.get(position);
        h.tvTitle.setText(FormatUtils.formatAdSkipTierTitle(h.itemView.getContext(), c));
        h.tvPrice.setText(String.format(Locale.getDefault(), "%d", c.getPriceCoins()));

        boolean selected = position == selectedPosition;
        applyRowStyle(h, selected);

        final int pos = position;
        h.itemView.setOnClickListener(v -> setSelectedPosition(pos));
    }

    private static void applyRowStyle(VH h, boolean selected) {
        int accent = ContextCompat.getColor(h.itemView.getContext(), R.color.ad_skip_privilege_accent);
        int white = ContextCompat.getColor(h.itemView.getContext(), R.color.white);
        if (selected) {
            h.row.setBackgroundResource(R.drawable.bg_ad_skip_tier_selected);
            h.tvTitle.setTextColor(accent);
            h.tvPrice.setTextColor(accent);
            h.tvUnit.setTextColor(white);
        } else {
            h.row.setBackgroundResource(R.drawable.bg_ad_skip_tier_unselected);
            h.tvTitle.setTextColor(white);
            h.tvPrice.setTextColor(white);
            h.tvUnit.setTextColor(white);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView tvTitle;
        final TextView tvPrice;
        final TextView tvUnit;

        VH(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.ad_skip_tier_row);
            tvTitle = itemView.findViewById(R.id.tv_tier_title);
            tvPrice = itemView.findViewById(R.id.tv_tier_price);
            tvUnit = itemView.findViewById(R.id.tv_tier_unit);
        }
    }
}
