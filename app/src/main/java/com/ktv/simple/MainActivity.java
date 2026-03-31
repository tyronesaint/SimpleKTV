package com.ktv.simple;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.ktv.simple.adapter.QueueAdapter;
import com.ktv.simple.adapter.SongAdapter;
import com.ktv.simple.api.OnlineMusicService;
import com.ktv.simple.audio.PlayQueueManager;
import com.ktv.simple.model.Lyrics;
import com.ktv.simple.model.MusicSource;
import com.ktv.simple.model.MusicUrlData;
import com.ktv.simple.model.PlayState;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;
import com.ktv.simple.service.HttpControlService;
import com.ktv.simple.service.MusicPlayerService;
import com.ktv.simple.util.QrCodeGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Activity for 简听KTV
 * 在线音乐服务版本
 * 
 * 核心流程：
 * 1. 从服务器获取音源列表，动态展示
 * 2. 用户搜索时使用音源的 supportedSources 中的值
 * 3. 搜索结果包含 source 和 songId，后续请求直接使用
 */
public class MainActivity extends AppCompatActivity implements
        SongAdapter.OnItemClickListener,
        QueueAdapter.OnItemClickListener,
        MusicPlayerService.PlaybackListener {

    private static final String TAG = "MainActivity";

    // UI Components
    private EditText searchEditText;
    private Button searchButton;
    private Spinner sourceSpinner;
    private ImageButton settingsButton;
    private Button libraryButton;
    private Button queueButton;
    private Button remoteButton;
    private RecyclerView songRecyclerView;
    private RecyclerView queueRecyclerView;
    private ScrollView remoteScrollView;
    private TextView statusTextView;

    private TextView songTitleTextView;
    private TextView songArtistTextView;
    private ImageView albumCoverImageView;
    private ImageButton previousButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private SeekBar progressSeekBar;
    private TextView currentPositionTextView;
    private TextView durationTextView;
    private com.ktv.simple.view.LyricsView lyricsView;
    private ImageView qrCodeImageView;
    private TextView serverUrlTextView;

    // Adapters
    private SongAdapter songAdapter;
    private QueueAdapter queueAdapter;

    // Data
    private List<Song> searchResults;
    private List<QueueItem> queueList;
    private PlayQueueManager queueManager;

    // Services
    private MusicPlayerService playerService;
    private HttpControlService httpService;
    private OnlineMusicService musicService;
    private boolean serviceBound = false;

    // 动态获取的音源列表
    private List<MusicSource> musicSources;
    // 当前选中的平台标识（从音源的 supportedSources 获取）
    private String currentSource = "";
    private int currentPage = 1;
    private boolean isLoading = false;

    // Service Connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            playerService = binder.getService();
            playerService.addPlaybackListener(MainActivity.this);
            serviceBound = true;
            Log.d(TAG, "MusicPlayerService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playerService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        settingsButton = findViewById(R.id.settingsButton);
        libraryButton = findViewById(R.id.libraryButton);
        queueButton = findViewById(R.id.queueButton);
        remoteButton = findViewById(R.id.remoteButton);
        songRecyclerView = findViewById(R.id.songRecyclerView);
        queueRecyclerView = findViewById(R.id.queueRecyclerView);
        remoteScrollView = findViewById(R.id.remoteScrollView);
        statusTextView = findViewById(R.id.storageTypeTextView);

        songTitleTextView = findViewById(R.id.songTitleTextView);
        songArtistTextView = findViewById(R.id.songArtistTextView);
        albumCoverImageView = findViewById(R.id.albumCoverImageView);
        previousButton = findViewById(R.id.previousButton);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextButton = findViewById(R.id.nextButton);
        progressSeekBar = findViewById(R.id.progressSeekBar);
        currentPositionTextView = findViewById(R.id.currentPositionTextView);
        durationTextView = findViewById(R.id.durationTextView);
        lyricsView = findViewById(R.id.lyricsView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        serverUrlTextView = findViewById(R.id.serverUrlTextView);

        // 添加音源选择 Spinner
        sourceSpinner = new Spinner(this);
        LinearLayout topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            topBar.addView(sourceSpinner, 1, params);
        }
    }

    private void initData() {
        searchResults = new ArrayList<>();
        queueList = new ArrayList<>();
        queueManager = PlayQueueManager.getInstance();
        musicService = OnlineMusicService.getInstance(this);
        musicSources = new ArrayList<>();

        // Setup Song RecyclerView
        songRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(this, searchResults, this);
        songRecyclerView.setAdapter(songAdapter);

        // Setup Queue RecyclerView
        queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        queueAdapter = new QueueAdapter(this, queueList, this);
        queueRecyclerView.setAdapter(queueAdapter);

        // 加载音源列表
        loadMusicSources();
    }

    /**
     * 加载音源列表
     */
    private void loadMusicSources() {
        // 先使用缓存的音源
        musicSources = musicService.getCachedMusicSources();
        
        if (musicSources.isEmpty()) {
            // 如果没有缓存，检查是否已配置
            if (!musicService.isConfigured()) {
                // 未配置，显示提示并引导用户去设置
                updateStatus("请先配置服务器地址");
                setupSourceSpinner(); // 设置空的 spinner
                
                // 显示提示对话框
                showConfigDialog();
                return;
            }
            
            // 从服务器获取音源列表
            fetchMusicSources();
        } else {
            // 使用缓存的音源
            updateSourceSpinner();
            updateStatus("已加载 " + musicSources.size() + " 个音源");
        }
    }

    /**
     * 显示配置提示对话框
     */
    private void showConfigDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("欢迎使用简听KTV");
        builder.setMessage("请先配置音乐服务器地址才能使用。\n\n您需要搭建自己的音乐服务，或使用已有的服务地址。");
        builder.setPositiveButton("去配置", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("稍后再说", null);
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 从服务器获取音源列表
     */
    private void fetchMusicSources() {
        updateStatus("正在获取音源列表...");
        
        musicService.getMusicSources(new OnlineMusicService.SourcesCallback() {
            @Override
            public void onSuccess(final List<MusicSource> sources) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        musicSources.clear();
                        musicSources.addAll(sources);
                        updateSourceSpinner();
                        updateStatus("已加载 " + sources.size() + " 个音源");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("获取音源失败: " + message);
                        setupSourceSpinner();
                    }
                });
            }
        });
    }

    /**
     * 设置音源选择器
     * 根据获取的音源列表动态展示
     */
    private void setupSourceSpinner() {
        // 默认选项
        String[] defaultOptions = {"请先配置"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, defaultOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        if (sourceSpinner != null) {
            sourceSpinner.setAdapter(adapter);
            sourceSpinner.setEnabled(false);
        }
    }

    /**
     * 更新音源选择器
     * 从音源列表中提取平台选项
     */
    private void updateSourceSpinner() {
        if (musicSources.isEmpty()) {
            setupSourceSpinner();
            return;
        }

        // 从音源列表中提取所有支持的平台
        // 优先使用默认音源
        MusicSource defaultSource = musicService.getDefaultSource();
        
        // 构建平台选项列表
        // 显示格式：平台标识 (音源名称)
        final List<String> sourceValues = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();

        if (defaultSource != null && defaultSource.getSupportedSources() != null) {
            // 使用默认音源的支持平台
            for (String platform : defaultSource.getSupportedSources()) {
                sourceValues.add(platform);
                sourceNames.add(getPlatformName(platform) + " (" + defaultSource.getName() + ")");
            }
        } else {
            // 使用第一个音源
            MusicSource firstSource = musicSources.get(0);
            if (firstSource.getSupportedSources() != null) {
                for (String platform : firstSource.getSupportedSources()) {
                    sourceValues.add(platform);
                    sourceNames.add(getPlatformName(platform) + " (" + firstSource.getName() + ")");
                }
            }
        }

        if (sourceValues.isEmpty()) {
            setupSourceSpinner();
            return;
        }

        // 设置默认选中第一个
        currentSource = sourceValues.get(0);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sourceNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        if (sourceSpinner != null) {
            sourceSpinner.setAdapter(adapter);
            sourceSpinner.setEnabled(true);
            sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentSource = sourceValues.get(position);
                    Log.d(TAG, "Source changed to: " + currentSource);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    /**
     * 获取平台中文名称
     */
    private String getPlatformName(String platform) {
        if ("kw".equals(platform)) return "酷我";
        if ("kg".equals(platform)) return "酷狗";
        if ("tx".equals(platform) || "qq".equals(platform)) return "QQ音乐";
        if ("wy".equals(platform)) return "网易云";
        if ("mg".equals(platform)) return "咪咕";
        return platform.toUpperCase();
    }

    private void setupListeners() {
        // Search
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        // Settings
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        // Tab buttons
        libraryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLibrary();
            }
        });

        queueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQueue();
            }
        });

        remoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRemote();
            }
        });

        // Playback controls
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrevious();
            }
        });

        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        });

        // Progress seek bar
        progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 仅在用户拖动时更新显示
                if (fromUser && playerService != null) {
                    int duration = playerService.getDuration();
                    int newPosition = (int) ((progress / 100.0) * duration);
                    currentPositionTextView.setText(formatTime(newPosition));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playerService != null) {
                    int duration = playerService.getDuration();
                    int newPosition = (int) ((seekBar.getProgress() / 100.0) * duration);
                    playerService.seekTo(newPosition);
                }
            }
        });
    }

    /**
     * 执行在线搜索
     * 使用当前选中的平台标识
     */
    private void performSearch() {
        final String keyword = searchEditText.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!musicService.isConfigured()) {
            Toast.makeText(this, "请先在设置中配置管理地址", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSource.isEmpty()) {
            Toast.makeText(this, "请先获取音源列表", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLoading) {
            return;
        }

        isLoading = true;
        currentPage = 1;
        updateStatus("搜索中...");

        // 使用当前选中的平台标识进行搜索
        musicService.search(keyword, currentSource, currentPage, 20,
            new OnlineMusicService.SearchCallback() {
                @Override
                public void onSuccess(final List<Song> songs) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            searchResults.clear();
                            searchResults.addAll(songs);
                            songAdapter.notifyDataSetChanged();
                            updateStatus("找到 " + songs.size() + " 首歌曲");
                            
                            if (songs.isEmpty()) {
                                Toast.makeText(MainActivity.this, "未找到相关歌曲", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                @Override
                public void onError(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            updateStatus("搜索失败");
                            // 显示详细错误信息
                            showErrorDialog("搜索失败", message);
                        }
                    });
                }
            });
    }

    /**
     * 显示错误对话框
     */
    private void showErrorDialog(String title, String message) {
        new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }

    private void showLibrary() {
        songRecyclerView.setVisibility(View.VISIBLE);
        queueRecyclerView.setVisibility(View.GONE);
        remoteScrollView.setVisibility(View.GONE);
    }

    private void showQueue() {
        songRecyclerView.setVisibility(View.GONE);
        queueRecyclerView.setVisibility(View.VISIBLE);
        remoteScrollView.setVisibility(View.GONE);
        updateQueueDisplay();
    }

    private void showRemote() {
        songRecyclerView.setVisibility(View.GONE);
        queueRecyclerView.setVisibility(View.GONE);
        remoteScrollView.setVisibility(View.VISIBLE);

        // Start HTTP service and show QR code
        startHttpService();
    }

    private void startHttpService() {
        if (httpService == null) {
            httpService = new HttpControlService(this, 8080);
        }

        if (!httpService.isAlive()) {
            httpService.startService();
        }

        // Generate QR code
        String serverUrl = httpService.getServerUrl();
        Bitmap qrBitmap = QrCodeGenerator.generateQrCode(serverUrl, 512, 512);
        if (qrBitmap != null) {
            qrCodeImageView.setImageBitmap(qrBitmap);
        }

        serverUrlTextView.setText("扫描二维码连接手机进行远程控制\n" + serverUrl);
    }

    // SongAdapter.OnItemClickListener
    @Override
    public void onSongClick(final Song song) {
        // 点击歌曲：使用搜索结果中的 source 和 songId 获取播放地址
        // 重要：source 和 songId 已经在搜索结果中返回，无需用户选择
        final String source = song.getSource();
        final String songId = song.getSongId();
        
        if (source == null || source.isEmpty() || songId == null || songId.isEmpty()) {
            Toast.makeText(this, "歌曲信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }

        updateStatus("获取播放地址...");
        
        // 使用搜索结果中的 source 和 songId
        musicService.getMusicUrl(source, songId, "320k",
            new OnlineMusicService.MusicUrlCallback() {
                @Override
                public void onSuccess(final MusicUrlData data) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Got music URL: " + data.url);
                            song.setMusicUrl(data.url);
                            song.setQuality(data.quality != null ? data.quality : data.type);
                            
                            // 如果返回了歌词，直接使用（不需要额外请求）
                            if (data.hasLyric()) {
                                Log.i(TAG, "Got lyric from music URL response, length: " + data.lyric.length());
                                song.setLyricsPath("lyric://" + songId);
                                saveLyricToCache(song, data.lyric);
                            } else {
                                // 没有歌词时才单独请求
                                loadLyricAndCover(song);
                            }
                            
                            // 清空队列并播放
                            queueManager.clearQueue();
                            QueueItem item = queueManager.addToQueue(song);
                            updateQueueDisplay();

                            if (item != null) {
                                playSong(item.getId());
                                Toast.makeText(MainActivity.this, "正在播放: " + song.getTitle(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                @Override
                public void onError(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("获取播放地址失败");
                            showErrorDialog("获取播放地址失败", message);
                        }
                    });
                }
            });
    }

    @Override
    public void onAddToQueueClick(final Song song) {
        // 添加到队列：使用搜索结果中的 source 和 songId
        final String source = song.getSource();
        final String songId = song.getSongId();
        
        if (source == null || source.isEmpty() || songId == null || songId.isEmpty()) {
            Toast.makeText(this, "歌曲信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }

        updateStatus("获取播放地址...");
        
        musicService.getMusicUrl(source, songId, "320k",
            new OnlineMusicService.MusicUrlCallback() {
                @Override
                public void onSuccess(final MusicUrlData data) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            song.setMusicUrl(data.url);
                            song.setQuality(data.quality != null ? data.quality : data.type);
                            
                            // 如果返回了歌词，直接使用
                            if (data.hasLyric()) {
                                song.setLyricsPath("lyric://" + songId);
                                saveLyricToCache(song, data.lyric);
                            } else {
                                // 没有歌词时才单独请求
                                loadLyricAndCover(song);
                            }
                            
                            QueueItem item = queueManager.addToQueue(song);
                            updateQueueDisplay();
                            updateStatus("已添加到队列: " + song.getTitle());
                            Toast.makeText(MainActivity.this, "已添加到队列", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showErrorDialog("获取播放地址失败", message);
                        }
                    });
                }
            });
    }

    /**
     * 加载歌词和封面（仅在播放地址接口未返回歌词时调用）
     * 使用歌曲中的 source 和 songId
     */
    private void loadLyricAndCover(final Song song) {
        String source = song.getSource();
        final String songId = song.getSongId();
        
        // 如果已经有歌词，跳过
        if (song.getLyricsPath() != null && !song.getLyricsPath().isEmpty()) {
            Log.d(TAG, "Song already has lyric, skip loading");
            return;
        }

        // 获取歌词
        musicService.getLyric(source, songId,
            new OnlineMusicService.LyricCallback() {
                @Override
                public void onSuccess(String lyric, String tlyric) {
                    if (lyric != null && !lyric.isEmpty()) {
                        song.setLyricsPath("lyric://" + songId);
                        // 存储歌词到临时文件
                        saveLyricToCache(song, lyric);
                    }
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Failed to get lyric: " + message);
                }
            });

        // 获取封面
        musicService.getAlbumCover(source, songId,
            new OnlineMusicService.CoverCallback() {
                @Override
                public void onSuccess(String coverUrl) {
                    if (coverUrl != null && !coverUrl.isEmpty()) {
                        song.setCoverPath(coverUrl);
                        // 更新当前播放显示
                        QueueItem current = queueManager.getCurrent();
                        if (current != null && current.getSong() != null 
                            && current.getSong().getSongId().equals(songId)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateNowPlaying();
                                }
                            });
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Failed to get cover: " + message);
                }
            });
    }

    /**
     * 保存歌词到缓存
     */
    private void saveLyricToCache(Song song, String lyricContent) {
        try {
            java.io.File cacheDir = getCacheDir();
            java.io.File lyricFile = new java.io.File(cacheDir, "lyric_" + song.getSongId() + ".lrc");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(lyricFile);
            fos.write(lyricContent.getBytes("UTF-8"));
            fos.close();
            song.setLyricsPath(lyricFile.getAbsolutePath());
            Log.d(TAG, "Lyric saved to: " + lyricFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save lyric", e);
        }
    }

    // QueueAdapter.OnItemClickListener
    @Override
    public void onQueueItemClick(QueueItem item, int action) {
        switch (action) {
            case QueueAdapter.ACTION_PLAY:
                playSong(item.getId());
                break;
            case QueueAdapter.ACTION_MOVE_TO_TOP:
                queueManager.moveToTop(item.getPosition());
                updateQueueDisplay();
                break;
            case QueueAdapter.ACTION_REMOVE:
                queueManager.removeFromQueueById(item.getId());
                updateQueueDisplay();
                break;
        }
    }

    private void playSong(long queueItemId) {
        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra(MusicPlayerService.EXTRA_QUEUE_ITEM_ID, queueItemId);
        startService(intent);
    }

    private void togglePlayPause() {
        Intent intent = new Intent(this, MusicPlayerService.class);
        if (playerService != null && playerService.getPlayState() == PlayState.PLAYING) {
            intent.setAction(MusicPlayerService.ACTION_PAUSE);
        } else {
            intent.setAction(MusicPlayerService.ACTION_PLAY);
        }
        startService(intent);
    }

    private void playPrevious() {
        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PREVIOUS);
        startService(intent);
    }

    private void playNext() {
        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_NEXT);
        startService(intent);
    }

    private void updateQueueDisplay() {
        queueList.clear();
        queueList.addAll(queueManager.getQueue());
        queueAdapter.notifyDataSetChanged();
    }

    // MusicPlayerService.PlaybackListener
    @Override
    public void onPlaybackStateChanged(PlayState state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateNowPlaying();
            }
        });
    }

    @Override
    public void onProgressChanged(int currentPosition, int duration) {
        // Update progress bar and time display
        if (duration > 0) {
            int progress = (int) ((currentPosition * 100.0) / duration);
            progressSeekBar.setProgress(progress);
            currentPositionTextView.setText(formatTime(currentPosition));
            durationTextView.setText(formatTime(duration));
        }

        // Update lyrics
        if (playerService != null) {
            int currentLine = playerService.getCurrentLyricLine();
            lyricsView.setCurrentLine(currentLine);
        }
    }

    @Override
    public void onQueueChanged(List<QueueItem> queue) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateQueueDisplay();
            }
        });
    }

    private void updateNowPlaying() {
        QueueItem currentItem = queueManager.getCurrent();
        if (currentItem != null && currentItem.getSong() != null) {
            Song song = currentItem.getSong();

            songTitleTextView.setText(song.getTitle());
            songArtistTextView.setText(song.getArtist());

            // Load album cover
            String coverPath = song.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty()) {
                Glide.with(this)
                        .load(coverPath)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(albumCoverImageView);
            } else {
                albumCoverImageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Load lyrics
            if (playerService != null) {
                Lyrics lyrics = playerService.getLyrics();
                lyricsView.setLyrics(lyrics);

                // Update play/pause button
                PlayState state = playerService.getPlayState();
                playPauseButton.setImageResource(state == PlayState.PLAYING ?
                        android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            if (playerService != null) {
                playerService.removePlaybackListener(this);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时重新加载音源列表（可能已在设置页更新）
        loadMusicSources();
        updateQueueDisplay();
        updateNowPlaying();
    }

    /**
     * 更新状态栏
     */
    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusTextView != null) {
                    statusTextView.setText(status);
                }
            }
        });
    }

    /**
     * 格式化时间（毫秒转换为 M:SS 格式）
     */
    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
