package com.jessica.feedapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jessica.feedapp.model.FeedItem;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * 负责 Feed 列表的本地缓存：
 * - 使用 SharedPreferences 存一份 JSON 字符串快照
 * - 提供 save / load / clear / hasCache 接口
 */
public class FeedCacheManager {

    private static final String TAG = "FeedCacheManager";
    private static final String PREF_NAME = "feed_cache";
    private static final String KEY_FEED_LIST = "key_feed_list";

    private final SharedPreferences sharedPreferences;
    private final Gson gson = new Gson();

    public FeedCacheManager(Context context) {
        this.sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存当前列表到本地缓存
     */
    public void saveFeedList(List<FeedItem> feedList) {
        if (feedList == null || feedList.isEmpty()) {
            Log.d(TAG, "saveFeedList: empty list, clear cache");
            clear();
            return;
        }
        try {
            String json = gson.toJson(feedList);
            sharedPreferences.edit()
                    .putString(KEY_FEED_LIST, json)
                    .apply(); // apply 异步写盘
            Log.d(TAG, "saveFeedList: cache saved, size=" + feedList.size());
        } catch (Exception e) {
            Log.e(TAG, "saveFeedList: error", e);
        }
    }

    /**
     * 读取本地缓存的列表
     */
    public List<FeedItem> loadFeedList() {
        String json = sharedPreferences.getString(KEY_FEED_LIST, null);
        if (json == null || json.isEmpty()) {
            Log.d(TAG, "loadFeedList: no cache");
            return Collections.emptyList();
        }
        try {
            Type type = new TypeToken<List<FeedItem>>() {}.getType();
            List<FeedItem> list = gson.fromJson(json, type);
            if (list == null) {
                return Collections.emptyList();
            }
            Log.d(TAG, "loadFeedList: loaded cache, size=" + list.size());
            return list;
        } catch (Exception e) {
            Log.e(TAG, "loadFeedList: parse error, clear cache", e);
            clear();
            return Collections.emptyList();
        }
    }

    public boolean hasCache() {
        String json = sharedPreferences.getString(KEY_FEED_LIST, null);
        return json != null && !json.isEmpty();
    }

    public void clear() {
        sharedPreferences.edit()
                .remove(KEY_FEED_LIST)
                .apply();
    }
}
