package com.ktv.simple.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.ktv.simple.MainActivity;
import com.ktv.simple.R;
import com.ktv.simple.model.Lyrics;
import com.ktv.simple.audio.LyricsLoader;
import com.ktv.simple.audio.PlayQueueManager;
import com.ktv.simple.audio.PlaybackStateHolder;
import com.ktv.simple.model.PlayState;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Music player service
 * 处理在线音乐播放、队列管理、歌词同步
 */
public class MusicPlayerService extends Service {
    private static final String TAG = "MusicPlayerService";

    private static final String CHANNEL_ID = "JianTingKTV_Player";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private PlayQueueManager queueManager;
    private PlaybackStateHolder stateHolder;
    private Lyrics currentLyrics;
    private PlayState playState;

    private Handler progressHandler;
    private Runnable progressRunnable;
    private List<PlaybackListener> listeners;

    private AudioManager audioManager;

    public static final String ACTION_PLAY = "com.ktv.simple.PLAY";
    public static final String ACTION_PAUSE = "com.ktv.simple.PAUSE";
    public static final String ACTION_NEXT = "com.ktv.simple.NEXT";
    public static final String ACTION_PREVIOUS = "com.ktv.simple.PREVIOUS";
    public static final String ACTION_STOP = "com.ktv.simple.STOP";
    public static final String ACTION_SEEK = "com.ktv.simple.SEEK";

