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

/**
 * Feed 列表 Adapter
 * - 支持两种 ViewType：纯文本卡 / 图文卡
 * - 点击卡片：Toast 提示
 * - 长按卡片：弹窗确认删除
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_IMAGE_TEXT = 1;

    private final List<FeedItem> data = new ArrayList<>();
    private final LayoutInflater inflater;
    private final Context context;

    public FeedAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
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
        FeedItem item = data.get(position);
        return item.getSpanSize();
    }

    @Override
    public int getItemViewType(int position) {
        FeedItem item = data.get(position);
        if (item.getCardType() == FeedItem.CARD_TYPE_TEXT) {
            return VIEW_TYPE_TEXT;
        } else {
            return VIEW_TYPE_IMAGE_TEXT;
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {
        if (viewType == VIEW_TYPE_TEXT) {
            View view = inflater.inflate(R.layout.item_feed_text, parent, false);
            return new TextViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_feed_image, parent, false);
            return new ImageTextViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position) {
        FeedItem item = data.get(position);
        if (holder instanceof TextViewHolder) {
            bindText((TextViewHolder) holder, item);
        } else if (holder instanceof ImageTextViewHolder) {
            bindImageText((ImageTextViewHolder) holder, item);
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
        // 点击
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
                            data.remove(pos);
                            notifyItemRemoved(pos);
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
