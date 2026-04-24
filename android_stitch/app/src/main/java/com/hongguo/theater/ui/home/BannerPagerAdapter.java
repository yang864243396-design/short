package com.hongguo.theater.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Banner;
import com.hongguo.theater.utils.ImageUrlUtils;
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class BannerPagerAdapter extends RecyclerView.Adapter<BannerPagerAdapter.ViewHolder> {

    /** 与 item_home_banner_pager 中高度 140dp、左右各 spacing_lg(16dp) 一致，避免按原图全尺寸解码 */
    public static int[] bannerTargetSizePx(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int marginDp = 16 + 16;
        int w = context.getResources().getDisplayMetrics().widthPixels - (int) (marginDp * density);
        int h = (int) (140 * density);
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        return new int[]{w, h};
    }

    private final Context context;
    private final List<Banner> banners = new ArrayList<>();
    private final int targetW;
    private final int targetH;

    public BannerPagerAdapter(Context context) {
        this.context = context;
        int[] wh = bannerTargetSizePx(context);
        this.targetW = wh[0];
        this.targetH = wh[1];
    }

    public void setData(List<Banner> data) {
        banners.clear();
        if (data != null) banners.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_ad_banner, parent, false);
        return new ViewHolder(v);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Banner banner = banners.get(position);

        String imageUrl = ImageUrlUtils.resolve(banner.getImageUrl());
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(context)
                    .load(imageUrl)
                    .override(targetW, targetH)
                    .priority(Priority.HIGH)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .transform(new CenterCrop(), new RoundedCorners(24))
                    .into(holder.image);
        } else {
            Glide.with(context).clear(holder.image);
            holder.image.setImageResource(R.drawable.bg_cover_placeholder);
        }

        if (banner.getTitle() != null && !banner.getTitle().isEmpty()) {
            holder.title.setVisibility(View.VISIBLE);
            holder.title.setText(banner.getTitle());
        } else {
            holder.title.setVisibility(View.GONE);
        }

        holder.tagAd.setVisibility(View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (banner.isDramaLink() && banner.getDramaId() > 0) {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("drama_id", banner.getDramaId());
                startActivitySafe(intent);
                return;
            }
            String rawLink = banner.getLinkUrl();
            if (rawLink != null && !rawLink.trim().isEmpty()) {
                String url = normalizeExternalUrl(rawLink.trim());
                try {
                    Uri uri = Uri.parse(url);
                    if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
                        Toast.makeText(context, "链接格式无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivitySafe(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return banners.size();
    }

    private void startActivitySafe(Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "未找到可打开的应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 外链无 scheme 时补上 https://，避免 Uri无效或系统无法路由 */
    private static String normalizeExternalUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        String lower = url.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("intent:") || lower.startsWith("market:")
                || lower.startsWith("tel:") || lower.startsWith("mailto:")) {
            return url;
        }
        return "https://" + url;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, tagAd;

        ViewHolder(View v) {
            super(v);
            image = v.findViewById(R.id.banner_ad_image);
            title = v.findViewById(R.id.banner_ad_title);
            tagAd = v.findViewById(R.id.banner_ad_tag);
        }
    }
}
