package com.jessica.feedapp.exposure;

/**
 * 内部使用的曝光状态（当前这一帧卡片的可见程度）
 */
public enum ExposureState {
    NONE,   // 不可见
    ENTER,  // 露出但 < 50%
    HALF,   // 露出 >= 50% 且 < 完全
    FULL    // 基本完全露出
}
