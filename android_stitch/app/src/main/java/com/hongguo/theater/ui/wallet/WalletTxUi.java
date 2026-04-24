package com.hongguo.theater.ui.wallet;

import com.hongguo.theater.model.WalletTransaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 余额流水展示：仅四类业务；时间与分组格式。
 */
final class WalletTxUi {

    private WalletTxUi() {
    }

    static String displayTitle(WalletTransaction t) {
        String title = t.getTitle();
        if ("后台充值".equals(title)) return "后台赠送";
        if ("金币充值".equals(title)) return "充值金币";
        if (title == null || title.isEmpty()) return "—";
        return title;
    }

    static boolean isCredit(WalletTransaction t) {
        return "recharge".equals(t.getType());
    }

    static long parseCreatedMs(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        String s = raw.trim();
        try {
            if (s.endsWith("Z")) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                String core = s.substring(0, s.length() - 1);
                int dot = core.indexOf('.');
                if (dot > 0) core = core.substring(0, dot);
                Date d = f.parse(core);
                return d != null ? d.getTime() : 0L;
            }
            String local = s.replace('T', ' ');
            int dot = local.indexOf('.');
            if (dot > 0) local = local.substring(0, dot);
            int plus = local.indexOf('+');
            if (plus > 0) local = local.substring(0, plus).trim();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = f.parse(local.trim());
            return d != null ? d.getTime() : 0L;
        } catch (ParseException e) {
            return 0L;
        }
    }

    static String monthSectionLabel(String createdAt) {
        long ms = parseCreatedMs(createdAt);
        if (ms <= 0L) return "";
        SimpleDateFormat out = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
        return out.format(new Date(ms));
    }

    static String lineTimeLabel(String createdAt) {
        long ms = parseCreatedMs(createdAt);
        if (ms <= 0L) return createdAt != null ? createdAt.replace('T', ' ') : "";
        SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        return out.format(new Date(ms));
    }
}
