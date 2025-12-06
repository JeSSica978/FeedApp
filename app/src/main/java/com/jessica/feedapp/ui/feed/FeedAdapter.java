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

import java.util.ArrayList;
import java.util.List;

/**
 * 信息流列表适配器：
 * - 文本 / 图文 / 视频 三种卡片
 * - 底部 footer 展示加载更多状态
 * - 支持长按删卡
 * - 为曝光统计提供 getItemAt / getSpanSizeForPosition
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ===== ViewType 定义 =====
    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_IMAGE_TEXT = 1;
    private static final int VIEW_TYPE_VIDEO = 2;
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

    public FeedAdapter(Context context, FeedVideoManager videoManager) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.videoManager = videoManager;
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
     * - 正常 item 使用自身的 spanSize。
     */
    public int getSpanSizeForPosition(int position) {
        if (isFooterPosition(position)) {
            return 2;
        }
        FeedItem item = getItemAt(position);
        if (item == null) return 2;
        return item.getSpanSize();
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
        int cardType = item.getCardType();
        if (cardType == FeedItem.CARD_TYPE_VIDEO) {
            return VIEW_TYPE_VIDEO;
        } else if (cardType == FeedItem.CARD_TYPE_IMAGE_TEXT) { // ✅ 对齐 FeedItem 常量
            return VIEW_TYPE_IMAGE_TEXT;
        } else {
            return VIEW_TYPE_TEXT;
        }
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
        } else if (viewType == VIEW_TYPE_IMAGE_TEXT) {
            View view = inflater.inflate(R.layout.item_feed_image, parent, false);
            return new ImageTextViewHolder(view);
        } else if (viewType == VIEW_TYPE_VIDEO) {
            View view = inflater.inflate(R.layout.item_feed_video, parent, false);
            return new VideoViewHolder(view);
        } else { // TEXT
            View view = inflater.inflate(R.layout.item_feed_text, parent, false);
            return new TextViewHolder(view);
        }
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

        if (holder instanceof TextViewHolder) {
            bindText((TextViewHolder) holder, item, position);
        } else if (holder instanceof ImageTextViewHolder) {
            bindImageText((ImageTextViewHolder) holder, item, position);
        } else if (holder instanceof VideoViewHolder) {
            bindVideo((VideoViewHolder) holder, item, position);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            VideoViewHolder vh = (VideoViewHolder) holder;
            videoManager.onViewRecycled(vh.playerView);
        }
    }

    // ===== 各类型卡片绑定逻辑 =====

    private void bindText(TextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        setupItemClicks(holder.itemView, item, position);
    }

    private void bindImageText(ImageTextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        // 如果你有占位图资源可以在这里 setImageResource
        // holder.ivImage.setImageResource(R.drawable.sample_image);
        setupItemClicks(holder.itemView, item, position);
    }

    private void bindVideo(VideoViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText("[视频] " + item.getTitle());
        holder.tvContent.setText(item.getContent());
        holder.tvCountdown.setText("点击视频区域可暂停/继续");

        // 点击视频区域：手动播放/暂停
        holder.playerView.setOnClickListener(v ->
                videoManager.togglePlay(holder.playerView, item)
        );

        setupItemClicks(holder.itemView, item, position);
    }

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

    // ===== 点击 / 长按删卡 =====

    private void setupItemClicks(View itemView, FeedItem item, int position) {
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

    // ===== ViewHolder 定义 =====

    static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;

        TextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }

    static class ImageTextViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;
        ImageView ivImage;

        ImageTextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            ivImage = itemView.findViewById(R.id.iv_image);
        }
    }

    // 给 FeedActivity 的自动播放逻辑用到，所以 public
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;
        TextView tvCountdown;
        PlayerView playerView;

        VideoViewHolder(@NonNull View itemView) {
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
            // ✅ 对齐 item_feed_footer.xml 中的 id
            progress = itemView.findViewById(R.id.progress);
            tvMessage = itemView.findViewById(R.id.tv_message);
        }
    }
}
