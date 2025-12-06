package com.jessica.feedapp.data;

import com.jessica.feedapp.model.FeedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 模拟服务端的 Feed 数据仓库
 * - 生成不同类型、不同列宽的卡片
 * 当前所有“来自服务器”的列表数据，都从这里产生。
 * UI 层只知道调用 loadInitial/refresh/loadMore，并不知道数据是本地造的。
 */
public class FeedRepository {

    private final Random random = new Random();

    // 首屏数据
    public List<FeedItem> loadInitial() {
        return generateItems(0, 20);
    }

    // 下拉刷新数据
    public List<FeedItem> refresh() {
        return generateItems(1000, 20);
    }

    // 加载更多，从 offset 开始往后造 10 条
    public List<FeedItem> loadMore(int offset) {
        return generateItems(offset, 10);
    }

    // 真正造数据 + 决定卡片类型/列宽
    private List<FeedItem> generateItems(int startId, int count) {
        List<FeedItem> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = startId + i;

            // 每 5 条来一条视频卡片，其余保持原来的文本/图文逻辑
            int cardType;
            if (i % 5 == 0) {
                cardType = FeedItem.CARD_TYPE_VIDEO; // 视频卡片
            } else if (i % 2 == 0) { // 每隔一条切换卡片类型：0 文本卡，1 图文卡
                cardType = FeedItem.CARD_TYPE_TEXT;
            } else {
                cardType = FeedItem.CARD_TYPE_IMAGE_TEXT;
            }

            // 视频卡片强制占两列，其余随机
            int span;
            if (cardType == FeedItem.CARD_TYPE_VIDEO) {
                span = FeedItem.SPAN_DOUBLE;
            } else {
                // // 随机单列 / 双列
                span = random.nextBoolean()
                        ? FeedItem.SPAN_SINGLE
                        : FeedItem.SPAN_DOUBLE;
            }

            String title = "标题 " + id;
            String content = "这里是内容摘要（id=" + id + "），用于展示多行文本效果。";
            // 暂时不用真实 URL，用空字符串占位
            String imageUrl = "";

            list.add(new FeedItem(id, title, content, imageUrl, cardType, span));
        }
        return list;
    }
}
