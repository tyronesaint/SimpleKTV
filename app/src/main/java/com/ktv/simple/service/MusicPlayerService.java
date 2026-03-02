package com.ktv.simple.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.ktv.simple.MainActivity;
import com.ktv.simple.R;
import com.ktv.simple.model.Lyrics;
import com.ktv.simple.audio.LyricsLoader;
import com.ktv.simple.audio.MetadataParser;
import com.ktv.simple.audio.PlayQueueManager;
import com.ktv.simple.cache.CacheManager;
import com.ktv.simple.model.PlayState;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Music player service
 * Handles music playback, queue management, and lyrics synchronization
 */
public class MusicPlayerService extends Service {
    private static final String TAG = "MusicPlayerService";

    private static final String CHANNEL_ID = "SimpleKTV_Player";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private PlayQueueManager queueManager;
    private Lyrics currentLyrics;
    private PlayState playState;

    private Handler progressHandler;
    private Runnable progressRunnable;
    private List<PlaybackListener> listeners;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private Object audioFocusRequestObj; // For API 26+ compatibility

    public static final String ACTION_PLAY = "com.ktv.simple.PLAY";
    public static final String ACTION_PAUSE = "com.ktv.simple.PAUSE";
    public static final String ACTION_NEXT = "com.ktv.simple.NEXT";
    public static final String ACTION_PREVIOUS = "com.ktv.simple.PREVIOUS";
    public static final String ACTION_STOP = "com.ktv.simple.STOP";

    public static final String EXTRA_SONG_ID = "song_id";
    public static final String EXTRA_QUEUE_ITEM_ID = "queue_item_id";

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
                    progressHandler.postDelayed(this, 100);
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

            switch (action) {
                case ACTION_PLAY:
                    handlePlay(intent);
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_STOP:
                    stop();
                    break;
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
     */
    private void playSong(final Song song) {
        if (song == null) {
            Log.e(TAG, "playSong: song is null");
            return;
        }

        String filePath = song.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "playSong: file path is null or empty");
            playState = PlayState.ERROR;
            notifyPlaybackStateChanged();
            return;
        }

