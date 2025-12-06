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
 * - 内部只维护一个 ExoPlayer
 * - 根据当前可见的视频卡片，动态把播放器绑定到对应的 PlayerView 上
 */
public class FeedVideoManager {

    private final ExoPlayer player;
    private PlayerView currentPlayerView;
    private long currentItemId = -1L;

    public FeedVideoManager(@NonNull Context context) {
        player = new ExoPlayer.Builder(context.getApplicationContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
    }

    /**
     * 绑定到指定卡片并自动播放
     */
    public void bindAndPlay(@NonNull PlayerView playerView, @NonNull FeedItem item) {
        String videoUrl = item.getImageUrl(); // 这里把 imageUrl 当作视频 URL 使用
        if (videoUrl == null || videoUrl.isEmpty()) {
            playerView.setPlayer(null);
            return;
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
     * 点击同一张卡片时的播放/暂停切换
     */
    public void togglePlay(@NonNull PlayerView playerView, @NonNull FeedItem item) {
        if (playerView == currentPlayerView && item.getId() == currentItemId) {
            // 同一张卡片 → 切换播放/暂停
            player.setPlayWhenReady(!player.getPlayWhenReady());
        } else {
            // 切到另一张卡片 → 重新绑定并从头播放
            bindAndPlay(playerView, item);
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
     * Activity.onPause：暂停当前播放。
     */
    public void pause() {
        player.setPlayWhenReady(false);
    }

    /**
     * Activity.onDestroy：彻底释放播放器资源。
     */
    public void release() {
        currentPlayerView = null;
        currentItemId = -1L;
        player.release();
    }
}
