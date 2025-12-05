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
import android.view.View;
import android.widget.Button;


import com.jessica.feedapp.R;
import com.jessica.feedapp.data.FeedRepository;
import com.jessica.feedapp.exposure.ExposureDataProvider;
import com.jessica.feedapp.exposure.ExposureEventType;
import com.jessica.feedapp.exposure.ExposureTracker;
import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;
import android.widget.Toast;
import java.util.Random;
import android.view.View;
import android.widget.Button;


public class FeedActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout; // 下拉刷新控件
    private RecyclerView recyclerView; // 信息流列表
    private TextView tvExposureLog;  // 曝光日志显示区

    // ===== 页面状态相关 View =====
    private View layoutLoading;
    private View layoutError;
    private View layoutEmpty;
    private Button btnRetryError;
    private Button btnRetryEmpty;

    private FeedAdapter adapter; // 适配器
    private FeedRepository repository; // 数据源

    private ExposureTracker exposureTracker;

    private boolean isLoadingMore = false; // 控制 loadMore
    private int loadedCount = 0; // 控制 loadMore

    private final Handler handler = new Handler(Looper.getMainLooper()); // 模拟网络延时
    private final Random random = new Random(); // 控制“请求成功/失败概率”

    // ===== 曝光调试面板相关 =====
    private final java.util.List<String> exposureLogs = new ArrayList<>();
    private static final int MAX_EXPOSURE_LOGS = 2;
    private boolean exposureDebugEnabled = true;

    private void retryLoadMore() {
        if (!isLoadingMore) {
            loadMoreData();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide(); // 隐藏默认的标题栏 ActionBar
        }

        setContentView(R.layout.activity_feed);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_feed);
        tvExposureLog = findViewById(R.id.tv_exposure_log); // TextView
        layoutLoading = findViewById(R.id.layout_loading);
        layoutError = findViewById(R.id.layout_error);
        layoutEmpty = findViewById(R.id.layout_empty);
        btnRetryError = findViewById(R.id.btn_retry_error);
        btnRetryEmpty = findViewById(R.id.btn_retry_empty);

        // 重试按钮：走首屏重新加载
        View.OnClickListener retryListener = v -> reloadFirstPage();
        btnRetryError.setOnClickListener(retryListener);
        btnRetryEmpty.setOnClickListener(retryListener);

        // 长按标题，开关曝光调试面板
        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setOnLongClickListener(v -> {
            exposureDebugEnabled = !exposureDebugEnabled;
            if (exposureDebugEnabled) {
                tvExposureLog.setVisibility(View.VISIBLE);
                Toast.makeText(
                        FeedActivity.this,
                        "曝光调试：已开启（长按标题可关闭）",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                tvExposureLog.setVisibility(View.GONE);
                Toast.makeText(
                        FeedActivity.this,
                        "曝光调试：已关闭（长按标题可重新打开）",
                        Toast.LENGTH_SHORT
                ).show();
            }
            return true;
        });

        repository = new FeedRepository();
        adapter = new FeedAdapter(this);

        initRecycler();
        initRefresh();
        initExposureTracker();

        // 首屏：先显示 Loading，再去加载数据
        showLoadingState();
        loadInitialData();
    }

    // ===== 页面状态切换 =====

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();  // 首屏不需要 footer
    }

    private void showContentState() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    private void showErrorState() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();
    }

    private void showEmptyState() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();
    }

    private void reloadFirstPage() {
        showLoadingState();
        loadInitialData();
    }

    private void initRecycler() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getSpanSizeForPosition(position); // 决定单/双列
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

                    // 如果关闭了调试开关，就不更新 UI（但仍然在控制台可以打 log）
                    if (!exposureDebugEnabled || tvExposureLog == null) {
                        return;
                    }

                    // 把最新一条放在最上面
                    exposureLogs.add(0, msg);
                    if (exposureLogs.size() > MAX_EXPOSURE_LOGS) {
                        exposureLogs.remove(exposureLogs.size() - 1);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("曝光日志（最新在上）：\n");
                    for (String log : exposureLogs) {
                        sb.append(log).append("\n");
                    }
                    tvExposureLog.setText(sb.toString());
                }
        );
    }

    private void loadInitialData() {
        handler.postDelayed(() -> {
            // 用随机数模拟网络成功 / 失败，方便演示首屏状态
            boolean success = random.nextFloat() < 0.85f;  // 85% 概率成功

            if (success) {
                List<FeedItem> items = repository.loadInitial(); // 模拟服务端首屏数据
                loadedCount = items.size();
                adapter.setItems(items);

                if (items == null || items.isEmpty()) {
                    showEmptyState();
                } else {
                    showContentState();
                }
            } else {
                showErrorState();
                Toast.makeText(this, "加载失败，请点击重试", Toast.LENGTH_SHORT).show();
            }
        }, 600);
    }

    private void refreshData() {
        swipeRefreshLayout.setRefreshing(true);

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f;
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
        }, 800); // 延迟 800ms 模拟网络
    }

}
