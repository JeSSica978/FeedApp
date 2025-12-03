package com.jessica.feedapp.model;

/**
 * 单条 Feed 卡片的数据结构：
 * - 支持文本 / 图文 / 视频 3 种类型（cardType）
 * - 支持单列 / 双列排版（spanSize）
 * 这些字段基本不包含业务逻辑，只是“数据描述”。
 */

public class FeedItem {

    // 卡片类型
    public static final int CARD_TYPE_TEXT = 0;       // 纯文本
    public static final int CARD_TYPE_IMAGE_TEXT = 1; // 图文
    public static final int CARD_TYPE_VIDEO = 2;  //视频

    // 列宽（span）
    public static final int SPAN_SINGLE = 1; // 占一列
    public static final int SPAN_DOUBLE = 2; // 占两列（整行）

    private final long id; // 唯一 ID，用来做曝光统计、去重等
    private final String title; //文案
    private final String content; //文案
    private final String imageUrl; // 图片 URL，目前用空字符串占位，后面可接网络图
    private final int cardType; //卡片类型（文本 / 图文 / 视频
    private final int spanSize; //占几列（1 = 单列，2 = 双列）

    public FeedItem(long id,
                    String title,
                    String content,
                    String imageUrl,
                    int cardType,
                    int spanSize) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.cardType = cardType;
        this.spanSize = spanSize;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getCardType() {
        return cardType;
    }

    public int getSpanSize() {
        return spanSize;
    }
}
