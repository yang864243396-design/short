package com.hongguo.theater.ui.wallet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.RechargePackageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WalletRechargePackageAdapter extends RecyclerView.Adapter<WalletRechargePackageAdapter.VH> {

    public interface OnPackageClickListener {
        void onPackageClick(RechargePackageItem pkg);
    }

    private final List<RechargePackageItem> items = new ArrayList<>();
    private OnPackageClickListener listener;

    public void setListener(OnPackageClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<RechargePackageItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet_package, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RechargePackageItem p = items.get(position);
        h.tvCoins.setText(String.format(Locale.getDefault(), "%d", p.getCoins()));
        h.tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", p.getPriceYuan()));

        int bonus = p.getBonusCoins();
        if (bonus > 0) {
            h.card.setBackgroundResource(R.drawable.bg_wallet_package_grid_cell);
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(h.itemView.getContext().getString(R.string.wallet_package_bonus_format, bonus));
            h.badge.setBackgroundResource(R.drawable.bg_wallet_badge_bonus);
        } else {
            h.card.setBackgroundResource(R.drawable.bg_wallet_package_grid_cell);
            h.badge.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPackageClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final TextView badge;
        final TextView tvCoins;
        final TextView tvPrice;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_pkg);
            badge = itemView.findViewById(R.id.tv_pkg_badge);
            tvCoins = itemView.findViewById(R.id.tv_pkg_coins);
            tvPrice = itemView.findViewById(R.id.tv_pkg_price);
        }
    }
}
