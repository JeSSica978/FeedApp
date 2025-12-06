package com.jessica.feedapp.ui.feed.card;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.ui.feed.FeedAdapter;

/**
 * 图文卡片 Binder（IMAGE_TEXT）
 */
public class ImageTextCardBinder implements CardBinder<FeedAdapter.ImageTextViewHolder> {

    private final FeedAdapter adapter;

    public ImageTextCardBinder(FeedAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public int getViewType() {
        return FeedItem.CARD_TYPE_IMAGE_TEXT;
    }

    @Override
    public boolean isForViewType(FeedItem item) {
        return item.getCardType() == FeedItem.CARD_TYPE_IMAGE_TEXT;
    }

    @Override
    public FeedAdapter.ImageTextViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.item_feed_image, parent, false);
        return new FeedAdapter.ImageTextViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FeedAdapter.ImageTextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        // 这里你可以根据 imageUrl 加载图片（Glide/Picasso），当前 Demo 先不加载真实图
        adapter.setupItemClicks(holder.itemView, item, position);
    }

    @Override
    public int getSpanSize(FeedItem item) {
        return item.getSpanSize();
    }
}
