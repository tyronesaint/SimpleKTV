package com.ktv.simple.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.ktv.simple.model.MusicInfo;
import com.ktv.simple.model.MusicSource;
import com.ktv.simple.model.MusicUrlData;
import com.ktv.simple.model.PlatformResult;
import com.ktv.simple.model.SearchData;
import com.ktv.simple.model.SearchResponse;
import com.ktv.simple.model.Song;
import com.ktv.simple.model.SongResult;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 在线音乐服务 API 封装
 * 支持动态音源获取、搜索、播放地址获取等功能
 * 
 * API 接口：
 * 1. 获取音源列表: GET /{apiKey}/api/scripts/loaded
 * 2. 搜索: GET /{apiKey}/api/search?keyword=xxx&source=kw
 * 3. 获取播放地址: POST /{apiKey}/api/music/url （同时返回歌词）
 * 4. 获取歌词: POST /{apiKey}/api/music/lyric
 * 5. 获取封面: POST /{apiKey}/api/music/pic
 */
public class OnlineMusicService {
    private static final String TAG = "OnlineMusicService";

    private static OnlineMusicService instance;
    private Context context;
    private OkHttpClient client;
    private Gson gson;

    private String baseUrl = "";
    private String apiKey = "";
    private List<MusicSource> musicSources;

    // 回调接口
    public interface SourcesCallback {
        void onSuccess(List<MusicSource> sources);
        void onError(String message);
    }

    public interface SearchCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    /**
     * 播放地址回调 - 返回完整数据（包含歌词）
     */
    public interface MusicUrlCallback {
        void onSuccess(MusicUrlData data);
        void onError(String message);
    }

    public interface LyricCallback {
        void onSuccess(String lyric, String tlyric);
        void onError(String message);
    }

    public interface CoverCallback {
        void onSuccess(String coverUrl);
        void onError(String message);
    }

    private OnlineMusicService(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.musicSources = new ArrayList<>();
        initHttpClient();
        loadSavedConfig();
    }

    public static synchronized OnlineMusicService getInstance(Context context) {
        if (instance == null) {
            instance = new OnlineMusicService(context);
        }
        return instance;
    }

