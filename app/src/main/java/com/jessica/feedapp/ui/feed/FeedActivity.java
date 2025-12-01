package com.jessica.feedapp.ui.feed;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.jessica.feedapp.R;
import com.jessica.feedapp.data.FeedRepository;
import com.jessica.feedapp.exposure.ExposureDataProvider;
import com.jessica.feedapp.exposure.ExposureEventType;
import com.jessica.feedapp.exposure.ExposureTracker;
import com.jessica.feedapp.model.FeedItem;

import java.util.List;

public class FeedActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView tvExposureLog;   // 可选，用于 UI 显示曝光日志

    private FeedAdapter adapter;
    private FeedRepository repository;

    private ExposureTracker exposureTracker;

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
        tvExposureLog = findViewById(R.id.tv_exposure_log); // 如果你没加这个 TextView，可以删掉这一行和后面的使用

        repository = new FeedRepository();
        adapter = new FeedAdapter(this);

        initRecycler();
        initRefresh();
        initExposureTracker();

        loadInitialData();
    }

    private void initRecycler() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getSpanSizeForPosition(position);
            }
        });

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(
                    @NonNull RecyclerView rv,
                    int dx,
                    int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;

                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();

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

    /**
     * 初始化曝光跟踪器：
     * - 用 adapter 提供 itemId
     * - 在回调中简单更新日志 TextView
     */
    private void initExposureTracker() {
        ExposureDataProvider dataProvider = position -> {
            FeedItem item = adapter.getItemAt(position);
            return item.getId();
        };

        exposureTracker = new ExposureTracker(
                recyclerView,
                dataProvider,
                (itemId, eventType, visibleRatio) -> {
                    String msg = "itemId=" + itemId
                            + " | event=" + eventType
                            + " | ratio=" + String.format("%.2f", visibleRatio);
                    // UI 上显示最近一次事件（可选）
                    if (tvExposureLog != null) {
                        tvExposureLog.setText("曝光日志：" + msg);
                    }
                    // 如果需要，这里也可以做打点上报
                    // e.g. sendExposureEventToServer(itemId, eventType, visibleRatio);
                }
        );
    }

    private void loadInitialData() {
        swipeRefreshLayout.setRefreshing(true);
        handler.postDelayed(() -> {
            List<FeedItem> items = repository.loadInitial();
            loadedCount = items.size();
            adapter.setItems(items);
            swipeRefreshLayout.setRefreshing(false);
        }, 500);
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