        // Check if file exists for local storage
        if (song.getStorageType() == Song.StorageType.LOCAL) {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "playSong: file does not exist or cannot be read: " + filePath);
                playState = PlayState.ERROR;
                notifyPlaybackStateChanged();
                return;
            }
        }

        try {
            // Stop current playback
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            // Reset media player
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            } else {
                mediaPlayer.reset();
            }

            playState = PlayState.PREPARING;
            notifyPlaybackStateChanged();

            Log.i(TAG, "Playing song: " + song.getTitle() + " from " + filePath);

            // Determine actual file path (local, cached, or remote)
            // Java 7: Use array wrapper for mutable variable accessed from inner class
            final String[] actualFilePathHolder = new String[]{filePath};
            final boolean useOnlineStreaming;

            switch (song.getStorageType()) {
                case LOCAL:
                    // Direct local playback
                    actualFilePathHolder[0] = filePath;
                    useOnlineStreaming = false;
                    break;

                case WEBDAV:
                case FTP:
                case SMB:
                    // Try online streaming first, fallback to cache
                    CacheManager cacheManager = CacheManager.getInstance(getApplicationContext());
                    File cachedFile = cacheManager.getCachedAudioFile(song);

                    if (cachedFile != null && cachedFile.exists()) {
                        // Use cached file
                        actualFilePathHolder[0] = cachedFile.getAbsolutePath();
                        useOnlineStreaming = false;
                        Log.i(TAG, "Using cached file: " + actualFilePathHolder[0]);
                    } else {
                        // Try online streaming first
                        useOnlineStreaming = true;
                        actualFilePathHolder[0] = filePath;
                        Log.i(TAG, "Trying online streaming: " + actualFilePathHolder[0]);
                    }
                    break;

                default:
                    // Fallback: treat as local playback
                    actualFilePathHolder[0] = filePath;
                    useOnlineStreaming = false;
                    break;
            }

            // Note: Metadata parsing is done in LocalStorageProvider
            // Skip parsing here to avoid conflict with MediaPlayer on Android 4.4

            // Load lyrics
            String lyricsPath = song.getLyricsPath();
            if (lyricsPath == null || !LyricsLoader.lyricsFileExists(lyricsPath)) {
                lyricsPath = MetadataParser.findLrcFile(filePath);
                if (lyricsPath != null) {
                    song.setLyricsPath(lyricsPath);
                }
            }

            if (lyricsPath != null && LyricsLoader.lyricsFileExists(lyricsPath)) {
                currentLyrics = LyricsLoader.loadFromFile(lyricsPath);
            } else {
                currentLyrics = new Lyrics();
            }

            // Set data source
            try {
                mediaPlayer.setDataSource(actualFilePathHolder[0]);
                Log.d(TAG, "Data source set: " + actualFilePathHolder[0]);
            } catch (Exception e) {
                Log.e(TAG, "Set data source error", e);

                // If online streaming failed and this is a remote file, try downloading
                if (useOnlineStreaming && song.getStorageType() != Song.StorageType.LOCAL) {
                    Log.i(TAG, "Online streaming failed, downloading to cache");
                    String downloadedPath = downloadRemoteFile(song);
                    if (downloadedPath != null) {
                        File downloadedFile = new File(downloadedPath);
                        if (downloadedFile.exists()) {
                            try {
                                mediaPlayer.setDataSource(downloadedPath);
                                actualFilePathHolder[0] = downloadedPath;
                                Log.i(TAG, "Using downloaded file: " + actualFilePathHolder[0]);
                            } catch (Exception ex) {
                                Log.e(TAG, "Set data source from downloaded file error", ex);
                                playState = PlayState.ERROR;
                                notifyPlaybackStateChanged();
                                return;
                            }
                        } else {
                            playState = PlayState.ERROR;
                            notifyPlaybackStateChanged();
                            return;
                        }
                    } else {
                        playState = PlayState.ERROR;
                        notifyPlaybackStateChanged();
                        return;
                    }
                } else {
                    playState = PlayState.ERROR;
                    notifyPlaybackStateChanged();
                    return;
                }
            }

            // Prepare and play
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.i(TAG, "MediaPlayer prepared");

                    // Add to cache if this is a remote file and was played successfully
                    if (useOnlineStreaming && song.getStorageType() != Song.StorageType.LOCAL) {
                        CacheManager cacheManager = CacheManager.getInstance(getApplicationContext());
                        File currentFile = cacheManager.getCachedAudioFile(song);
                        if (currentFile == null) {
                            // Cache the downloaded file
                            File downloadedFile = new File(actualFilePathHolder[0]);
                            if (downloadedFile.exists()) {
                                cacheManager.addAudioToCache(song, downloadedFile);
                                Log.i(TAG, "Added to cache: " + song.getTitle());
                            }
                        }
                    }

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
            notifyPlaybackStateChanged();
            updateNotification();
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    /**
     * Resume playback
     */
    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            playState = PlayState.PLAYING;
            notifyPlaybackStateChanged();
            updateNotification();
            progressHandler.post(progressRunnable);
        }
    }

    /**
     * Play next song
     */
    public void playNext() {
        QueueItem next = queueManager.getNext();
        if (next != null) {
            playCurrent();
        } else {
            Log.i(TAG, "No next song in queue");
            stop();
        }
    }

    /**
     * Play previous song
     */
    public void playPrevious() {
        QueueItem previous = queueManager.getPrevious();
        if (previous != null) {
            playCurrent();
        }
    }

    /**
     * Stop playback
     */
    public void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        playState = PlayState.IDLE;
        notifyPlaybackStateChanged();
        progressHandler.removeCallbacks(progressRunnable);
        stopForeground(true);
        stopSelf();
    }

    /**
     * Called when song completes
     */
    private void onSongCompleted() {
        playState = PlayState.COMPLETED;
        notifyPlaybackStateChanged();
        playNext();
    }

    /**
     * Request audio focus
     */
    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            // API 26- (Android 4.4 compatible)
            audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(0.3f, 0.3f);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    resume();
                    break;
            }
        }
    };

    /**
     * Get current playback position
     */
    public int getCurrentPosition() {
        if (mediaPlayer != null && playState == PlayState.PLAYING) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    /**
     * Get duration of current song
     */
    public int getDuration() {
        if (mediaPlayer != null && playState != PlayState.IDLE) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    /**
     * Seek to position
     */
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    /**
     * Get current lyrics line
     */
    public int getCurrentLyricLine() {
        if (currentLyrics != null && currentLyrics.hasLyrics()) {
            int position = getCurrentPosition();
            return currentLyrics.findCurrentLine(position);
        }
        return -1;
    }

    /**
     * Get lyrics
     */
    public Lyrics getLyrics() {
        return currentLyrics;
    }

    /**
     * Get play state
     */
    public PlayState getPlayState() {
        return playState;
    }

    /**
     * Add playback listener
     */
    public void addPlaybackListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove playback listener
     */
    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyPlaybackStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackStateChanged(playState);
        }
    }

    private void notifyProgressChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onProgressChanged(getCurrentPosition(), getDuration());
        }
    }

    private void notifyQueueChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onQueueChanged(queueManager.getQueue());
        }
    }

    /**
     * Playback listener interface
     */
    public interface PlaybackListener {
        void onPlaybackStateChanged(PlayState state);
        void onProgressChanged(int currentPosition, int duration);
        void onQueueChanged(List<QueueItem> queue);
    }

    /**
     * Create notification channel (required for Android O+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music player notification");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Update notification
     */
    private void updateNotification() {
        try {
            QueueItem item = queueManager.getCurrent();
            if (item == null) {
                Log.w(TAG, "No current item for notification");
                return;
            }

            Song song = item.getSong();
            if (song == null) {
                Log.w(TAG, "No song in current item for notification");
                return;
            }

            String title = song.getTitle();
            String artist = song.getArtist();

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setOngoing(playState == PlayState.PLAYING);

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
            Log.i(TAG, "Notification updated: " + title);
        } catch (Exception e) {
            Log.e(TAG, "Update notification error", e);
            // Don't crash the service if notification fails
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }

        if (audioManager != null && audioFocusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    /**
     * Download remote file to temp directory for playback
     */
    private String downloadRemoteFile(Song song) {
        if (song == null || song.getFilePath() == null) {
            return null;
        }

        java.io.File tempDir = new java.io.File(
                getApplicationContext().getCacheDir(),
                "audio_cache"
        );

        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        String fileName = "song_" + song.getId() + ".mp3";
        java.io.File tempFile = new java.io.File(tempDir, fileName);

        // Check if file already exists
        if (tempFile.exists()) {
            Log.d(TAG, "Temp file already exists: " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
        }

        try {
            // Download based on storage type
            java.io.InputStream inputStream = null;
            java.io.FileOutputStream outputStream = null;

            switch (song.getStorageType()) {
                case WEBDAV:
                    inputStream = downloadFromWebDAV(song.getFilePath());
                    break;
                case FTP:
                    inputStream = downloadFromFTP(song.getFilePath());
                    break;
                case SMB:
                    inputStream = downloadFromSMB(song.getFilePath());
                    break;
                default:
                    Log.w(TAG, "Unknown storage type: " + song.getStorageType());
                    return null;
            }

            if (inputStream == null) {
                Log.e(TAG, "Failed to get input stream for: " + song.getFilePath());
                return null;
            }

            outputStream = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            inputStream.close();
            outputStream.close();

            Log.i(TAG, "Downloaded " + totalBytes + " bytes to " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Download remote file error", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        }
    }

    /**
     * Download file from WebDAV
     */
    private java.io.InputStream downloadFromWebDAV(String url) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            String webdavUrl = prefs.getString("webdav_url", "");
            String webdavUsername = prefs.getString("webdav_username", "");
            String webdavPassword = prefs.getString("webdav_password", "");

            String authHeader = webdavUsername + ":" + webdavPassword;
            String encodedAuth = android.util.Base64.encodeToString(
                    authHeader.getBytes(),
                    android.util.Base64.NO_WRAP
            );

            java.net.URL fileUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) fileUrl.openConnection();
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            if (connection.getResponseCode() == 200) {
                return connection.getInputStream();
            } else {
                Log.e(TAG, "WebDAV download failed: " + connection.getResponseCode());
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "WebDAV download error", e);
            return null;
        }
    }

    /**
     * Download file from FTP
     */
    private java.io.InputStream downloadFromFTP(String filePath) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            String ftpServer = prefs.getString("ftp_server", "");
            String ftpPortStr = prefs.getString("ftp_port", "21");
            String ftpUsername = prefs.getString("ftp_username", "");
            String ftpPassword = prefs.getString("ftp_password", "");

            int ftpPort = Integer.parseInt(ftpPortStr);

            // Use Apache Commons FTP client
            org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();
            ftpClient.connect(ftpServer, ftpPort);
            ftpClient.login(ftpUsername, ftpPassword);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);

            java.io.InputStream inputStream = ftpClient.retrieveFileStream(filePath);
            // Note: Caller must close the stream and call ftpClient.completePendingCommand()
            // For simplicity, we'll just return a copy
            if (inputStream != null) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                ftpClient.completePendingCommand();
                ftpClient.logout();
                ftpClient.disconnect();

                return new java.io.ByteArrayInputStream(baos.toByteArray());
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "FTP download error", e);
            return null;
        }
    }

    /**
     * Download file from SMB
     */
    private java.io.InputStream downloadFromSMB(String filePath) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            String smbServer = prefs.getString("smb_server", "");
            String smbShare = prefs.getString("smb_share", "");
            String smbUsername = prefs.getString("smb_username", "");
            String smbPassword = prefs.getString("smb_password", "");

            String smbUrl = "smb://" + smbServer + "/" + smbShare + filePath;

            jcifs.smb.SmbFile smbFile = new jcifs.smb.SmbFile(smbUrl);
            if (smbFile.exists()) {
                return new jcifs.smb.SmbFileInputStream(smbFile);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "SMB download error", e);
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
