package com.jessica.feedapp.ui.feed;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ui.PlayerView;
import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.player.FeedVideoManager;
import com.jessica.feedapp.ui.feed.card.CardBinder;
import com.jessica.feedapp.ui.feed.card.ImageTextCardBinder;
import com.jessica.feedapp.ui.feed.card.TextCardBinder;
import com.jessica.feedapp.ui.feed.card.VideoCardBinder;

import java.util.ArrayList;
import java.util.List;

import android.util.SparseArray;

/**
 * 信息流列表适配器（插件式卡片扩展）：
 * - 文本 / 图文 / 视频 卡片由独立的 CardBinder 实现
 * - 底部 footer 展示加载更多状态
 * - 支持长按删卡
 * - 为曝光统计提供 getItemAt / getSpanSizeForPosition
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ===== Footer ViewType =====
    private static final int VIEW_TYPE_FOOTER = 100;

    // ===== Footer 状态 =====
    private boolean showFooter = false;
    private boolean footerLoading = false;
    private boolean footerError = false;

    public interface OnLoadMoreRetryListener {
        void onRetryLoadMore();
    }

    private OnLoadMoreRetryListener loadMoreRetryListener;

    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeedItem> data = new ArrayList<>();

    private final FeedVideoManager videoManager;

    // ===== Binder 插件管理 =====
    private final List<CardBinder<? extends RecyclerView.ViewHolder>> cardBinders = new ArrayList<>();
    private final SparseArray<CardBinder<? extends RecyclerView.ViewHolder>> binderMap = new SparseArray<>();

    public FeedAdapter(Context context, FeedVideoManager videoManager) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.videoManager = videoManager;

        // 注册三种默认卡片 Binder
        registerBinder(new TextCardBinder(this));
        registerBinder(new ImageTextCardBinder(this));
        registerBinder(new VideoCardBinder(this, videoManager));
    }

    /**
     * 注册一个新的卡片 Binder（用于插件式扩展）
     */
    public void registerBinder(CardBinder<? extends RecyclerView.ViewHolder> binder) {
        cardBinders.add(binder);
        binderMap.put(binder.getViewType(), binder);
    }

    public void setOnLoadMoreRetryListener(OnLoadMoreRetryListener listener) {
        this.loadMoreRetryListener = listener;
    }

    // ===== 对外数据操作 =====

    // 重置列表
    public void setItems(List<FeedItem> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    // 追加数据（loadMore）
    public void appendItems(List<FeedItem> items) {
        if (items == null || items.isEmpty()) return;
        int start = data.size();
        data.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    // footer：显示“正在加载更多…”
    public void showLoadMoreLoading() {
        showFooter = true;
        footerLoading = true;
        footerError = false;
        notifyFooterChanged();
    }

    // footer：显示“加载失败，点击重试”
    public void showLoadMoreError() {
        showFooter = true;
        footerLoading = false;
        footerError = true;
        notifyFooterChanged();
    }

    // 隐藏 footer
    public void hideFooter() {
        if (showFooter) {
            showFooter = false;
            footerLoading = false;
            footerError = false;
            notifyDataSetChanged();
        }
    }

    private void notifyFooterChanged() {
        if (showFooter) {
            notifyItemChanged(data.size());
        } else {
            notifyDataSetChanged();
        }
    }

    /**
     * 暴露给 ExposureTracker：根据 position 拿 FeedItem。
     * 如果是 footer 位置或越界，返回 null。
     */
    public FeedItem getItemAt(int position) {
        if (position < 0 || position >= data.size()) {
            return null;
        }
        return data.get(position);
    }

    /**
     * 暴露给 GridLayoutManager 的 SpanSizeLookup：
     * - footer 占满两列；
     * - 正常 item 使用 Binder 的 spanSize。
     */
    public int getSpanSizeForPosition(int position) {
        if (isFooterPosition(position)) {
            return 2;
        }
        FeedItem item = getItemAt(position);
        if (item == null) return 2;

        CardBinder<? extends RecyclerView.ViewHolder> binder = findBinderForItem(item);
        if (binder == null) {
            return item.getSpanSize();
        }
        return binder.getSpanSize(item);
    }

    // ===== Adapter 核心实现 =====

    @Override
    public int getItemCount() {
        return data.size() + (showFooter ? 1 : 0);
    }

    private boolean isFooterPosition(int position) {
        return showFooter && position == getItemCount() - 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isFooterPosition(position)) {
            return VIEW_TYPE_FOOTER;
        }
        FeedItem item = data.get(position);
        CardBinder<? extends RecyclerView.ViewHolder> binder = findBinderForItem(item);
        if (binder != null) {
            return binder.getViewType();
        }
        // 兜底：视为文本卡
        return FeedItem.CARD_TYPE_TEXT;
    }

    private CardBinder<? extends RecyclerView.ViewHolder> findBinderForItem(FeedItem item) {
        for (CardBinder<? extends RecyclerView.ViewHolder> binder : cardBinders) {
            if (binder.isForViewType(item)) {
                return binder;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        if (viewType == VIEW_TYPE_FOOTER) {
            View view = inflater.inflate(R.layout.item_feed_footer, parent, false);
            return new FooterViewHolder(view);
        }

        CardBinder<? extends RecyclerView.ViewHolder> binder = binderMap.get(viewType);
        if (binder == null) {
            // 未知类型，兜底使用 TextCard
            binder = binderMap.get(FeedItem.CARD_TYPE_TEXT);
        }

        // 这里做一次不安全转换，但 viewType 与 binder 已经通过 map 对齐
        @SuppressWarnings("unchecked")
        CardBinder<RecyclerView.ViewHolder> typedBinder =
                (CardBinder<RecyclerView.ViewHolder>) binder;

        return typedBinder.onCreateViewHolder(inflater, parent);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        if (holder instanceof FooterViewHolder) {
            bindFooter((FooterViewHolder) holder);
            return;
        }

        FeedItem item = data.get(position);
        if (item == null) return;

        int viewType = getItemViewType(position);
        CardBinder<? extends RecyclerView.ViewHolder> binder = binderMap.get(viewType);
        if (binder == null) {
            binder = findBinderForItem(item);
        }
        if (binder == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        CardBinder<RecyclerView.ViewHolder> typedBinder =
                (CardBinder<RecyclerView.ViewHolder>) binder;

        typedBinder.onBindViewHolder(holder, item, position);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            VideoViewHolder vh = (VideoViewHolder) holder;
            videoManager.onViewRecycled(vh.playerView);
        }
    }

    // ===== Footer 绑定 =====

    private void bindFooter(FooterViewHolder holder) {
        if (!showFooter) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE);

        if (footerLoading) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.tvMessage.setText("正在加载更多…");
            holder.itemView.setOnClickListener(null);
        } else if (footerError) {
            holder.progress.setVisibility(View.GONE);
            holder.tvMessage.setText("加载失败，点击重试");
            holder.itemView.setOnClickListener(v -> {
                if (loadMoreRetryListener != null) {
                    loadMoreRetryListener.onRetryLoadMore();
                }
            });
        } else {
            holder.progress.setVisibility(View.GONE);
            holder.tvMessage.setText("");
            holder.itemView.setOnClickListener(null);
        }
    }

    // ===== 点击 / 长按删卡：对外暴露给 Binder 使用 =====

    public void setupItemClicks(View itemView, FeedItem item, int position) {
        // 单击：Toast 提示
        itemView.setOnClickListener(v ->
                Toast.makeText(context, "点击卡片：" + item.getTitle(), Toast.LENGTH_SHORT).show()
        );

        // 长按：删除卡片
        itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("删除卡片")
                    .setMessage("确定要删除这条卡片吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        int pos = position;
                        if (pos >= 0 && pos < data.size()) {
                            data.remove(pos);
                            notifyItemRemoved(pos);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    // ===== ViewHolder 定义（提供给 Binder & Activity 使用） =====

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTitle;
        public TextView tvContent;

        public TextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }

    public static class ImageTextViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTitle;
        public TextView tvContent;
        public ImageView ivImage;

        public ImageTextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            ivImage = itemView.findViewById(R.id.iv_image);
        }
    }

    // 给 FeedActivity 的自动播放逻辑用到，所以 public
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTitle;
        public TextView tvContent;
        public TextView tvCountdown;
        public PlayerView playerView;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvCountdown = itemView.findViewById(R.id.tv_video_countdown);
            playerView = itemView.findViewById(R.id.player_view);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progress;
        TextView tvMessage;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            progress = itemView.findViewById(R.id.progress);
            tvMessage = itemView.findViewById(R.id.tv_message);
        }
    }
}
