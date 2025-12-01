package com.jessica.feedapp.data;

import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟服务端的 Feed 数据仓库
 * 后面可以替换成真实网络请求
 */
public class FeedRepository {

    // 加载首屏数据
    public List<FeedItem> loadInitial() {
        return generateItems(0, 20);
    }

    // 下拉刷新
    public List<FeedItem> refresh() {
        return generateItems(1000, 20);
    }

    // 加载更多
    public List<FeedItem> loadMore(int offset) {
        return generateItems(offset, 10);
    }

    private List<FeedItem> generateItems(int startId, int count) {
        List<FeedItem> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = startId + i;
            String title = "标题 " + id;
            String content = "这里是 Feed 内容摘要，id = " + id;
            list.add(new FeedItem(id, title, content));
        }
        return list;
    }
}
