package com.jessica.feedapp.ui.feed.card;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.ui.feed.FeedAdapter;

/**
 * 纯文本卡片 Binder
 */
public class TextCardBinder implements CardBinder<FeedAdapter.TextViewHolder> {

    private final FeedAdapter adapter;

    public TextCardBinder(FeedAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public int getViewType() {
        return FeedItem.CARD_TYPE_TEXT;
    }

    @Override
    public boolean isForViewType(FeedItem item) {
        return item.getCardType() == FeedItem.CARD_TYPE_TEXT;
    }

    @Override
    public FeedAdapter.TextViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.item_feed_text, parent, false);
        return new FeedAdapter.TextViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FeedAdapter.TextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        adapter.setupItemClicks(holder.itemView, item, position);
    }

    @Override
    public int getSpanSize(FeedItem item) {
        return item.getSpanSize();
    }
}
