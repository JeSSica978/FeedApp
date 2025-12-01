package com.jessica.feedapp.data;

import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 模拟服务端的 Feed 数据仓库
 * - 生成不同类型、不同列宽的卡片
 */
public class FeedRepository {

    private final Random random = new Random();

    // 首屏数据
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

            // 每隔一条切换卡片类型：0 文本卡，1 图文卡
            int cardType = (i % 2 == 0)
                    ? FeedItem.CARD_TYPE_TEXT
                    : FeedItem.CARD_TYPE_IMAGE_TEXT;

            // 随机单列 / 双列
            int span = (random.nextBoolean())
                    ? FeedItem.SPAN_SINGLE
                    : FeedItem.SPAN_DOUBLE;

            String title = "标题 " + id;
            String content = "这里是内容摘要（id=" + id + "），用于展示多行文本效果。";
            // 暂时不用真实 URL，用空字符串占位
            String imageUrl = "";

            list.add(new FeedItem(id, title, content, imageUrl, cardType, span));
        }
        return list;
    }
}
