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
import android.widget.Toast;
import java.util.Random;


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

    private void retryLoadMore() {
        if (!isLoadingMore) {
            loadMoreData();
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_feed);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_feed);
        tvExposureLog = findViewById(R.id.tv_exposure_log); // TextView

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

        // footer“加载失败，点击重试”时重新触发 loadMore
        adapter.setOnLoadMoreRetryListener(this::retryLoadMore);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;

                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();

                // 接近底部 & 当前不在加载中 → 触发 loadMore
                if (!isLoadingMore
                        && visibleCount + firstVisiblePos >= totalCount - 2) {
                    loadMoreData();
                }
            }
        });
    }

    private void initRefresh() {
        // 当用户下拉并松手时，会回调到 refreshData()
        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    /**
     * 初始化曝光跟踪器：
     * - 用 adapter 提供 itemId
     * - 在回调中简单更新日志 TextView
     */
    private void initExposureTracker() {
        tvExposureLog = findViewById(R.id.tv_exposure_log);

        ExposureDataProvider dataProvider = position -> {
            FeedItem item = adapter.getItemAt(position);
            if (item == null) {
                // 说明这个 position 不是正常的数据条目（比如 footer），直接返回无效 id
                return -1L;
            }
            return item.getId(); // 使用 FeedItem.id 作为曝光唯一标识
        };

        // 告诉曝光系统：position 对应的是哪个 itemId
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
            List<FeedItem> items = repository.loadInitial(); // <-- 从仓库拿数据（模拟服务端 首屏数据）
            loadedCount = items.size();
            adapter.setItems(items);                         // <-- 把数据给 Adapter
            swipeRefreshLayout.setRefreshing(false);
        }, 500);
    }

    private void refreshData() {
        swipeRefreshLayout.setRefreshing(true);

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.8f;
            if (success) {
                List<FeedItem> items = repository.refresh();
                loadedCount = items.size();
                adapter.setItems(items);
            } else {
                Toast.makeText(this, "刷新失败，请稍后重试", Toast.LENGTH_SHORT).show();
            }

            swipeRefreshLayout.setRefreshing(false);
        }, 800);
    }


    private final Random random = new Random(); // 在类成员变量区加这个

    private void loadMoreData() {
        isLoadingMore = true;
        adapter.showLoadMoreLoading();   // 开始 loadMore：footer 显示“正在加载更多…”

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f; // 60% 成功，40% 失败

            if (success) {
                List<FeedItem> more = repository.loadMore(loadedCount);
                loadedCount += more.size();
                adapter.appendItems(more);
                adapter.hideFooter();    // 加载成功：追加数据 & 隐藏 footer
            } else {
                adapter.showLoadMoreError(); // 加载失败：footer 显示“加载失败，点击重试”
                Toast.makeText(this, "加载更多失败，请点击重试", Toast.LENGTH_SHORT).show();
            }

            isLoadingMore = false;
        }, 800);
    }

}
