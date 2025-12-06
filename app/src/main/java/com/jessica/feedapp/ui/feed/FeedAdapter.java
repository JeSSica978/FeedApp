package com.jessica.feedapp.ui.feed;

import android.content.Context;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 信息流列表适配器：
 * - 支持三种 ViewType: 文本卡 / 图文卡 / 视频卡
 * - 支持单列 / 双列混排（通过 FeedItem.spanSize 控制）
 * - 支持 loadMore 底部 Footer（加载中 / 加载失败-点击重试）
 * - 支持点击卡片：Toast 提示
 * - 支持长按卡片：弹窗确认删除
 */

public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 四种布局类型
    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_IMAGE_TEXT = 1;
    private static final int VIEW_TYPE_VIDEO = 2;
    private static final int VIEW_TYPE_FOOTER = 100;  // footer 的类型

    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeedItem> data = new ArrayList<>();

    // ----- loadMore Footer 状态 -----
    // 控制底部加载更多那一行的显示文案和状态条
    private boolean showFooter = false;
    private boolean footerLoading = false;
    private boolean footerError = false;


    // 给“点击重试”用的回调接口
    public interface OnLoadMoreRetryListener {
        void onRetryLoadMore();
    }
    private OnLoadMoreRetryListener loadMoreRetryListener;

    public FeedAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnLoadMoreRetryListener(OnLoadMoreRetryListener listener) {
        this.loadMoreRetryListener = listener;
    }

    // ----- 对外数据操作 -----
    // 重置列表
    public void setItems(List<FeedItem> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    // 追加数据（用于 loadMore）
    public void appendItems(List<FeedItem> items) {
        if (items == null || items.isEmpty()) return;
        int start = data.size();
        data.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    // 获取当前列表（用于本地缓存等场景）
    public List<FeedItem> getItems() {
        return new ArrayList<>(data); // 返回一份拷贝，避免外部修改内部 data
    }


    // 给曝光模块用的，从“Adapter 的 position”拿到真正的 FeedItem
    public FeedItem getItemAt(int position) {
        // 注意：data.size() 只包含真实的 feed 条目，不包含 footer
        if (position < 0 || position >= data.size()) return null;
        return data.get(position);
    }

    /**
     * 给 GridLayoutManager 用的 spanSize
     */
    public int getSpanSizeForPosition(int position) {
        // 如果是 footer，直接占满两列
        if (showFooter && position == getItemCount() - 1) {
            return 2;
        }
        // 防御一下：万一 position 异常，也不要越界
        if (position < 0 || position >= data.size()) {
            return 1;
        }
        FeedItem item = data.get(position);
        return item.getSpanSize(); // 1 或 2
    }


    // ----- Footer 状态控制 -----
    // 显示 “正在加载更多...”
    public void showLoadMoreLoading() {
        showFooter = true;
        footerLoading = true;
        footerError = false;
        notifyFooterChanged();
    }

    // 显示 “加载失败，点击重试”
    public void showLoadMoreError() {
        showFooter = true;
        footerLoading = false;
        footerError = true;
        notifyFooterChanged();
    }

    // 隐藏 footer（加载完成且没有更多错误提示时使用）
    public void hideFooter() {
        if (showFooter) {
            showFooter = false;
            footerLoading = false;
            footerError = false;
            notifyDataSetChanged();
        }
    }

    // 内部小工具：刷新 footer 这一行  把变更通知 RecyclerView
    private void notifyFooterChanged() {
        if (showFooter) {
            notifyItemChanged(getItemCount() - 1);
        } else {
            notifyDataSetChanged();
        }
    }

    // ----- RecyclerView.Adapter 必备实现 -----
    @Override
    public int getItemCount() {
        return data.size() + (showFooter ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        // 如果有 footer 且 position 是最后一个，就用 footer 类型
        if (showFooter && position == getItemCount() - 1) {
            return VIEW_TYPE_FOOTER;
        }

        // 其他位置仍然根据 cardType 来判断
        FeedItem item = data.get(position);
        switch (item.getCardType()) {
            case FeedItem.CARD_TYPE_TEXT:
                return VIEW_TYPE_TEXT;
            case FeedItem.CARD_TYPE_IMAGE_TEXT:
                return VIEW_TYPE_IMAGE_TEXT;
            case FeedItem.CARD_TYPE_VIDEO:
                return VIEW_TYPE_VIDEO;
            default:
                return VIEW_TYPE_TEXT;
        }
    }

    // 按 viewType inflate 对应的 xml
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_TEXT) {
            View v = inflater.inflate(R.layout.item_feed_text, parent, false);
            return new TextViewHolder(v);
        } else if (viewType == VIEW_TYPE_IMAGE_TEXT) {
            View v = inflater.inflate(R.layout.item_feed_image, parent, false);
            return new ImageTextViewHolder(v);
        }else if (viewType == VIEW_TYPE_VIDEO) {
            View v = inflater.inflate(R.layout.item_feed_video, parent, false);
            return new VideoViewHolder(v);
        } else { // VIEW_TYPE_FOOTER
            View v = inflater.inflate(R.layout.item_feed_footer, parent, false);
            return new FooterViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder, int position) {

        // 如果是 footer → bindFooter()
        if (holder instanceof FooterViewHolder) {
            bindFooter((FooterViewHolder) holder);
            return;
        }

        FeedItem item = data.get(position);
        if (item == null) return;

        // 否则根据实际 holder 类型调用 bindText / bindImageText / bindVideo
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
            ((VideoViewHolder) holder).stopDemoPlay();
        }
    }

    // ----- 具体绑定逻辑 -----
    private void bindText(TextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        setupItemClicks(holder.itemView, item, position);
    }

    private void bindImageText(ImageTextViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        // 这里使用占位图，后续可以接 Glide 等真实图片
        holder.ivImage.setImageResource(R.drawable.sample_image);
        setupItemClicks(holder.itemView, item, position);
    }

    private void bindVideo(VideoViewHolder holder, FeedItem item, int position) {
        holder.tvTitle.setText("[视频] " + item.getTitle());
        holder.tvContent.setText(item.getContent());
        holder.startDemoPlay(); // 进入屏幕就“自动播放”
        setupItemClicks(holder.itemView, item, position);
    }

    private void bindFooter(FooterViewHolder holder) {
        holder.tvMessage.setOnClickListener(null);
        if (footerLoading) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.tvMessage.setText("正在加载更多...");
        } else if (footerError) {
            holder.progress.setVisibility(View.GONE);
            holder.tvMessage.setText("加载失败，点击重试");
            holder.tvMessage.setOnClickListener(v -> {
                if (loadMoreRetryListener != null) {
                    loadMoreRetryListener.onRetryLoadMore();
                }
            });
        } else {
            holder.progress.setVisibility(View.GONE);
            holder.tvMessage.setText("");
        }
    }

    // ----- 点击 / 长按逻辑（点击 Toast，长按删卡） -----
    private void setupItemClicks(View itemView, FeedItem item, int position) {
        // 单击Toast
        itemView.setOnClickListener(v ->
                Toast.makeText(context, "点击卡片：" + item.getTitle(),
                        Toast.LENGTH_SHORT).show()
        );

        // 长按删除
        itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("删除卡片")
                    .setMessage("确定要删除这条卡片吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        int pos = position;
                        if (pos >= 0 && pos < data.size()) {
                            data.remove(pos);   // 从数据源移除
                            notifyItemRemoved(pos);  // // 通知 RecyclerView 局部刷新
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    // --- ViewHolder 定义 ---
    class TextViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;

        TextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }

    class ImageTextViewHolder extends RecyclerView.ViewHolder {
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

    // 视频卡片模拟播放 做一个 5s 倒计时来模拟“自动播放”和“停止播放”。
    class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;
        TextView tvCountdown;
        CountDownTimer timer;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvCountdown = itemView.findViewById(R.id.tv_video_countdown);
        }

        void startDemoPlay() {
            cancelTimer();
            final int totalSeconds = 5;
            tvCountdown.setText("播放中 " + totalSeconds + "s");

            timer = new CountDownTimer(totalSeconds * 1000L, 1000L) {
                int remain = totalSeconds;

                @Override
                public void onTick(long millisUntilFinished) {
                    remain--;
                    tvCountdown.setText("播放中 " + remain + "s");
                }

                @Override
                public void onFinish() {
                    tvCountdown.setText("播放结束");
                }
            }.start();
        }

        void stopDemoPlay() {
            cancelTimer();
            tvCountdown.setText("待播放");
        }

        void cancelTimer() {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }
    }

    class FooterViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progress;
        TextView tvMessage;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            progress = itemView.findViewById(R.id.progress);
            tvMessage = itemView.findViewById(R.id.tv_message);
        }
    }
}

