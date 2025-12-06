package com.jessica.feedapp.ui.feed;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.jessica.feedapp.R;
import com.jessica.feedapp.data.FeedCacheManager;
import com.jessica.feedapp.data.FeedRepository;
import com.jessica.feedapp.exposure.ExposureDataProvider;
import com.jessica.feedapp.exposure.ExposureTracker;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.player.FeedVideoManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 信息流主页面：
 * - 首屏加载 + 下拉刷新 + 加载更多
 * - Loading / Error / Empty 覆盖层
 * - 曝光调试面板
 * - 本地缓存（任务包 B：首屏秒开 + 弱网兜底）
 * - 视频播放能力（任务包 C：ExoPlayer，通过 FeedVideoManager 注入到 FeedAdapter）
 */
public class FeedActivity extends AppCompatActivity {

    // ====== 基本 UI ======
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView tvExposureLog;

    // 覆盖层：加载中 / 错误 / 空数据
    private View layoutLoading;
    private View layoutError;
    private View layoutEmpty;
    private Button btnRetryError;
    private Button btnRetryEmpty;

    // ====== 核心组件 ======
    private FeedAdapter adapter;
    private FeedRepository repository;
    private ExposureTracker exposureTracker;
    private FeedCacheManager cacheManager;
    private FeedVideoManager videoManager;