    public static final String EXTRA_SONG_ID = "song_id";
    public static final String EXTRA_QUEUE_ITEM_ID = "queue_item_id";
    public static final String EXTRA_POSITION = "position";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onSongCompleted();
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                playState = PlayState.ERROR;
                notifyPlaybackStateChanged();
                return true;
            }
        });

        queueManager = PlayQueueManager.getInstance();
        stateHolder = PlaybackStateHolder.getInstance();
        listeners = new ArrayList<>();
        playState = PlayState.IDLE;

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Setup progress update
        progressHandler = new Handler();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (playState == PlayState.PLAYING && mediaPlayer != null) {
                    notifyProgressChanged();
                    progressHandler.postDelayed(this, 500); // 更新间隔改为500ms
                }
            }
        };

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.i(TAG, "Received action: " + action);

            if (ACTION_PLAY.equals(action)) {
                handlePlay(intent);
            } else if (ACTION_PAUSE.equals(action)) {
                pause();
            } else if (ACTION_NEXT.equals(action)) {
                playNext();
            } else if (ACTION_PREVIOUS.equals(action)) {
                playPrevious();
            } else if (ACTION_STOP.equals(action)) {
                stop();
            } else if (ACTION_SEEK.equals(action)) {
                int position = intent.getIntExtra(EXTRA_POSITION, 0);
                seekTo(position);
            }
        }

        return START_NOT_STICKY;
    }

    private void handlePlay(Intent intent) {
        long queueItemId = intent.getLongExtra(EXTRA_QUEUE_ITEM_ID, -1);
        if (queueItemId != -1) {
            // Play specific queue item
            playQueueItem(queueItemId);
        } else {
            // Resume or play current
            if (playState == PlayState.PAUSED) {
                resume();
            } else if (playState != PlayState.PLAYING) {
                playNext();
            }
        }
    }

    /**
     * Play specific queue item
     */
    public void playQueueItem(long queueItemId) {
        List<QueueItem> queue = queueManager.getQueue();
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == queueItemId) {
                queueManager.jumpTo(i);
                playCurrent();
                break;
            }
        }
    }

    /**
     * Play current song in queue
     */
    private void playCurrent() {
        QueueItem item = queueManager.getCurrent();
        if (item == null || item.getSong() == null) {
            Log.w(TAG, "No current song to play");
            return;
        }

        playSong(item.getSong());
    }

    /**
     * Play a song
     * 支持在线音乐 URL 播放
     */
    private void playSong(final Song song) {
        if (song == null) {
            Log.e(TAG, "playSong: song is null");
            return;
        }

        String playUrl = song.getMusicUrl();
        if (playUrl == null || playUrl.isEmpty()) {
            playUrl = song.getFilePath();
        }

        if (playUrl == null || playUrl.isEmpty()) {
            Log.e(TAG, "playSong: no play URL available");
            playState = PlayState.ERROR;
            notifyPlaybackStateChanged();
            return;
        }

        // 检查是否为 MP3 格式（Android 4.4 仅支持 MP3）
        String lowerUrl = playUrl.toLowerCase();
        if (!lowerUrl.contains(".mp3") && !lowerUrl.contains("mp3")) {
            Log.w(TAG, "Warning: URL may not be MP3 format. Android 4.4 only supports MP3. URL: " + playUrl);
        }

        try {
            // Stop current playback
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            // Reset media player
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            } else {
                mediaPlayer.reset();
            }

            playState = PlayState.PREPARING;
            notifyPlaybackStateChanged();

            // 更新全局状态 - 当前歌曲信息
            stateHolder.setCurrentSong(song.getTitle(), song.getArtist());

            Log.i(TAG, "Playing song: " + song.getTitle() + " from " + playUrl);

            // Load lyrics
            String lyricsPath = song.getLyricsPath();
            if (lyricsPath != null && LyricsLoader.lyricsFileExists(lyricsPath)) {
                currentLyrics = LyricsLoader.loadFromFile(lyricsPath);
            } else {
                currentLyrics = new Lyrics();
            }

            // Set data source - 在线音乐 URL
            mediaPlayer.setDataSource(playUrl);

            // Prepare and play
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.i(TAG, "MediaPlayer prepared");

                    playState = PlayState.PLAYING;

                    try {
                        requestAudioFocus();
                        Log.i(TAG, "Audio focus requested");
                    } catch (Exception e) {
                        Log.e(TAG, "Request audio focus error", e);
                    }

                    try {
                        mp.start();
                        Log.i(TAG, "MediaPlayer started");
                    } catch (Exception e) {
                        Log.e(TAG, "Start media player error", e);
                    }

                    // 更新全局状态
                    stateHolder.setPlayState(PlayState.PLAYING);
                    stateHolder.setPosition(0, mp.getDuration());

                    try {
                        notifyPlaybackStateChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Notify playback state error", e);
                    }

                    try {
                        notifyQueueChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Notify queue changed error", e);
                    }

                    try {
                        updateNotification();
                        Log.i(TAG, "Notification updated");
                    } catch (Exception e) {
                        Log.e(TAG, "Update notification error", e);
                    }

                    try {
                        progressHandler.post(progressRunnable);
                        Log.i(TAG, "Progress handler started");
                    } catch (Exception e) {
                        Log.e(TAG, "Start progress handler error", e);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Play song error", e);
            playState = PlayState.ERROR;
            stateHolder.setPlayState(PlayState.ERROR);
            notifyPlaybackStateChanged();
        }
    }

    /**
     * Pause playback
     */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playState = PlayState.PAUSED;
            stateHolder.setPlayState(PlayState.PAUSED);
            notifyPlaybackStateChanged();
            updateNotification();
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    /**
     * Resume playback
     */
    public void resume() {
        if (mediaPlayer != null && playState == PlayState.PAUSED) {
            mediaPlayer.start();
            playState = PlayState.PLAYING;
            stateHolder.setPlayState(PlayState.PLAYING);
            notifyPlaybackStateChanged();
            updateNotification();
            progressHandler.post(progressRunnable);
        }
    }

    /**
     * Play next song
     */
    public void playNext() {
        if (queueManager.hasNext()) {
            queueManager.next();
            playCurrent();
        } else {
            Log.i(TAG, "No next song");
            stop();
        }
    }

    /**
     * Play previous song
     */
    public void playPrevious() {
        if (queueManager.hasPrevious()) {
            queueManager.previous();
            playCurrent();
        } else {
            Log.i(TAG, "No previous song");
        }
    }

    /**
     * Stop playback
     */
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
        playState = PlayState.IDLE;
        stateHolder.setPlayState(PlayState.IDLE);
        stateHolder.setPosition(0, 0);
        stateHolder.setCurrentSong("", "");
        notifyPlaybackStateChanged();
        progressHandler.removeCallbacks(progressRunnable);
        stopForeground(true);
    }

    private void onSongCompleted() {
        Log.i(TAG, "Song completed");
        playNext();
    }

    private void requestAudioFocus() {
        if (audioManager != null) {
            int result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
            Log.d(TAG, "Audio focus request result: " + result);
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && (playState == PlayState.PLAYING || playState == PlayState.PAUSED)) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null && (playState == PlayState.PLAYING || playState == PlayState.PAUSED)) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public int getCurrentLyricLine() {
        if (currentLyrics != null && currentLyrics.hasLyrics()) {
            int position = getCurrentPosition();
            return currentLyrics.findCurrentLine(position);
        }
        return -1;
    }

    public Lyrics getLyrics() {
        return currentLyrics;
    }

    public PlayState getPlayState() {
        return playState;
    }

    public void addPlaybackListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyPlaybackStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackStateChanged(playState);
        }
    }

    private void notifyProgressChanged() {
        int position = getCurrentPosition();
        int duration = getDuration();
        
        // 更新全局状态
        stateHolder.setPosition(position, duration);
        
        for (PlaybackListener listener : listeners) {
            listener.onProgressChanged(position, duration);
        }
    }

    private void notifyQueueChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onQueueChanged(queueManager.getQueue());
        }
    }

    public interface PlaybackListener {
        void onPlaybackStateChanged(PlayState state);
        void onProgressChanged(int currentPosition, int duration);
        void onQueueChanged(List<QueueItem> queue);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "简听KTV 播放器",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("音乐播放通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification() {
        QueueItem current = queueManager.getCurrent();
        if (current == null || current.getSong() == null) {
            return;
        }

        Song song = current.getSong();
        String title = song.getTitle();
        String artist = song.getArtist();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingIntent)
            .setOngoing(playState == PlayState.PLAYING);

        // Add control actions
        Intent prevIntent = new Intent(this, MusicPlayerService.class);
        prevIntent.setAction(ACTION_PREVIOUS);
        PendingIntent prevPending = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.addAction(android.R.drawable.ic_media_previous, "上一首", prevPending);

        int playIcon = playState == PlayState.PLAYING ?
            android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        Intent playIntent = new Intent(this, MusicPlayerService.class);
        playIntent.setAction(playState == PlayState.PLAYING ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPending = PendingIntent.getService(
            this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.addAction(playIcon, "播放/暂停", playPending);

        Intent nextIntent = new Intent(this, MusicPlayerService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPending = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.addAction(android.R.drawable.ic_media_next, "下一首", nextPending);

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        progressHandler.removeCallbacks(progressRunnable);
        stopForeground(true);
    }

    // Binder for activity binding
    public class LocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
