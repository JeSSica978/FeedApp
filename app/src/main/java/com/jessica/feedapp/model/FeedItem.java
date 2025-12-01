package com.jessica.feedapp.model;

/**
 * 单条 Feed 卡片的数据结构（简化版）
 */
public class FeedItem {

    private final long id;
    private final String title;
    private final String content;

    public FeedItem(long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
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
}