    // ====== 列表 & 网络模拟状态 ======
    private boolean isLoadingMore = false;
    private int loadedCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // ====== 曝光调试面板 ======
    private final List<String> exposureLogs = new ArrayList<>();
    private static final int MAX_EXPOSURE_LOGS = 1;
    private boolean exposureDebugEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide(); // 隐藏默认标题栏
        }

        setContentView(R.layout.activity_feed);

        // 基本 View 绑定
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_feed);
        tvExposureLog = findViewById(R.id.tv_exposure_log);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutError = findViewById(R.id.layout_error);
        layoutEmpty = findViewById(R.id.layout_empty);
        btnRetryError = findViewById(R.id.btn_retry_error);
        btnRetryEmpty = findViewById(R.id.btn_retry_empty);

        // 错误 & 空态重试：重新拉首屏
        View.OnClickListener retryListener = v -> reloadFirstPage();
        btnRetryError.setOnClickListener(retryListener);
        btnRetryEmpty.setOnClickListener(retryListener);

        // 长按标题，开关曝光调试面板
        TextView tvTitle = findViewById(R.id.tv_title);
        if (tvTitle != null) {
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
        }

        // 初始化核心组件
        repository = new FeedRepository();
        cacheManager = new FeedCacheManager(this);
        videoManager = new FeedVideoManager(this);
        // ⚠️ 这里假设你的 FeedAdapter 构造已经改成 (Context, FeedVideoManager)
        adapter = new FeedAdapter(this, videoManager);

        initRecycler();
        initRefresh();
        initExposureTracker();
        showLoadingState();
        loadInitialData();
        // 首屏：优先用缓存“秒开”，再请求最新数据
        startWithCacheThenLoadInitial();
    }

    // ================= 页面状态切换 =================

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();  // 首屏不需要 footer
    }

    private void showErrorState() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();
    }

    private void showContentState() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
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

    // ================= RecyclerView / 刷新 / 曝光 =================

    private void initRecycler() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getSpanSizeForPosition(position); // 单列/双列
            }
        });

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // footer“加载失败，点击重试”时重新触发 loadMore
        adapter.setOnLoadMoreRetryListener(this::retryLoadMore);

        // 滚动到底部自动加载更多
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;

                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();

                // 接近底部 & 当前不在加载中 → 触发 loadMore
                if (!isLoadingMore && visibleCount + firstVisiblePos >= totalCount - 2) {
                    loadMoreData();
                }
            }
        });
    }

    private void initRefresh() {
        // 下拉刷新 → refreshData()
        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    /**
     * 初始化曝光跟踪器：
     * - 用 adapter 提供 itemId
     * - 在回调中更新日志 TextView
     */
    private void initExposureTracker() {
        ExposureDataProvider dataProvider = position -> {
            FeedItem item = adapter.getItemAt(position);
            if (item == null) {
                // 说明这个 position 不是正常的数据条目（比如 footer），直接返回无效 id
                return -1L;
            }
            return item.getId(); // 使用 FeedItem.id 作为曝光唯一标识
        };

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

                    // 把最新一条放在最上面
                    exposureLogs.add(0, msg);
                    if (exposureLogs.size() > MAX_EXPOSURE_LOGS) {
                        exposureLogs.remove(exposureLogs.size() - 1);
                    }

                    // 构建文案：只显示最新的 MAX_EXPOSURE_LOGS 条，不要额外 header
                    StringBuilder sb = new StringBuilder();
                    for (String log : exposureLogs) {
                        sb.append(log).append("\n");
                    }
                    tvExposureLog.setText(sb.toString());

                }
        );
    }

    // ================= 本地缓存 + 首屏秒开 =================

    /**
     * 冷启动逻辑：
     * 1. 先尝试用本地缓存填充列表（有就秒开）
     * 2. 再发起一次首屏请求，成功则刷新并更新缓存
     */
    private void startWithCacheThenLoadInitial() {
        List<FeedItem> cached = cacheManager.loadFeedList();
        if (cached != null && !cached.isEmpty()) {
            adapter.setItems(cached);
            loadedCount = cached.size();
            showContentState();
        } else {
            showLoadingState();
        }

        // 无论是否有缓存，都请求一次最新数据
        loadInitialData();
    }

    // ================= 首屏加载 / 刷新 / 加载更多 =================

    private void loadInitialData() {
        handler.postDelayed(() -> {
            // 用随机数模拟网络成功 / 失败，方便演示首屏状态
            boolean success = random.nextFloat() < 0.85f;  // 85% 概率成功

            if (success) {
                List<FeedItem> items = repository.loadInitial(); // 模拟服务端首屏数据

                if (items == null || items.isEmpty()) {
                    adapter.setItems(null);
                    loadedCount = 0;
                    showEmptyState();
                    cacheManager.clear();
                } else {
                    loadedCount = items.size();
                    adapter.setItems(items);
                    showContentState();
                    // 首屏成功后写入本地缓存
                    cacheManager.saveFeedList(items);
                }
            } else {
                // 网络失败：尝试使用本地缓存兜底
                List<FeedItem> cached = cacheManager.loadFeedList();
                if (cached != null && !cached.isEmpty()) {
                    adapter.setItems(cached);
                    loadedCount = cached.size();
                    showContentState();
                    Toast.makeText(
                            this,
                            "网络异常，已展示上次缓存内容",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    showErrorState();
                    Toast.makeText(
                            this,
                            "加载失败，请点击重试",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        }, 800); // 延迟 800ms 模拟网络
    }

    private void refreshData() {
        swipeRefreshLayout.setRefreshing(true);

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f;
            if (success) {
                List<FeedItem> items = repository.refresh();
                if (items == null || items.isEmpty()) {
                    Toast.makeText(this, "暂无最新内容", Toast.LENGTH_SHORT).show();
                    // 不更新缓存，保留旧数据
                } else {
                    loadedCount = items.size();
                    adapter.setItems(items);
                    showContentState();
                    cacheManager.saveFeedList(items); // 刷新成功更新缓存
                }
            } else {
                Toast.makeText(this, "刷新失败，已保留当前内容", Toast.LENGTH_SHORT).show();
            }

            swipeRefreshLayout.setRefreshing(false);
        }, 800);
    }

    private void loadMoreData() {
        isLoadingMore = true;
        adapter.showLoadMoreLoading(); // footer 显示“正在加载更多…”

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f; // 60% 成功，40% 失败

            if (success) {
                List<FeedItem> more = repository.loadMore(loadedCount);
                loadedCount += more.size();
                adapter.appendItems(more);
                adapter.hideFooter();    // 加载成功：追加数据 & 隐藏 footer

                // （可选）如果你在 FeedAdapter 里提供 getItems()，这里可以顺便更新缓存
                // cacheManager.saveFeedList(adapter.getItems());
            } else {
                adapter.showLoadMoreError(); // 加载失败：footer 显示“加载失败，点击重试”
                Toast.makeText(this, "加载更多失败，请点击重试", Toast.LENGTH_SHORT).show();
            }

            isLoadingMore = false;
        }, 800); // 延迟 800ms 模拟网络
    }

    // footer“点击重试”回调
    private void retryLoadMore() {
        if (!isLoadingMore) {
            loadMoreData();
        }
    }

    // ================= 生命周期：控制播放器 =================

    @Override
    protected void onPause() {
        super.onPause();
        if (videoManager != null) {
            videoManager.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoManager != null) {
            videoManager.release();
        }
    }
}
