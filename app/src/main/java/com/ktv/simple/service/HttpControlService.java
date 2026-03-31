package com.ktv.simple.service;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktv.simple.api.OnlineMusicService;
import com.ktv.simple.audio.PlayQueueManager;
import com.ktv.simple.audio.PlaybackStateHolder;
import com.ktv.simple.model.MusicSource;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP control service for remote control
 * Uses NanoHTTPD to provide REST API and web interface
 */
public class HttpControlService extends NanoHTTPD {
    private static final String TAG = "HttpControlService";
    private static final int DEFAULT_PORT = 8080;

    private Context context;
    private Gson gson;
    private PlayQueueManager queueManager;
    private List<Song> songLibrary;

    public HttpControlService(Context context, int port) {
        super(port);
        this.context = context;
        this.gson = new Gson();
        this.queueManager = PlayQueueManager.getInstance();
        this.songLibrary = new ArrayList<>();
    }

    /**
     * Set song library for search
     */
    public void setSongLibrary(List<Song> songs) {
        this.songLibrary = new ArrayList<>(songs);
        Log.i(TAG, "Song library updated: " + songs.size() + " songs");
    }

    /**
     * Start the HTTP server
     */
    public boolean startService() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.i(TAG, "HTTP server started on port " + getListeningPort());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start HTTP server", e);
            return false;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        try {
            // Serve remote control page for root
            if (uri.equals("/") || uri.equals("/index.html")) {
                return serveRemotePage();
            }

            // API endpoints
            if (uri.startsWith("/api/")) {
                Response response = handleApiRequest(session);
                // Add CORS headers
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                response.addHeader("Access-Control-Allow-Headers", "Content-Type");
                return response;
            }

            // Handle OPTIONS preflight request
            if (Method.OPTIONS.equals(method)) {
                Response response = newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                response.addHeader("Access-Control-Allow-Headers", "Content-Type");
                return response;
            }

            // Default to index
            return serveStaticFile("remote.html", "text/html");

        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT, "Internal server error");
        }
    }

    /**
     * Handle API requests
     */
    private Response handleApiRequest(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            // GET /api/queue - Get play queue
            if (uri.equals("/api/queue") && Method.GET.equals(method)) {
                return getQueue();
            }

            // POST /api/queue/add - Add song to queue
            if (uri.equals("/api/queue/add") && Method.POST.equals(method)) {
                return addToQueue(session);
            }

            // POST /api/queue/add-online - Add online song to queue (from web remote)
            if (uri.equals("/api/queue/add-online") && Method.POST.equals(method)) {
                return addOnlineSongToQueue(session);
            }

            // POST /api/queue/remove - Remove song from queue
            if (uri.equals("/api/queue/remove") && Method.POST.equals(method)) {
                return removeFromQueue(session);
            }

            // POST /api/queue/move - Move song in queue
            if (uri.equals("/api/queue/move") && Method.POST.equals(method)) {
                return moveInQueue(session);
            }

            // GET /api/status - Get playback status
            if (uri.equals("/api/status") && Method.GET.equals(method)) {
                return getStatus();
            }

            // GET /api/config - Get server config for web remote search
            if (uri.equals("/api/config") && Method.GET.equals(method)) {
                return getConfig();
            }

            // POST /api/control/seek - Seek to position
            if (uri.equals("/api/control/seek") && Method.POST.equals(method)) {
                return controlSeek(session);
            }

            // POST /api/control/play - Play
            if (uri.equals("/api/control/play") && Method.POST.equals(method)) {
                return controlPlay();
            }

            // POST /api/control/pause - Pause
            if (uri.equals("/api/control/pause") && Method.POST.equals(method)) {
                return controlPause();
            }

            // POST /api/control/next - Next
            if (uri.equals("/api/control/next") && Method.POST.equals(method)) {
                return controlNext();
            }

            // POST /api/control/previous - Previous
            if (uri.equals("/api/control/previous") && Method.POST.equals(method)) {
                return controlPrevious();
            }

            // POST /api/control/jump - Jump to specific song
            if (uri.equals("/api/control/jump") && Method.POST.equals(method)) {
                return controlJump(session);
            }

            // GET /api/songs/search - Search songs in queue
            if (uri.equals("/api/songs/search") && Method.GET.equals(method)) {
                return searchSongs(session);
            }

            // GET /api/library/search - Search songs in library
            if (uri.equals("/api/library/search") && Method.GET.equals(method)) {
                return searchLibrary(session);
            }

            // GET /api/library/info - Get library info (debug)
            if (uri.equals("/api/library/info") && Method.GET.equals(method)) {
                return getLibraryInfo();
            }

            // GET /api/online/search - Online music search
            if (uri.equals("/api/online/search") && Method.GET.equals(method)) {
                return onlineSearch(session);
            }

            // GET /api/online/sources - Get available music sources
            if (uri.equals("/api/online/sources") && Method.GET.equals(method)) {
                return getOnlineSources();
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "API endpoint not found");

        } catch (Exception e) {
            Log.e(TAG, "API error", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT, "API error: " + e.getMessage());
        }
    }

    /**
     * GET /api/songs/search - Search songs
     */
    private Response searchSongs(IHTTPSession session) {
        try {
            Map<String, List<String>> params = session.getParameters();
            List<String> keywordList = params.get("keyword");

            if (keywordList != null && !keywordList.isEmpty()) {
                String keyword = keywordList.get(0);

                // Search in queue
                List<QueueItem> queue = queueManager.getQueue();
                List<Map<String, Object>> results = new ArrayList<>();

                String lowerKeyword = keyword.toLowerCase();
                for (QueueItem item : queue) {
                    Song song = item.getSong();
                    if (song != null) {
                        boolean matchTitle = song.getTitle() != null &&
                                song.getTitle().toLowerCase().contains(lowerKeyword);
                        boolean matchArtist = song.getArtist() != null &&
                                song.getArtist().toLowerCase().contains(lowerKeyword);

                        if (matchTitle || matchArtist) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", item.getId());
                            map.put("position", item.getPosition());
                            map.put("songId", song.getId());
                            map.put("title", song.getTitle());
                            map.put("artist", song.getArtist());
                            map.put("album", song.getAlbum());
                            results.add(map);
                        }
                    }
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("results", results);
                response.put("count", results.size());

                return jsonResponse(response);
            }

            return jsonResponse(mapOf("success", false, "error", "No keyword provided"));

        } catch (Exception e) {
            Log.e(TAG, "Search songs error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * GET /api/library/search - Search songs in library
     */
    private Response searchLibrary(IHTTPSession session) {
        try {
            Map<String, List<String>> params = session.getParameters();
            List<String> keywordList = params.get("keyword");

            Log.d(TAG, "Search library - keywordList: " + (keywordList != null ? keywordList.toString() : "null"));
            Log.d(TAG, "Song library size: " + songLibrary.size());

            if (keywordList != null && !keywordList.isEmpty()) {
                String keyword = keywordList.get(0);
                Log.d(TAG, "Search keyword: " + keyword);

                // Search in song library
                List<Map<String, Object>> results = new ArrayList<>();

                String lowerKeyword = keyword.toLowerCase();
                for (Song song : songLibrary) {
                    boolean matchTitle = song.getTitle() != null &&
                            song.getTitle().toLowerCase().contains(lowerKeyword);
                    boolean matchArtist = song.getArtist() != null &&
                            song.getArtist().toLowerCase().contains(lowerKeyword);

                    if (matchTitle || matchArtist) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("songId", song.getId());
                        map.put("title", song.getTitle());
                        map.put("artist", song.getArtist());
                        map.put("album", song.getAlbum());
                        results.add(map);
                    }
                }

                Log.d(TAG, "Search results: " + results.size());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("results", results);
                response.put("count", results.size());

                return jsonResponse(response);
            }

            return jsonResponse(mapOf("success", false, "error", "No keyword provided"));

        } catch (Exception e) {
            Log.e(TAG, "Search library error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * GET /api/library/info - Get library info
     */
    private Response getLibraryInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("librarySize", songLibrary.size());
        response.put("message", "Library contains " + songLibrary.size() + " songs");
        return jsonResponse(response);
    }

    /**
     * GET /api/queue
     */
    private Response getQueue() {
        List<QueueItem> queue = queueManager.getQueue();
        List<Map<String, Object>> result = new ArrayList<>();

        for (QueueItem item : queue) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("position", item.getPosition());

            Song song = item.getSong();
            if (song != null) {
                map.put("songId", song.getId());
                map.put("title", song.getTitle());
                map.put("artist", song.getArtist());
                map.put("album", song.getAlbum());
                map.put("duration", song.getDuration());
            }

            result.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("queue", result);
        response.put("currentPosition", queueManager.getCurrentIndex());

        return jsonResponse(response);
    }

    /**
     * POST /api/queue/add
     */
    private Response addToQueue(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                if (request.containsKey("songId")) {
                    long songId = ((Number) request.get("songId")).longValue();

                    // Find song from library
                    Song song = null;
                    for (Song libSong : songLibrary) {
                        if (libSong.getId() == songId) {
                            song = libSong;
                            break;
                        }
                    }

                    if (song != null) {
                        Log.d(TAG, "Adding song to queue: " + song.getTitle());
                        QueueItem item = queueManager.addToQueue(song);
                        if (item != null) {
                            return jsonResponse(mapOf("success", true, "message", "Added to queue"));
                        } else {
                            return jsonResponse(mapOf("success", false, "error", "Failed to add to queue"));
                        }
                    } else {
                        Log.w(TAG, "Song not found in library: " + songId);
                        return jsonResponse(mapOf("success", false, "error", "Song not found in library"));
                    }
                }
            }

            return jsonResponse(mapOf("success", false, "error", "Invalid request"));

        } catch (Exception e) {
            Log.e(TAG, "Add to queue error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * POST /api/queue/add-online - Add online song to queue
     * Called from web remote with song details already fetched
     * Body: { title, artist, album, source, songId, musicUrl, lyric, tlyric }
     */
    private Response addOnlineSongToQueue(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                // Create song from request data
                final Song song = new Song();
                song.setTitle(request.containsKey("title") ? (String) request.get("title") : "未知歌曲");
                song.setArtist(request.containsKey("artist") ? (String) request.get("artist") : "未知歌手");
                song.setAlbum(request.containsKey("album") ? (String) request.get("album") : "");
                song.setSource(request.containsKey("source") ? (String) request.get("source") : "");
                song.setSongId(request.containsKey("songId") ? (String) request.get("songId") : "");
                
                // URL directly from web (already resolved)
                if (request.containsKey("musicUrl")) {
                    song.setMusicUrl((String) request.get("musicUrl"));
                }
                
                song.setStorageType(Song.StorageType.ONLINE);

                Log.i(TAG, "Adding online song to queue: " + song.getTitle() + " - " + song.getArtist());
                
                // 处理歌词（从请求中获取）
                String lyricContent = request.containsKey("lyric") ? (String) request.get("lyric") : "";
                if (lyricContent != null && !lyricContent.isEmpty()) {
                    Log.i(TAG, "Got lyric from remote, length: " + lyricContent.length());
                    song.setLyricsPath("lyric://" + song.getSongId());
                    // 异步保存歌词
                    saveLyricAsync(song, lyricContent);
                }
                
                QueueItem item = queueManager.addToQueue(song);
                if (item != null) {
                    // Auto play if queue was empty
                    if (queueManager.getQueueSize() == 1) {
                        Intent intent = new Intent(context, MusicPlayerService.class);
                        intent.setAction(MusicPlayerService.ACTION_PLAY);
                        context.startService(intent);
                    }
                    return jsonResponse(mapOf("success", true, "message", "已添加到队列", "title", song.getTitle()));
                } else {
                    return jsonResponse(mapOf("success", false, "error", "添加失败"));
                }
            }

            return jsonResponse(mapOf("success", false, "error", "无效请求"));

        } catch (Exception e) {
            Log.e(TAG, "Add online song error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * 异步保存歌词到缓存
     */
    private void saveLyricAsync(final Song song, final String lyricContent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File cacheDir = context.getCacheDir();
                    java.io.File lyricFile = new java.io.File(cacheDir, "lyrics/" + song.getSongId() + ".lrc");
                    lyricFile.getParentFile().mkdirs();
                    
                    java.io.FileWriter writer = new java.io.FileWriter(lyricFile);
                    writer.write(lyricContent);
                    writer.close();
                    
                    Log.i(TAG, "Saved lyric to: " + lyricFile.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save lyric", e);
                }
            }
        }).start();
    }

    /**
     * POST /api/queue/remove
     */
    private Response removeFromQueue(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                if (request.containsKey("id")) {
                    long queueItemId = ((Number) request.get("id")).longValue();
                    boolean success = queueManager.removeFromQueueById(queueItemId);

                    return jsonResponse(mapOf("success", success));
                }
            }

            return jsonResponse(mapOf("success", false, "error", "Invalid request"));

        } catch (Exception e) {
            Log.e(TAG, "Remove from queue error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * POST /api/queue/move
     */
    private Response moveInQueue(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                if (request.containsKey("index")) {
                    int index = ((Number) request.get("index")).intValue();
                    boolean success = queueManager.moveToTop(index);

                    return jsonResponse(mapOf("success", success));
                }
            }

            return jsonResponse(mapOf("success", false, "error", "Invalid request"));

        } catch (Exception e) {
            Log.e(TAG, "Move in queue error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * GET /api/status
     */
    private Response getStatus() {
        PlaybackStateHolder stateHolder = PlaybackStateHolder.getInstance();
        QueueItem current = queueManager.getCurrent();
        Map<String, Object> response = new HashMap<>();

        if (current != null && current.getSong() != null) {
            Song song = current.getSong();
            response.put("currentSong", mapOf(
                    "id", song.getId(),
                    "title", song.getTitle(),
                    "artist", song.getArtist(),
                    "album", song.getAlbum()
            ));
        }

        // 从全局状态获取实际播放状态
        response.put("isPlaying", stateHolder.isPlaying());
        response.put("playState", stateHolder.getPlayState().name());
        response.put("queueSize", queueManager.getQueueSize());
        response.put("currentIndex", queueManager.getCurrentIndex());
        
        // 添加进度信息
        response.put("currentPosition", stateHolder.getCurrentPosition());
        response.put("duration", stateHolder.getDuration());
        response.put("progress", stateHolder.getProgressPercent());
        response.put("positionText", stateHolder.getFormattedCurrentPosition());
        response.put("durationText", stateHolder.getFormattedDuration());
        
        // 当前歌曲信息
        response.put("currentTitle", stateHolder.getCurrentSongTitle());
        response.put("currentArtist", stateHolder.getCurrentSongArtist());

        return jsonResponse(response);
    }

    /**
     * GET /api/config - Get server config for web remote search
     * Returns baseUrl and apiKey so web can directly call music API
     */
    private Response getConfig() {
        OnlineMusicService musicService = OnlineMusicService.getInstance(context);
        Map<String, Object> response = new HashMap<String, Object>();
        
        response.put("configured", musicService.isConfigured());
        response.put("baseUrl", musicService.getBaseUrl());
        response.put("apiKey", musicService.getApiKey());
        
        // Get available sources
        MusicSource defaultSource = musicService.getDefaultSource();
        if (defaultSource != null && defaultSource.getSupportedSources() != null) {
            response.put("defaultSourceId", defaultSource.getId());
            response.put("defaultSourceName", defaultSource.getName());
            response.put("supportedSources", defaultSource.getSupportedSources());
        }
        
        return jsonResponse(response);
    }

    /**
     * POST /api/control/play
     */
    private Response controlPlay() {
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        context.startService(intent);
        return jsonResponse(mapOf("success", true));
    }

    /**
     * POST /api/control/pause
     */
    private Response controlPause() {
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PAUSE);
        context.startService(intent);
        return jsonResponse(mapOf("success", true));
    }

    /**
     * POST /api/control/next
     */
    private Response controlNext() {
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_NEXT);
        context.startService(intent);
        return jsonResponse(mapOf("success", true));
    }

    /**
     * POST /api/control/previous
     */
    private Response controlPrevious() {
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PREVIOUS);
        context.startService(intent);
        return jsonResponse(mapOf("success", true));
    }

    /**
     * POST /api/control/seek
     * Body: { percent: 0-100 }
     */
    private Response controlSeek(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                if (request.containsKey("percent")) {
                    int percent = ((Number) request.get("percent")).intValue();
                    
                    // Get duration from state holder
                    PlaybackStateHolder stateHolder = PlaybackStateHolder.getInstance();
                    int duration = stateHolder.getDuration();
                    
                    if (duration > 0) {
                        int newPosition = (int) ((percent / 100.0) * duration);
                        
                        // Send seek intent
                        Intent intent = new Intent(context, MusicPlayerService.class);
                        intent.setAction(MusicPlayerService.ACTION_SEEK);
                        intent.putExtra("position", newPosition);
                        context.startService(intent);
                        
                        return jsonResponse(mapOf("success", true));
                    } else {
                        return jsonResponse(mapOf("success", false, "error", "No song playing"));
                    }
                }
            }

            return jsonResponse(mapOf("success", false, "error", "Invalid request"));

        } catch (Exception e) {
            Log.e(TAG, "Seek error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * POST /api/control/jump
     */
    private Response controlJump(IHTTPSession session) {
        try {
            Map<String, String> params = new HashMap<>();
            session.parseBody(params);
            String body = params.get("postData");

            if (body != null) {
                Map<String, Object> request = gson.fromJson(body,
                        new TypeToken<Map<String, Object>>(){}.getType());

                if (request.containsKey("index")) {
                    int index = ((Number) request.get("index")).intValue();
                    boolean success = queueManager.jumpTo(index);
                    if (success) {
                        Intent intent = new Intent(context, MusicPlayerService.class);
                        intent.setAction(MusicPlayerService.ACTION_PLAY);
                        context.startService(intent);
                    }

                    return jsonResponse(mapOf("success", success));
                }
            }

            return jsonResponse(mapOf("success", false, "error", "Invalid request"));

        } catch (Exception e) {
            Log.e(TAG, "Jump error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Serve static file from assets
     */
    private Response serveStaticFile(String fileName, String mimeType) {
        try {
            InputStream inputStream = context.getAssets().open(fileName);
            return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, inputStream.available());
        } catch (IOException e) {
            Log.e(TAG, "Failed to serve static file: " + fileName, e);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found");
        }
    }

    /**
     * Serve remote control HTML page from assets
     */
    private Response serveRemotePage() {
        InputStream is = null;
        BufferedReader reader = null;

        try {
            is = context.getAssets().open("remote.html");
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            Response response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", content.toString());
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;

        } catch (IOException e) {
            Log.e(TAG, "Error serving remote page", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT, "Error: " + e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    /**
     * Create JSON response
     */
    private Response jsonResponse(Object data) {
        String json = gson.toJson(data);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /**
     * Get device IP address
     */
    public String getIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();

            return String.format("%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));

        } catch (Exception e) {
            Log.e(TAG, "Get IP address error", e);
            return "127.0.0.1";
        }
    }

    /**
     * Get server URL
     */
    public String getServerUrl() {
        String ip = getIpAddress();
        int port = getListeningPort();
        return "http://" + ip + ":" + port;
    }

    /**
     * GET /api/online/sources - Get available music sources
     */
    private Response getOnlineSources() {
        OnlineMusicService musicService = OnlineMusicService.getInstance(context);
        List<MusicSource> sources = musicService.getCachedMusicSources();
        MusicSource defaultSource = musicService.getDefaultSource();
        
        List<Map<String, Object>> sourcesList = new ArrayList<Map<String, Object>>();
        
        if (defaultSource != null && defaultSource.getSupportedSources() != null) {
            for (String platform : defaultSource.getSupportedSources()) {
                Map<String, Object> sourceMap = new HashMap<String, Object>();
                sourceMap.put("id", platform);
                sourceMap.put("name", getPlatformName(platform));
                sourceMap.put("sourceName", defaultSource.getName());
                sourcesList.add(sourceMap);
            }
        }
        
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("success", true);
        response.put("sources", sourcesList);
        response.put("configured", musicService.isConfigured());
        
        return jsonResponse(response);
    }

    /**
     * GET /api/online/search - Online music search
     * Requires: keyword, source (optional, defaults to first available)
     */
    private Response onlineSearch(IHTTPSession session) {
        try {
            Map<String, List<String>> params = session.getParameters();
            
            // Check if configured
            final OnlineMusicService musicService = OnlineMusicService.getInstance(context);
            if (!musicService.isConfigured()) {
                return jsonResponse(mapOf("success", false, "error", "请先配置服务器地址"));
            }
            
            // Get keyword
            List<String> keywordList = params.get("keyword");
            if (keywordList == null || keywordList.isEmpty()) {
                return jsonResponse(mapOf("success", false, "error", "请输入搜索关键词"));
            }
            final String keyword = keywordList.get(0);
            
            // Get source (platform)
            String source = null;
            List<String> sourceList = params.get("source");
            if (sourceList != null && !sourceList.isEmpty()) {
                source = sourceList.get(0);
            } else {
                // Use first available source
                MusicSource defaultSource = musicService.getDefaultSource();
                if (defaultSource != null && defaultSource.getSupportedSources() != null 
                    && !defaultSource.getSupportedSources().isEmpty()) {
                    source = defaultSource.getSupportedSources().get(0);
                }
            }
            
            if (source == null) {
                return jsonResponse(mapOf("success", false, "error", "没有可用的音源"));
            }
            
            final String finalSource = source;
            
            // This is a synchronous wrapper for the async search
            // Since NanoHTTPD doesn't support async responses easily, we'll return a placeholder
            // and the client will need to use the main app for search
            
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("success", true);
            response.put("message", "请在手机应用中搜索");
            response.put("keyword", keyword);
            response.put("source", finalSource);
            response.put("note", "远程搜索功能需要在主应用中实现");
            
            return jsonResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Online search error", e);
            return jsonResponse(mapOf("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get platform display name
     */
    private String getPlatformName(String platform) {
        if ("kw".equals(platform)) return "酷我";
        if ("kg".equals(platform)) return "酷狗";
        if ("tx".equals(platform) || "qq".equals(platform)) return "QQ音乐";
        if ("wy".equals(platform)) return "网易云";
        if ("mg".equals(platform)) return "咪咕";
        return platform.toUpperCase();
    }

    // Java 7 compatible helper methods to replace Map.of()
    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        map.put(k7, v7);
        map.put(k8, v8);
        return map;
    }
}
