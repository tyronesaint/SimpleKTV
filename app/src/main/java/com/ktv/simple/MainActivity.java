package com.ktv.simple;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.ktv.simple.adapter.QueueAdapter;
import com.ktv.simple.adapter.SongAdapter;
import com.ktv.simple.audio.PlayQueueManager;
import com.ktv.simple.audio.MetadataParser;
import com.ktv.simple.model.Lyrics;
import com.ktv.simple.model.PlayState;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;
import com.ktv.simple.service.HttpControlService;
import com.ktv.simple.service.MusicPlayerService;
import com.ktv.simple.storage.LocalStorageProvider;
import com.ktv.simple.storage.StorageProvider;
import com.ktv.simple.util.QrCodeGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Activity for SimpleKTV
 */
public class MainActivity extends AppCompatActivity implements
        SongAdapter.OnItemClickListener,
        QueueAdapter.OnItemClickListener,
        MusicPlayerService.PlaybackListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_STORAGE = 1001;

    // UI Components
    private EditText searchEditText;
    private Button searchButton;
    private ImageButton settingsButton;
    private Button libraryButton;
    private Button queueButton;
    private Button remoteButton;
    private RecyclerView songRecyclerView;
    private RecyclerView queueRecyclerView;
    private ScrollView remoteScrollView;
    private TextView storageTypeTextView;

    private TextView songTitleTextView;
    private TextView songArtistTextView;
    private ImageView albumCoverImageView;
    private ImageButton previousButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private com.ktv.simple.view.LyricsView lyricsView;
    private com.ktv.simple.view.VolumeVisualizerView visualizerView;
    private ImageView qrCodeImageView;
    private TextView serverUrlTextView;

    // Adapters
    private SongAdapter songAdapter;
    private QueueAdapter queueAdapter;

    // Data
    private List<Song> allSongs;
    private List<QueueItem> queueList;
    private StorageProvider storageProvider;
    private PlayQueueManager queueManager;

    // Services
    private MusicPlayerService playerService;
    private HttpControlService httpService;
    private boolean serviceBound = false;

    // Service Connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize MetadataParser with application context
        com.ktv.simple.audio.MetadataParser.init(this);

        initViews();
        initData();
        setupListeners();

        // Check and request permissions
        checkPermissions();
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
        storageTypeTextView = findViewById(R.id.storageTypeTextView);

        songTitleTextView = findViewById(R.id.songTitleTextView);
        songArtistTextView = findViewById(R.id.songArtistTextView);
        albumCoverImageView = findViewById(R.id.albumCoverImageView);
        previousButton = findViewById(R.id.previousButton);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextButton = findViewById(R.id.nextButton);
        lyricsView = findViewById(R.id.lyricsView);
        visualizerView = findViewById(R.id.visualizerView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        serverUrlTextView = findViewById(R.id.serverUrlTextView);
    }

    private void initData() {
        allSongs = new ArrayList<>();
        queueList = new ArrayList<>();
        queueManager = PlayQueueManager.getInstance();

        // Setup Song RecyclerView
        songRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(this, allSongs, this);
        songRecyclerView.setAdapter(songAdapter);

        // Setup Queue RecyclerView
        queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        queueAdapter = new QueueAdapter(this, queueList, this);
        queueRecyclerView.setAdapter(queueAdapter);

        // Initialize storage provider from settings
        storageProvider = createStorageProvider();
    }

    /**
     * Create storage provider based on user settings
     */
    private StorageProvider createStorageProvider() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String storageType = prefs.getString("storage_type", "local");

        if ("webdav".equals(storageType)) {
            String url = prefs.getString("webdav_url", "");
            String username = prefs.getString("webdav_username", "");
            String password = prefs.getString("webdav_password", "");
            if (!url.isEmpty()) {
                return new com.ktv.simple.storage.WebDavStorageProvider(url, username, password);
            }
        } else if ("ftp".equals(storageType)) {
            String server = prefs.getString("ftp_server", "");
            String portStr = prefs.getString("ftp_port", "21");
            String username = prefs.getString("ftp_username", "");
            String password = prefs.getString("ftp_password", "");
            if (!server.isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr);
                    return new com.ktv.simple.storage.FtpStorageProvider(server, port, username, password);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        } else if ("smb".equals(storageType)) {
            String server = prefs.getString("smb_server", "");
            String share = prefs.getString("smb_share", "");
            String username = prefs.getString("smb_username", "");
            String password = prefs.getString("smb_password", "");
            if (!server.isEmpty() && !share.isEmpty()) {
                String smbUrl = "smb://" + server + "/" + share;
                return new com.ktv.simple.storage.SmbStorageProvider(smbUrl, username, password);
            }
        }

        // Default to local storage
        String localPath = prefs.getString("local_path", "");
        if (!localPath.isEmpty()) {
            return new LocalStorageProvider(this, localPath);
        } else {
            return new LocalStorageProvider(this);
        }
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

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
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
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_STORAGE);
        } else {
            loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadSongs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Recreate storage provider with latest settings
                    storageProvider = createStorageProvider();
                    storageProvider.connect();
                    final List<Song> songs = storageProvider.loadSongs();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            allSongs.clear();
                            allSongs.addAll(songs);
                            songAdapter.notifyDataSetChanged();

                            Log.d(TAG, "Loaded " + songs.size() + " songs");

                            // Update storage type indicator
                            updateStorageTypeIndicator();

                            // Update HTTP control service library
                            if (httpService != null) {
                                httpService.setSongLibrary(allSongs);
                                Log.d(TAG, "Song library updated to HTTP service");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Failed to load songs: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void performSearch() {
        final String keyword = searchEditText.getText().toString().trim();
        if (keyword.isEmpty()) {
            songAdapter.setSongs(allSongs);
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final List<Song> results = storageProvider.searchSongs(keyword);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                songAdapter.setSongs(results);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
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

        serverUrlTextView.setText("Server: " + serverUrl);
    }

    // SongAdapter.OnItemClickListener
    @Override
    public void onSongClick(Song song) {
        // Click on song to play directly
        queueManager.clearQueue();
        QueueItem item = queueManager.addToQueue(song);
        updateQueueDisplay();

        // Start playing immediately
        if (item != null) {
            playSong(item.getId());
            Toast.makeText(this, "Playing: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAddToQueueClick(Song song) {
        // Click on add button to add to queue without playing
        QueueItem item = queueManager.addToQueue(song);
        updateQueueDisplay();
        Toast.makeText(this, "Added to queue: " + song.getTitle(), Toast.LENGTH_SHORT).show();
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
        intent.setAction(playerService != null &&
                playerService.getPlayState() == PlayState.PLAYING ?
                MusicPlayerService.ACTION_PAUSE : MusicPlayerService.ACTION_PLAY);
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
        // Update lyrics
        int currentLine = playerService.getCurrentLyricLine();
        lyricsView.setCurrentLine(currentLine);

        // Simulate visualizer
        float volume = (float) currentPosition / duration;
        visualizerView.updateVolume(volume);
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
            if (song.getCoverPath() != null) {
                Glide.with(this)
                        .load(new File(song.getCoverPath()))
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(albumCoverImageView);
            }

            // Load lyrics
            Lyrics lyrics = playerService.getLyrics();
            lyricsView.setLyrics(lyrics);

            // Update play/pause button
            PlayState state = playerService.getPlayState();
            playPauseButton.setImageResource(state == PlayState.PLAYING ?
                    android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
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
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void updateStorageTypeIndicator() {
        if (storageProvider == null || storageTypeTextView == null) {
            return;
        }

        String storageType = "本地存储";
        if (storageProvider.getStorageType() == Song.StorageType.WEBDAV) {
            storageType = "WebDAV";
        } else if (storageProvider.getStorageType() == Song.StorageType.FTP) {
            storageType = "FTP";
        } else if (storageProvider.getStorageType() == Song.StorageType.SMB) {
            storageType = "SMB";
        }

        storageTypeTextView.setText("当前存储：" + storageType + " | 歌曲数：" + allSongs.size());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update storage type indicator
        updateStorageTypeIndicator();

        // Reload songs when returning from settings
        // Check if storage type has changed
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String currentStorageType = prefs.getString("storage_type", "local");

        if (storageProvider != null) {
            String providerType = storageProvider.getStorageType() == Song.StorageType.LOCAL ? "local" :
                                  storageProvider.getStorageType() == Song.StorageType.WEBDAV ? "webdav" :
                                  storageProvider.getStorageType() == Song.StorageType.FTP ? "ftp" : "smb";

            if (!currentStorageType.equals(providerType)) {
                // Storage type changed, reload songs
                Log.d(TAG, "Storage type changed, reloading songs");
                loadSongs();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (httpService != null) {
            httpService.stop();
        }
    }
}
