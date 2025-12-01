package com.jessica.feedapp.ui.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jessica.feedapp.R;
import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化版 Feed 列表 Adapter
 * - 显示标题+摘要
 * - 支持长按删除卡片
 */
public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.FeedViewHolder> {

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

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_feed_text, parent, false);
        return new FeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        FeedItem item = data.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());

        // 点击事件（这里简单 Toast，可后续扩展）
        holder.itemView.setOnClickListener(v -> {
            android.widget.Toast.makeText(
                    context,
                    "点击卡片：" + item.getTitle(),
                    android.widget.Toast.LENGTH_SHORT
            ).show();
        });

        // 长按删除
        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("删除卡片")
                    .setMessage("确定要删除这条卡片吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            data.remove(pos);
                            notifyItemRemoved(pos);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class FeedViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvContent;

        FeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }
}
