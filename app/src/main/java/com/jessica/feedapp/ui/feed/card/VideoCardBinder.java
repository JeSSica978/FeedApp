package com.jessica.feedapp.ui.feed.card;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.player.FeedVideoManager;
import com.jessica.feedapp.ui.feed.FeedAdapter;

/**
 * 视频卡片 Binder（ExoPlayer）
 */
public class VideoCardBinder implements CardBinder<FeedAdapter.VideoViewHolder> {

    private final FeedAdapter adapter;
    private final FeedVideoManager videoManager;

    public VideoCardBinder(FeedAdapter adapter, FeedVideoManager videoManager) {
        this.adapter = adapter;
        this.videoManager = videoManager;
    }

    @Override
    public int getViewType() {
        return FeedItem.CARD_TYPE_VIDEO;
    }

    @Override
    public boolean isForViewType(FeedItem item) {
        return item.getCardType() == FeedItem.CARD_TYPE_VIDEO;
    }

    @Override
    public FeedAdapter.VideoViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.item_feed_video, parent, false);
        return new FeedAdapter.VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FeedAdapter.VideoViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText("[视频] " + item.getTitle());
        holder.tvContent.setText(item.getContent());
        holder.tvCountdown.setText("点击视频区域可暂停/继续");

        // 点击视频区域：手动播放/暂停
        holder.playerView.setOnClickListener(v ->
                videoManager.togglePlay(holder.playerView, item)
        );

        adapter.setupItemClicks(holder.itemView, item, position);
    }

    @Override
    public int getSpanSize(FeedItem item) {
        return item.getSpanSize();
    }
}
