package com.hongguo.theater.ui.wallet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.WalletTransaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WalletTxListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH = 0;
    private static final int TYPE_TX = 1;
    private static final int TYPE_FOOTER = 2;

    private final List<Row> rows = new ArrayList<>();
    private final NumberFormat amountFormat = NumberFormat.getIntegerInstance(Locale.US);

    public void setTransactions(List<WalletTransaction> flat, boolean showEndFooter) {
        rows.clear();
        if (flat != null && !flat.isEmpty()) {
            String lastMonth = null;
            for (WalletTransaction t : flat) {
                String month = WalletTxUi.monthSectionLabel(t.getCreatedAt());
                if (!month.isEmpty() && !month.equals(lastMonth)) {
                    rows.add(new MonthRow(month));
                    lastMonth = month;
                }
                rows.add(new TxRow(t));
            }
            if (showEndFooter) {
                rows.add(new FooterRow());
            }
        }
        notifyDataSetChanged();
    }

    public int getTransactionRowCount() {
        int n = 0;
        for (Row r : rows) {
            if (r instanceof TxRow) n++;
        }
        return n;
    }

    @Override
    public int getItemViewType(int position) {
        Row r = rows.get(position);
        if (r instanceof MonthRow) return TYPE_MONTH;
        if (r instanceof FooterRow) return TYPE_FOOTER;
        return TYPE_TX;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MONTH) {
            View v = inf.inflate(R.layout.item_wallet_tx_month, parent, false);
            return new MonthVH(v);
        }
        if (viewType == TYPE_FOOTER) {
            View v = inf.inflate(R.layout.item_wallet_tx_footer, parent, false);
            return new FooterVH(v);
        }
        View v = inf.inflate(R.layout.item_wallet_tx_row, parent, false);
        return new TxVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row r = rows.get(position);
        if (holder instanceof MonthVH) {
            ((MonthVH) holder).bind(((MonthRow) r).label);
        } else if (holder instanceof TxVH) {
            ((TxVH) holder).bind(((TxRow) r).tx, amountFormat);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private abstract static class Row {
    }

    private static final class MonthRow extends Row {
        final String label;

        MonthRow(String label) {
            this.label = label;
        }
    }

    private static final class TxRow extends Row {
        final WalletTransaction tx;

        TxRow(WalletTransaction tx) {
            this.tx = tx;
        }
    }

    private static final class FooterRow extends Row {
    }

    static class MonthVH extends RecyclerView.ViewHolder {
        final TextView tvMonth;

        MonthVH(@NonNull View itemView) {
            super(itemView);
            tvMonth = itemView.findViewById(R.id.tv_wallet_tx_month);
        }

        void bind(String label) {
            tvMonth.setText(label);
        }
    }

    static class FooterVH extends RecyclerView.ViewHolder {
        FooterVH(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class TxVH extends RecyclerView.ViewHolder {
        final View iconBg;
        final ImageView icon;
        final TextView tvTitle;
        final TextView tvMeta;
        final TextView tvAmount;

        TxVH(@NonNull View itemView) {
            super(itemView);
            iconBg = itemView.findViewById(R.id.wallet_tx_icon_bg);
            icon = itemView.findViewById(R.id.wallet_tx_icon);
            tvTitle = itemView.findViewById(R.id.tv_tx_title);
            tvMeta = itemView.findViewById(R.id.tv_tx_meta);
            tvAmount = itemView.findViewById(R.id.tv_tx_amount);
        }

        void bind(WalletTransaction t, NumberFormat nf) {
            tvTitle.setText(WalletTxUi.displayTitle(t));
            tvMeta.setText(WalletTxUi.lineTimeLabel(t.getCreatedAt()));
            boolean credit = WalletTxUi.isCredit(t);
            int accent = ContextCompat.getColor(itemView.getContext(), R.color.wallet_tx_accent);
            int muted = ContextCompat.getColor(itemView.getContext(), R.color.on_surface_variant);
            if (credit) {
                iconBg.setBackgroundResource(R.drawable.bg_wallet_tx_icon_positive);
                icon.setImageResource(R.drawable.ic_wallet_tx_plus);
                icon.clearColorFilter();
                String num = nf.format(t.getAmount());
                tvAmount.setText(itemView.getContext().getString(R.string.wallet_tx_amount_plus_format, num));
                tvAmount.setTextColor(accent);
            } else {
                iconBg.setBackgroundResource(R.drawable.bg_wallet_tx_icon_negative);
                icon.setImageResource(R.drawable.ic_wallet_tx_consume);
                icon.clearColorFilter();
                String num = nf.format(t.getAmount());
                tvAmount.setText(itemView.getContext().getString(R.string.wallet_tx_amount_minus_format, num));
                tvAmount.setTextColor(muted);
            }
        }
    }
}