    /**
     * 初始化 HTTP 客户端
     * 配置 TLS 1.2 以兼容 Android 4.4
     */
    private void initHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) 
                        throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) 
                        throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });

            client = builder.build();
            Log.i(TAG, "HTTP client initialized with TLS 1.2");

        } catch (Exception e) {
            Log.e(TAG, "Failed to init HTTP client", e);
            client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        }
    }

    /**
     * 加载已保存的配置
     */
    private void loadSavedConfig() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString("base_url", "");
        this.apiKey = prefs.getString("api_key", "");
        
        // 加载已保存的音源列表
        String sourcesJson = prefs.getString("music_sources", "");
        if (!sourcesJson.isEmpty()) {
            try {
                musicSources = gson.fromJson(sourcesJson, new TypeToken<List<MusicSource>>(){}.getType());
            } catch (Exception e) {
                Log.e(TAG, "Failed to load saved sources", e);
            }
        }
    }

    /**
     * 解析管理地址
     * 输入格式: https://example.com/abc123/admin 或 https://example.com/abc123
     * 输出: baseUrl=https://example.com, apiKey=abc123
     * 
     * @param adminUrl 用户输入的完整管理地址
     * @return 是否解析成功
     */
    public boolean parseAdminUrl(String adminUrl) {
        if (adminUrl == null || adminUrl.isEmpty()) {
            return false;
        }

        try {
            // 清理输入
            adminUrl = adminUrl.trim();
            if (!adminUrl.startsWith("http://") && !adminUrl.startsWith("https://")) {
                adminUrl = "https://" + adminUrl;
            }

            URL url = new URL(adminUrl);
            this.baseUrl = url.getProtocol() + "://" + url.getHost();
            
            // 如果有端口，添加端口
            if (url.getPort() != -1 && url.getPort() != url.getDefaultPort()) {
                this.baseUrl += ":" + url.getPort();
            }

            // 解析路径获取 apiKey
            // 路径格式: /{apiKey}/admin 或 /{apiKey}
            String path = url.getPath();
            if (path != null && path.length() > 1) {
                String[] parts = path.split("/");
                // parts[0] = "", parts[1] = apiKey, parts[2] = "admin" (可能存在)
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    this.apiKey = parts[1];
                    Log.i(TAG, "Parsed adminUrl: baseUrl=" + baseUrl + ", apiKey=" + apiKey);
                    return true;
                }
            }

            Log.w(TAG, "Failed to parse apiKey from path: " + path);
            return false;

        } catch (MalformedURLException e) {
            Log.e(TAG, "Invalid admin URL: " + adminUrl, e);
            return false;
        }
    }

    /**
     * 更新配置
     */
    public void updateConfig(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        saveConfig();
    }

    /**
     * 从管理地址更新配置（自动解析）
     */
    public boolean updateConfigFromAdminUrl(String adminUrl) {
        if (parseAdminUrl(adminUrl)) {
            saveConfig();
            return true;
        }
        return false;
    }

    /**
     * 保存配置到 SharedPreferences
     */
    public void saveConfig() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("base_url", baseUrl);
        editor.putString("api_key", apiKey);
        if (!musicSources.isEmpty()) {
            editor.putString("music_sources", gson.toJson(musicSources));
        }
        editor.apply();
    }

    /**
     * 获取音源列表
     * GET /{apiKey}/api/scripts/loaded
     */
    public void getMusicSources(final SourcesCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("请先配置 API 地址");
            return;
        }

        String url = baseUrl + "/" + apiKey + "/api/scripts/loaded";
        Log.i(TAG, "Getting music sources: " + url);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Get sources failed", e);
                callback.onError("获取音源列表失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Sources response: " + body);

                if (!response.isSuccessful()) {
                    callback.onError("服务器错误: " + response.code());
                    return;
                }

                try {
                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    int code = json.get("code").getAsInt();
                    
                    if (code != 200) {
                        String msg = json.has("msg") ? json.get("msg").getAsString() : "未知错误";
                        callback.onError(msg);
                        return;
                    }

                    JsonArray dataArray = json.getAsJsonArray("data");
                    musicSources.clear();

                    for (JsonElement element : dataArray) {
                        JsonObject sourceObj = element.getAsJsonObject();
                        MusicSource source = new MusicSource();
                        
                        source.setId(sourceObj.has("id") ? sourceObj.get("id").getAsString() : "");
                        source.setName(sourceObj.has("name") ? sourceObj.get("name").getAsString() : "");
                        source.setDescription(sourceObj.has("description") ? 
                            sourceObj.get("description").getAsString() : "");
                        source.setDefault(sourceObj.has("isDefault") && 
                            sourceObj.get("isDefault").getAsBoolean());

                        // 解析 supportedSources
                        if (sourceObj.has("supportedSources")) {
                            JsonArray sourcesArray = sourceObj.getAsJsonArray("supportedSources");
                            List<String> supportedSources = new ArrayList<>();
                            for (JsonElement src : sourcesArray) {
                                supportedSources.add(src.getAsString());
                            }
                            source.setSupportedSources(supportedSources);
                        }

                        musicSources.add(source);
                    }

                    // 保存音源列表
                    saveConfig();

                    Log.i(TAG, "Loaded " + musicSources.size() + " music sources");
                    callback.onSuccess(musicSources);

                } catch (Exception e) {
                    Log.e(TAG, "Parse sources failed", e);
                    callback.onError("解析音源列表失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 获取缓存的音源列表
     */
    public List<MusicSource> getCachedMusicSources() {
        return musicSources;
    }

    /**
     * 获取默认音源
     */
    public MusicSource getDefaultSource() {
        for (MusicSource source : musicSources) {
            if (source.isDefault()) {
                return source;
            }
        }
        return musicSources.isEmpty() ? null : musicSources.get(0);
    }

    /**
     * 搜索歌曲
     * GET /{apiKey}/api/search?keyword=xxx&source=kw
     * 
     * @param keyword 搜索关键词
     * @param source 平台标识（从音源列表获取，如 "kw"）
     * @param page 页码
     * @param pageSize 每页数量
     */
    public void search(String keyword, String source, int page, int pageSize, 
                       final SearchCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("请先配置 API 地址");
            return;
        }

        if (keyword == null || keyword.isEmpty()) {
            callback.onError("请输入搜索关键词");
            return;
        }

        // 构建 URL
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("/").append(apiKey);
        urlBuilder.append("/api/search?");
        urlBuilder.append("keyword=").append(urlEncode(keyword));
        urlBuilder.append("&source=").append(source);
        urlBuilder.append("&page=").append(page);
        urlBuilder.append("&pageSize=").append(pageSize);

        final String url = urlBuilder.toString();
        Log.i(TAG, "Searching: " + url);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Search network failed", e);
                String errorDetail = "搜索失败\n";
                errorDetail += "原因: " + e.getClass().getSimpleName() + "\n";
                errorDetail += "详情: " + e.getMessage();
                callback.onError(errorDetail);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Search response (first 500 chars): " + body.substring(0, Math.min(500, body.length())));

                if (!response.isSuccessful()) {
                    String errorDetail = "服务器错误\n";
                    errorDetail += "HTTP 状态码: " + response.code() + "\n";
                    errorDetail += "URL: " + url;
                    callback.onError(errorDetail);
                    return;
                }

                try {
                    // 使用 Gson 解析完整响应
                    SearchResponse searchResponse = gson.fromJson(body, SearchResponse.class);
                    
                    if (searchResponse == null) {
                        callback.onError("解析失败: 响应为空");
                        return;
                    }
                    
                    if (!searchResponse.isSuccess()) {
                        String errorDetail = "搜索失败\n";
                        errorDetail += "错误码: " + searchResponse.code + "\n";
                        errorDetail += "错误信息: " + searchResponse.getErrorMessage();
                        callback.onError(errorDetail);
                        return;
                    }

                    if (searchResponse.data == null) {
                        callback.onError("解析失败: data 字段为空");
                        return;
                    }

                    // 从嵌套结构中提取所有歌曲
                    List<Song> songs = new ArrayList<>();
                    List<SongResult> allSongResults = searchResponse.data.getAllSongs();
                    
                    Log.i(TAG, "Search keyword: " + searchResponse.data.keyword);
                    Log.i(TAG, "Total songs found: " + searchResponse.data.getTotalCount());
                    
                    for (SongResult songResult : allSongResults) {
                        Song song = songResult.toSong();
                        songs.add(song);
                        
                        Log.d(TAG, "Song: " + songResult.singer + " - " + songResult.name 
                            + " [" + songResult.getFormattedDuration() + "]"
                            + " source=" + songResult.source + " id=" + songResult.id);
                    }

                    if (songs.isEmpty()) {
                        callback.onError("未找到匹配的歌曲");
                        return;
                    }

                    Log.i(TAG, "Parsed " + songs.size() + " songs successfully");
                    callback.onSuccess(songs);

                } catch (Exception e) {
                    Log.e(TAG, "Parse search results failed", e);
                    String errorDetail = "解析搜索结果失败\n";
                    errorDetail += "异常类型: " + e.getClass().getSimpleName() + "\n";
                    errorDetail += "详情: " + e.getMessage() + "\n";
                    errorDetail += "响应内容 (前200字符): " + body.substring(0, Math.min(200, body.length()));
                    callback.onError(errorDetail);
                }
            }
        });
    }

    /**
     * 解析歌曲对象
     */
    private Song parseSong(JsonObject songObj) {
        Song song = new Song();
        
        // 从搜索结果中提取 source 和 id（songId）
        song.setSource(songObj.has("source") ? songObj.get("source").getAsString() : "");
        song.setSongId(songObj.has("id") ? songObj.get("id").getAsString() : 
                      (songObj.has("songId") ? songObj.get("songId").getAsString() : ""));
        
        song.setTitle(songObj.has("title") ? songObj.get("title").getAsString() : 
                     (songObj.has("name") ? songObj.get("name").getAsString() : ""));
        song.setArtist(songObj.has("artist") ? songObj.get("artist").getAsString() : 
                      (songObj.has("singer") ? songObj.get("singer").getAsString() : ""));
        song.setAlbum(songObj.has("album") ? songObj.get("album").getAsString() : "");
        
        // 封面图片
        if (songObj.has("cover")) {
            song.setCoverPath(songObj.get("cover").getAsString());
        } else if (songObj.has("pic")) {
            song.setCoverPath(songObj.get("pic").getAsString());
        } else if (songObj.has("pic_url")) {
            song.setCoverPath(songObj.get("pic_url").getAsString());
        }

        song.setStorageType(Song.StorageType.ONLINE);
        
        return song;
    }

    /**
     * 获取播放地址
     * POST /{apiKey}/api/music/url
     * Body: { "source": "kw", "songId": "123456", "quality": "320k" }
     * 
     * 注意：Android 4.4 仅支持 MP3 格式，quality 参数使用 128k/320k（均为 MP3）
     * 不请求 flac 格式
     */
    /**
     * 获取播放地址（同时返回歌词）
     * POST /{apiKey}/api/music/url
     * Body: { "source": "kw", "songId": "123456", "quality": "320k" }
     * 
     * 响应包含：
     * - url: 播放地址
     * - lyric: 标准LRC歌词
     * - tlyric: 翻译歌词
     * - type/quality/source: 元信息
     * 
     * 注意：Android 4.4 仅支持 MP3 格式，quality 参数使用 128k/320k（均为 MP3）
     */
    public void getMusicUrl(String source, String songId, String quality,
                            final MusicUrlCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("请先配置 API 地址");
            return;
        }

        final String url = baseUrl + "/" + apiKey + "/api/music/url";
        Log.i(TAG, "Getting music URL: " + url + " source=" + source + " songId=" + songId);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("source", source);
        bodyJson.addProperty("songId", songId);
        
        // 确保只请求 MP3 格式（128k 或 320k），不请求 flac
        if (quality == null || quality.isEmpty() || "flac".equalsIgnoreCase(quality)) {
            quality = "320k"; // 默认使用 320k MP3
        }
        bodyJson.addProperty("quality", quality);

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            bodyJson.toString()
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Get music URL failed", e);
                String errorDetail = "获取播放地址失败\n";
                errorDetail += "原因: " + e.getClass().getSimpleName() + "\n";
                errorDetail += "详情: " + e.getMessage();
                callback.onError(errorDetail);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Music URL response length: " + body.length());

                if (!response.isSuccessful()) {
                    String errorDetail = "服务器错误\n";
                    errorDetail += "HTTP 状态码: " + response.code() + "\n";
                    errorDetail += "URL: " + url;
                    callback.onError(errorDetail);
                    return;
                }

                try {
                    // 使用 Gson 解析完整响应
                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    int code = json.get("code").getAsInt();
                    
                    if (code != 200) {
                        String msg = json.has("msg") ? json.get("msg").getAsString() : "未知错误";
                        String errorDetail = "获取播放地址失败\n";
                        errorDetail += "错误码: " + code + "\n";
                        errorDetail += "错误信息: " + msg;
                        callback.onError(errorDetail);
                        return;
                    }

                    // 解析 data 字段为 MusicUrlData
                    JsonObject dataObj = json.getAsJsonObject("data");
                    MusicUrlData data = gson.fromJson(dataObj, MusicUrlData.class);

                    if (data == null || data.url == null || data.url.isEmpty()) {
                        callback.onError("未获取到播放地址");
                        return;
                    }

                    Log.i(TAG, "Got music URL: " + data.url.substring(0, Math.min(50, data.url.length())) + "...");
                    Log.i(TAG, "Has lyric: " + data.hasLyric() + ", type: " + data.type + ", quality: " + data.quality);
                    
                    callback.onSuccess(data);

                } catch (Exception e) {
                    Log.e(TAG, "Parse music URL failed", e);
                    String errorDetail = "解析播放地址失败\n";
                    errorDetail += "异常类型: " + e.getClass().getSimpleName() + "\n";
                    errorDetail += "详情: " + e.getMessage() + "\n";
                    errorDetail += "响应内容 (前200字符): " + body.substring(0, Math.min(200, body.length()));
                    callback.onError(errorDetail);
                }
            }
        });
    }

    /**
     * 获取歌词
     * POST /{apiKey}/api/music/lyric
     * Body: { "source": "kw", "songId": "123456" }
     */
    public void getLyric(String source, String songId, final LyricCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("请先配置 API 地址");
            return;
        }

        String url = baseUrl + "/" + apiKey + "/api/music/lyric";
        Log.i(TAG, "Getting lyric: " + url);

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("source", source);
        bodyJson.addProperty("songId", songId);

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            bodyJson.toString()
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Get lyric failed", e);
                callback.onError("获取歌词失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Lyric response length: " + body.length());

                if (!response.isSuccessful()) {
                    callback.onError("服务器错误: " + response.code());
                    return;
                }

                try {
                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    int code = json.get("code").getAsInt();
                    
                    if (code != 200) {
                        String msg = json.has("msg") ? json.get("msg").getAsString() : "未知错误";
                        callback.onError(msg);
                        return;
                    }

                    JsonObject data = json.getAsJsonObject("data");
                    String lyric = data.has("lrc") ? data.get("lrc").getAsString() : "";
                    String tlyric = data.has("tlyric") ? data.get("tlyric").getAsString() : "";

                    callback.onSuccess(lyric, tlyric);

                } catch (Exception e) {
                    Log.e(TAG, "Parse lyric failed", e);
                    callback.onError("解析歌词失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 获取封面图片
     * POST /{apiKey}/api/music/pic
     * Body: { "source": "kw", "songId": "123456" }
     */
    public void getAlbumCover(String source, String songId, final CoverCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("请先配置 API 地址");
            return;
        }

        String url = baseUrl + "/" + apiKey + "/api/music/pic";
        Log.i(TAG, "Getting cover: " + url);

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("source", source);
        bodyJson.addProperty("songId", songId);

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            bodyJson.toString()
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Get cover failed", e);
                callback.onError("获取封面失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Cover response: " + body);

                if (!response.isSuccessful()) {
                    callback.onError("服务器错误: " + response.code());
                    return;
                }

                try {
                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    int code = json.get("code").getAsInt();
                    
                    if (code != 200) {
                        String msg = json.has("msg") ? json.get("msg").getAsString() : "未知错误";
                        callback.onError(msg);
                        return;
                    }

                    JsonObject data = json.getAsJsonObject("data");
                    String coverUrl = data.has("url") ? data.get("url").getAsString() : "";

                    if (!coverUrl.isEmpty()) {
                        Log.i(TAG, "Got cover URL: " + coverUrl);
                        callback.onSuccess(coverUrl);
                    } else {
                        callback.onError("未获取到封面地址");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Parse cover failed", e);
                    callback.onError("解析封面失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * URL 编码
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty();
    }
}
