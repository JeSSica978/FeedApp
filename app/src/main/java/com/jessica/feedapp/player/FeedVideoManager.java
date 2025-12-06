package com.jessica.feedapp.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.jessica.feedapp.model.FeedItem;

/**
 * 单实例视频播放器管理器：
 * - 只维护一个 ExoPlayer
 * - 可绑定到任意一个视频卡片的 PlayerView 上
 * - 支持手动点击播放/暂停
 * - 支持根据 itemId 执行自动播放/暂停（给曝光事件用）
 */
public class FeedVideoManager {

    private final ExoPlayer player;

    // 当前绑定的 View & itemId
    private PlayerView currentPlayerView;
    private long currentItemId = -1L;

    public FeedVideoManager(@NonNull Context context) {
        player = new ExoPlayer.Builder(context.getApplicationContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
    }

    /**
     * 手动或自动绑定到指定卡片并播放（会替换之前的绑定）
     */
    public void bindAndPlay(@NonNull PlayerView playerView, @NonNull FeedItem item) {
        String videoUrl = item.getImageUrl(); // 这里把 imageUrl 当作视频 URL 使用
        if (videoUrl == null || videoUrl.isEmpty()) {
            playerView.setPlayer(null);
            return;
        }

        // 如果之前有绑定到别的 View，先解绑
        if (currentPlayerView != null && currentPlayerView != playerView) {
            currentPlayerView.setPlayer(null);
        }

        currentPlayerView = playerView;
        currentItemId = item.getId();

        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /**
     * 点击当前卡片时的播放/暂停切换。
     */
    public void togglePlay(@NonNull PlayerView playerView, @NonNull FeedItem item) {
        if (playerView == currentPlayerView && item.getId() == currentItemId) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
        } else {
            bindAndPlay(playerView, item);
        }
    }

    /**
     * 暂停当前正在播放的视频。
     */
    public void pause() {
        player.setPlayWhenReady(false);
    }

    /**
     * 如果当前播放的就是某个 itemId，对其进行暂停（给曝光 DISAPPEAR 时使用）。
     */
    public void pauseIfMatching(long itemId) {
        if (itemId == currentItemId) {
            player.setPlayWhenReady(false);
        }
    }

    /**
     * ViewHolder 被回收（滚出屏幕）时调用，停止播放并解绑 View。
     */
    public void onViewRecycled(@NonNull PlayerView playerView) {
        if (playerView == currentPlayerView) {
            player.setPlayWhenReady(false);
            playerView.setPlayer(null);
            currentPlayerView = null;
            currentItemId = -1L;
        }
    }

    /**
     * Activity.onDestroy 时调用，释放播放器资源。
     */
    public void release() {
        currentPlayerView = null;
        currentItemId = -1L;
        player.release();
    }
}
