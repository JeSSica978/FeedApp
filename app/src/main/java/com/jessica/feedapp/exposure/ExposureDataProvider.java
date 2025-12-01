package com.jessica.feedapp.exposure;

/**
 * 曝光数据提供接口
 * - 用 position 换取 item 的唯一 id
 */
public interface ExposureDataProvider {
    long getItemIdForPosition(int position);
}
