package com.jessica.feedapp.ui.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;
import android.widget.ProgressBar;


/**
 * Feed 列表 Adapter
 * - 支持两种 ViewType：纯文本卡 / 图文卡
 * - 点击卡片：Toast 提示
 * - 长按卡片：弹窗确认删除
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_IMAGE_TEXT = 1;

    // footer 的类型
    private static final int VIEW_TYPE_FOOTER = 100;

    private final List<FeedItem> data = new ArrayList<>();

    // footer 的状态
    private boolean showFooter = false;
    private boolean footerLoading = false;
    private boolean footerError = false;
    private final LayoutInflater inflater;
    private final Context context;

    // 给“点击重试”用的回调接口
    public interface OnLoadMoreRetryListener {
        void onRetryLoadMore();
    }
    private OnLoadMoreRetryListener loadMoreRetryListener;

    public void setOnLoadMoreRetryListener(OnLoadMoreRetryListener listener) {
        this.loadMoreRetryListener = listener;
    }

    public FeedAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

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

    // 内部小工具：刷新 footer 这一行
    private void notifyFooterChanged() {
        if (showFooter) {
            notifyItemChanged(getItemCount() - 1);
        } else {
            notifyDataSetChanged();
        }
    }

    public void setItems(List<FeedItem> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    public void appendItems(List<FeedItem> items) {
        int start = data.size();
        data.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    public FeedItem getItemAt(int position) {
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

        if (position < 0 || position >= data.size()) {
            return 1;
        }
        FeedItem item = data.get(position);
        return item.getSpanSize(); // 1 或 2
    }


    @Override
    public int getItemViewType(int position) {
        // 如果有 footer 且 position 是最后一个，就用 footer 类型
        if (showFooter && position == getItemCount() - 1) {
            return VIEW_TYPE_FOOTER;
        }

        // 其他位置仍然根据 cardType 来判断
        FeedItem item = data.get(position);
        return item.getCardType() == FeedItem.CARD_TYPE_TEXT
                ? VIEW_TYPE_TEXT
                : FeedItem.CARD_TYPE_IMAGE_TEXT;
    }

    @Override
    public int getItemCount() {
        return data.size() + (showFooter ? 1 : 0);
    }

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
        } else { // VIEW_TYPE_FOOTER
            View v = inflater.inflate(R.layout.item_feed_footer, parent, false);
            return new FooterViewHolder(v);
        }
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            bindFooter((FooterViewHolder) holder);
            return;
        }

        FeedItem item = data.get(position);
        if (holder instanceof TextViewHolder) {
            bindText((TextViewHolder) holder, item);
        } else if (holder instanceof ImageTextViewHolder) {
            bindImageText((ImageTextViewHolder) holder, item);
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


    private void bindText(TextViewHolder holder, FeedItem item) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        setupItemClicks(holder.itemView, item, holder.getAdapterPosition());
    }

    private void bindImageText(ImageTextViewHolder holder, FeedItem item) {
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        // 暂时用本地占位图，后面可以接网络图片加载库（如 Glide）
        holder.ivImage.setImageResource(R.drawable.sample_image);
        setupItemClicks(holder.itemView, item, holder.getAdapterPosition());
    }

    private void setupItemClicks(View itemView, FeedItem item, int position) {
        // 单机Toast
        itemView.setOnClickListener(v -> {
            android.widget.Toast.makeText(
                    context,
                    "点击卡片：" + item.getTitle(),
                    android.widget.Toast.LENGTH_SHORT
            ).show();
        });

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
}
