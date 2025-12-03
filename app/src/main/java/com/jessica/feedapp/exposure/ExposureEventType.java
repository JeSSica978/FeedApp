package com.jessica.feedapp.exposure;

/**
 * EventType: 这一帧发生的事件（状态变化）
 * 对外回调使用的曝光事件类型
 */
public enum ExposureEventType {
    ENTER,          // 卡片露出
    OVER_HALF,      // 卡片露出超过 50%
    FULL_VISIBLE,   // 卡片完整露出
    DISAPPEAR       // 卡片消失
}
