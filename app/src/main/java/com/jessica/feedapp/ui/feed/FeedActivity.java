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
import com.jessica.feedapp.exposure.ExposureEventType;
import com.jessica.feedapp.exposure.ExposureTracker;
import com.jessica.feedapp.model.FeedItem;
import com.jessica.feedapp.player.FeedVideoManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FeedActivity extends AppCompatActivity {

    // ===== 基本 UI =====
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView tvExposureLog;

    // 覆盖层
    private View layoutLoading;
    private View layoutError;
    private View layoutEmpty;
    private Button btnRetryError;
    private Button btnRetryEmpty;

    // ===== 核心组件 =====
    private FeedAdapter adapter;
    private FeedRepository repository;
    private ExposureTracker exposureTracker;
    private FeedCacheManager cacheManager;
    private FeedVideoManager videoManager;

    // ===== 列表状态 =====
    private boolean isLoadingMore = false;
    private int loadedCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // ===== 曝光调试（只保留两条） =====
    private final List<String> exposureLogs = new ArrayList<>();
    private static final int MAX_EXPOSURE_LOGS = 2;
    private boolean exposureDebugEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_feed);

        initViews();
        initCoreComponents();
        initRecycler();
        initRefresh();
        initExposureTracker();

        // 冷启动：先用缓存“秒开”，再请求最新首屏
        startWithCacheThenLoadInitial();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_feed);
        tvExposureLog = findViewById(R.id.tv_exposure_log);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutError = findViewById(R.id.layout_error);
        layoutEmpty = findViewById(R.id.layout_empty);
        btnRetryError = findViewById(R.id.btn_retry_error);
        btnRetryEmpty = findViewById(R.id.btn_retry_empty);

        // 错误 / 空态重试
        View.OnClickListener retryListener = v -> reloadFirstPage();
        btnRetryError.setOnClickListener(retryListener);
        btnRetryEmpty.setOnClickListener(retryListener);

        // 长按标题开关曝光调试
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
    }

    private void initCoreComponents() {
        repository = new FeedRepository();
        cacheManager = new FeedCacheManager(this);
        videoManager = new FeedVideoManager(this);
        adapter = new FeedAdapter(this, videoManager);
    }

    // ========= 页面状态 =========

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        adapter.hideFooter();
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

    // ========= Recycler / 刷新 =========

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

        adapter.setOnLoadMoreRetryListener(this::retryLoadMore);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(
                    @NonNull RecyclerView rv,
                    int newState
            ) {
                super.onScrollStateChanged(rv, newState);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // 滚动停止 → 自动播放居中视频
                    autoPlayCenterVideo();
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING
                        || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    // 正在滚动 → 暂停当前视频，避免边滚边放
                    videoManager.pause();
                }
            }

            @Override
            public void onScrolled(
                    @NonNull RecyclerView rv,
                    int dx,
                    int dy
            ) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;

                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();

                if (!isLoadingMore && visibleCount + firstVisiblePos >= totalCount - 2) {
                    loadMoreData();
                }
            }
        });
    }

    private void initRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            videoManager.pause();  // 下拉刷新时先暂停当前视频
            refreshData();
        });
    }

    private void initExposureTracker() {
        // 把 position 转为 itemId
        ExposureDataProvider dataProvider = position -> {
            FeedItem item = adapter.getItemAt(position);
            if (item == null) return -1L;
            return item.getId();
        };

        exposureTracker = new ExposureTracker(
                recyclerView,
                dataProvider,
                (itemId, eventType, visibleRatio) -> {
                    handleExposureLog(itemId, eventType, visibleRatio);
                    handleVideoPauseByDisappear(itemId, eventType);
                }
        );
    }

    // ========= 曝光日志（只保留最新两条） =========

    private void handleExposureLog(long itemId,
                                   ExposureEventType eventType,
                                   float visibleRatio) {
        if (!exposureDebugEnabled || tvExposureLog == null) {
            return;
        }

        String msg = "itemId=" + itemId
                + " | event=" + eventType
                + " | ratio=" + String.format("%.2f", visibleRatio);

        exposureLogs.add(0, msg);
        if (exposureLogs.size() > MAX_EXPOSURE_LOGS) {
            exposureLogs.remove(exposureLogs.size() - 1);
        }

        StringBuilder sb = new StringBuilder();
        for (String log : exposureLogs) {
            sb.append(log).append("\n");
        }
        tvExposureLog.setText(sb.toString());
    }

    // ========= 基于曝光：DISAPPEAR 时暂停，防止“鬼畜播放” =========

    private void handleVideoPauseByDisappear(long itemId,
                                             ExposureEventType eventType) {
        if (eventType == ExposureEventType.DISAPPEAR) {
            videoManager.pauseIfMatching(itemId);
        }
    }

    // ========= 核心：滚动停止后，自动播放最居中的视频卡 =========

    private void autoPlayCenterVideo() {
        if (recyclerView == null || adapter == null) return;

        int childCount = recyclerView.getChildCount();
        if (childCount == 0) return;

        int rvHeight = recyclerView.getHeight();
        if (rvHeight <= 0) return;

        int rvCenterY = rvHeight / 2;

        float bestDistance = Float.MAX_VALUE;
        FeedAdapter.VideoViewHolder targetVH = null;
        FeedItem targetItem = null;

        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (!(vh instanceof FeedAdapter.VideoViewHolder)) {
                continue;
            }

            int pos = vh.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) continue;

            FeedItem item = adapter.getItemAt(pos);
            if (item == null || item.getCardType() != FeedItem.CARD_TYPE_VIDEO) {
                continue;
            }

            int top = child.getTop();
            int bottom = child.getBottom();
            int centerY = (top + bottom) / 2;
            float distance = Math.abs(centerY - rvCenterY);

            if (distance < bestDistance) {
                bestDistance = distance;
                targetVH = (FeedAdapter.VideoViewHolder) vh;
                targetItem = item;
            }
        }

        if (targetVH != null && targetItem != null) {
            // 你可以根据需要设置一个阈值：比如只有当 distance < rvHeight * 0.3 才自动播
            // 这里简单起见，只要当前屏幕内有视频，就播放距离中心最近的那一个
            videoManager.bindAndPlay(targetVH.playerView, targetItem);
        }
    }

    /**
     * 根据 itemId 在 adapter 中查找 position（O(n)，Demo 规模足够）
     */
    private int findAdapterPositionByItemId(long itemId) {
        int count = adapter.getItemCount();
        for (int i = 0; i < count; i++) {
            FeedItem item = adapter.getItemAt(i);
            if (item != null && item.getId() == itemId) {
                return i;
            }
        }
        return -1;
    }

    // ========= 本地缓存 + 首屏秒开 =========

    private void startWithCacheThenLoadInitial() {
        List<FeedItem> cached = cacheManager.loadFeedList();
        if (cached != null && !cached.isEmpty()) {
            adapter.setItems(cached);
            loadedCount = cached.size();
            showContentState();

            // 如果首屏缓存中刚好有视频，直接自动播居中视频
            recyclerView.post(this::autoPlayCenterVideo);
        } else {
            showLoadingState();
        }
        loadInitialData();
    }

    // ========= 首屏加载 / 刷新 / 加载更多 =========

    private void loadInitialData() {
        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.85f;

            if (success) {
                List<FeedItem> items = repository.loadInitial();
                if (items == null || items.isEmpty()) {
                    adapter.setItems(null);
                    loadedCount = 0;
                    showEmptyState();
                    cacheManager.clear();
                } else {
                    loadedCount = items.size();
                    adapter.setItems(items);
                    showContentState();
                    cacheManager.saveFeedList(items);

                    // 首屏数据加载完成后，尝试自动播放居中视频
                    recyclerView.post(this::autoPlayCenterVideo);
                }
            } else {
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

                    recyclerView.post(this::autoPlayCenterVideo);
                } else {
                    showErrorState();
                    Toast.makeText(
                            this,
                            "加载失败，请点击重试",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        }, 800);
    }

    private void refreshData() {
        swipeRefreshLayout.setRefreshing(true);

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f;
            if (success) {
                List<FeedItem> items = repository.refresh();
                if (items == null || items.isEmpty()) {
                    Toast.makeText(this, "暂无最新内容", Toast.LENGTH_SHORT).show();
                } else {
                    loadedCount = items.size();
                    adapter.setItems(items);
                    showContentState();
                    cacheManager.saveFeedList(items);

                    recyclerView.post(this::autoPlayCenterVideo);
                }
            } else {
                Toast.makeText(this, "刷新失败，已保留当前内容", Toast.LENGTH_SHORT).show();
            }

            swipeRefreshLayout.setRefreshing(false);
        }, 800);
    }

    private void loadMoreData() {
        isLoadingMore = true;
        adapter.showLoadMoreLoading();

        handler.postDelayed(() -> {
            boolean success = random.nextFloat() < 0.6f;

            if (success) {
                List<FeedItem> more = repository.loadMore(loadedCount);
                loadedCount += more.size();
                adapter.appendItems(more);
                adapter.hideFooter();
                // 如有需要可以在此更新缓存
            } else {
                adapter.showLoadMoreError();
                Toast.makeText(this, "加载更多失败，请点击重试", Toast.LENGTH_SHORT).show();
            }

            isLoadingMore = false;
        }, 800);
    }

    private void retryLoadMore() {
        if (!isLoadingMore) {
            loadMoreData();
        }
    }

    // ========= 生命周期：控制播放器 =========

    @Override
    protected void onPause() {
        super.onPause();
        if (videoManager != null) {
            videoManager.pause();
        }
    }

    //
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoManager != null) {
            videoManager.release();
        }
    }
}
