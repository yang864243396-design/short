package com.hongguo.theater.ui.home;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Banner;
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class BannerPagerAdapter extends RecyclerView.Adapter<BannerPagerAdapter.ViewHolder> {

    private final Context context;
    private final List<Banner> banners = new ArrayList<>();

    public BannerPagerAdapter(Context context) {
        this.context = context;
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

        String imageUrl = banner.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (imageUrl.startsWith("/")) {
                String baseUrl = BuildConfig.BASE_URL;
                String host = baseUrl.replace("/api/v1/", "");
                imageUrl = host + imageUrl;
            }
            Glide.with(context)
                    .load(imageUrl)
                    .transform(new CenterCrop(), new RoundedCorners(24))
                    .into(holder.image);
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
                context.startActivity(intent);
            } else if (banner.getLinkUrl() != null && !banner.getLinkUrl().isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(banner.getLinkUrl()));
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return banners.size();
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
