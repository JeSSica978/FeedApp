package com.jessica.feedapp.ui.feed.card;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.jessica.feedapp.model.FeedItem;

/**
 * 卡片样式插件接口：
 * - 一个 Binder 对应一种卡片 viewType
 * - 负责创建 ViewHolder、绑定数据、spanSize 等
 */
public interface CardBinder<VH extends RecyclerView.ViewHolder> {

    /** 用于 RecyclerView 的 viewType，通常与 FeedItem.cardType 对应 */
    int getViewType();

    /** 是否处理这个 FeedItem（一般根据 cardType 判断） */
    boolean isForViewType(FeedItem item);

    /** 创建 ViewHolder */
    VH onCreateViewHolder(LayoutInflater inflater, ViewGroup parent);

    /** 绑定数据 */
    void onBindViewHolder(VH holder, FeedItem item, int position);

    /** 返回该卡片所占列数（用于 GridLayoutManager.spanSizeLookup） */
    int getSpanSize(FeedItem item);
}
