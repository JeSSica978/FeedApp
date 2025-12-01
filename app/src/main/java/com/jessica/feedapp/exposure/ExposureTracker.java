package com.jessica.feedapp.exposure;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 卡片曝光跟踪器：
 * - 监听 RecyclerView 滚动
 * - 按可见高度比例计算每个 item 的曝光状态
 * - 当状态发生变化时，触发 4 种事件：
 *   - ENTER       （卡片露出）
 *   - OVER_HALF   （卡片露出超过 50%）
 *   - FULL_VISIBLE（卡片完整露出）
 *   - DISAPPEAR   （卡片消失）
 */
public class ExposureTracker {

    private static final String TAG = "ExposureTracker";

    /**
     * 事件回调接口
     */
    public interface ExposureListener {
        /**
         * @param itemId       卡片唯一 id（你传进来的）
         * @param eventType    事件类型（露出 / >50% / 完整 / 消失）
         * @param visibleRatio 当前这一帧的可见比例 [0,1]
         */
        void onExposureEvent(long itemId, ExposureEventType eventType, float visibleRatio);
    }

    private final RecyclerView recyclerView;
    private final ExposureDataProvider dataProvider;
    private final ExposureListener listener;

    // 记录每个 item 当前的曝光状态
    private final Map<Long, ExposureState> stateMap = new HashMap<>();

    public ExposureTracker(@NonNull RecyclerView recyclerView,
                           @NonNull ExposureDataProvider dataProvider,
                           @NonNull ExposureListener listener) {
        this.recyclerView = recyclerView;
        this.dataProvider = dataProvider;
        this.listener = listener;

        // 监听滚动
        this.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                checkExposure();
            }
        });

        // 首次布局后检测一次
        this.recyclerView.post(this::checkExposure);
    }

    /**
     * 遍历当前可见的 child，计算可见比例并触发状态机
     */
    private void checkExposure() {
        int childCount = recyclerView.getChildCount();
        int rvHeight = recyclerView.getHeight();

        if (childCount == 0 || rvHeight == 0) {
            return;
        }

        // 标记这一次遍历中“出现过”的 itemId
        Set<Long> seenThisPass = new HashSet<>();

        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            int position = recyclerView.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;

            long itemId = dataProvider.getItemIdForPosition(position);
            seenThisPass.add(itemId);

            int childTop = child.getTop();
            int childBottom = child.getBottom();
            int childHeight = child.getHeight();

            // 计算可见部分的 top / bottom（相对于 RecyclerView）
            int visibleTop = Math.max(childTop, 0);
            int visibleBottom = Math.min(childBottom, rvHeight);
            int visibleHeight = Math.max(0, visibleBottom - visibleTop);

            float visibleRatio = (childHeight <= 0)
                    ? 0f
                    : (visibleHeight * 1f / childHeight);

            // ExposureState prevState = stateMap.getOrDefault(itemId, ExposureState.NONE); API24及以上才支持
            ExposureState prevState = stateMap.containsKey(itemId)
                    ? stateMap.get(itemId)
                    : ExposureState.NONE; // API23支持写法
            ExposureState nowState;

            if (visibleRatio <= 0f) {
                nowState = ExposureState.NONE;
            } else if (visibleRatio >= 0.99f) {
                nowState = ExposureState.FULL;
            } else if (visibleRatio >= 0.5f) {
                nowState = ExposureState.HALF;
            } else {
                nowState = ExposureState.ENTER;
            }

            // 根据状态变化触发对应事件
            handleStateTransition(itemId, prevState, nowState, visibleRatio);

            // 记录最新状态
            stateMap.put(itemId, nowState);
        }

        // 对于这次没遍历到、但上一次不是 NONE 的 item，视为“消失”
        // 避免 ConcurrentModification：先拷贝一份 key 集合
        Set<Long> allIds = new HashSet<>(stateMap.keySet());
        for (Long id : allIds) {
            if (!seenThisPass.contains(id)) {
                ExposureState prev = stateMap.get(id);
                if (prev != null && prev != ExposureState.NONE) {
                    fireEvent(id, ExposureEventType.DISAPPEAR, 0f);
                    stateMap.put(id, ExposureState.NONE);
                }
            }
        }
    }

    /**
     * 状态机：从 prevState -> nowState 时触发哪些事件
     */
    private void handleStateTransition(long itemId,
                                       ExposureState prevState,
                                       ExposureState nowState,
                                       float visibleRatio) {

        // 1）NONE -> 非 NONE：卡片露出
        if (prevState == ExposureState.NONE && nowState != ExposureState.NONE) {
            fireEvent(itemId, ExposureEventType.ENTER, visibleRatio);
        }

        // 2）第一次达到 HALF 或以上：露出超过 50%
        if (prevState.ordinal() < ExposureState.HALF.ordinal()
                && nowState.ordinal() >= ExposureState.HALF.ordinal()
                && nowState != ExposureState.NONE) {
            fireEvent(itemId, ExposureEventType.OVER_HALF, visibleRatio);
        }

        // 3）第一次达到 FULL：卡片完整露出
        if (prevState.ordinal() < ExposureState.FULL.ordinal()
                && nowState == ExposureState.FULL) {
            fireEvent(itemId, ExposureEventType.FULL_VISIBLE, visibleRatio);
        }

        // 4）从非 NONE -> NONE：卡片消失（本轮遍历中，如果可见高度变成 0）
        if (prevState != ExposureState.NONE && nowState == ExposureState.NONE) {
            fireEvent(itemId, ExposureEventType.DISAPPEAR, visibleRatio);
        }
    }

    private void fireEvent(long itemId,
                           ExposureEventType type,
                           float visibleRatio) {
        Log.d(TAG, "itemId=" + itemId + ", event=" + type
                + ", visibleRatio=" + visibleRatio);
        listener.onExposureEvent(itemId, type, visibleRatio);
    }
}
