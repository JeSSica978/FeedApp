package com.jessica.feedapp.ui.feed;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.jessica.feedapp.R;
import com.jessica.feedapp.data.FeedRepository;
import com.jessica.feedapp.model.FeedItem;

import java.util.List;

/**
 * Feed 主页面
 * - 下拉刷新
 * - 滑动到底部加载更多
 */
public class FeedActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;

    private FeedAdapter adapter;
    private FeedRepository repository;

    private boolean isLoadingMore = false;
    private int loadedCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_feed);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_feed);

        repository = new FeedRepository();
        adapter = new FeedAdapter(this);

        initRecycler();
        initRefresh();

        loadInitialData();
    }

    private void initRecycler() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // 简单的“滑到底部加载更多”监听
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(
                    @NonNull RecyclerView rv,
                    int dx,
                    int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return; // 只关心向下滑动

                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();

                // 滑到接近底部时触发加载更多
                if (!isLoadingMore
                        && visibleCount + firstVisiblePos >= totalCount - 2) {
                    loadMoreData();
                }
            }
        });
    }

    private void initRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    private void loadInitialData() {
        swipeRefreshLayout.setRefreshing(true);
        handler.postDelayed(() -> {
            List<FeedItem> items = repository.loadInitial();
            loadedCount = items.size();
            adapter.setItems(items);
            swipeRefreshLayout.setRefreshing(false);
        }, 500); // 模拟网络延迟
    }

    private void refreshData() {
        handler.postDelayed(() -> {
            List<FeedItem> items = repository.refresh();
            loadedCount = items.size();
            adapter.setItems(items);
            swipeRefreshLayout.setRefreshing(false);
        }, 800);
    }

    private void loadMoreData() {
        isLoadingMore = true;
        handler.postDelayed(() -> {
            List<FeedItem> more = repository.loadMore(loadedCount);
            loadedCount += more.size();
            adapter.appendItems(more);
            isLoadingMore = false;
        }, 1000);
    }
}
