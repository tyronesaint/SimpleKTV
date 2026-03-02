package com.ktv.simple.storage;

import android.util.Log;

import com.ktv.simple.model.Song;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * WebDAV storage provider using OkHttp
 */
public class WebDavStorageProvider implements StorageProvider {
    private static final String TAG = "WebDavStorageProvider";

    private String baseUrl;
    private String username;
    private String password;
    private OkHttpClient httpClient;
    private boolean connected;

    public WebDavStorageProvider(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.connected = false;
    }

    @Override
    public boolean connect() throws Exception {
        // Create OkHttp client with TLS 1.2 support for Android 4.4
        try {
            httpClient = new OkHttpClient.Builder()
                    .build();

            // Test connection
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Basic " + getAuthHeader())
                    .header("Depth", "1")
                    .build();

            Response response = httpClient.newCall(request).execute();
            connected = response.isSuccessful();

            if (!connected) {
                Log.e(TAG, "WebDAV connection failed: " + response.code());
            }

            return connected;
        } catch (Exception e) {
            Log.e(TAG, "WebDAV connect error", e);
            throw new Exception("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public List<Song> loadSongs() throws Exception {
        List<Song> songs = new ArrayList<>();

        try {
            // Perform PROPFIND request to list files
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Basic " + getAuthHeader())
                    .header("Depth", "1")
                    .method("PROPFIND", null)
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("PROPFIND failed: " + response.code());
            }

            String responseBody = response.body().string();
            parseWebDavResponse(responseBody, songs);

        } catch (Exception e) {
            Log.e(TAG, "Load songs error", e);
            throw new Exception("Failed to load songs: " + e.getMessage());
        }

        return songs;
    }

    @Override
    public List<Song> searchSongs(String keyword) throws Exception {
        List<Song> allSongs = loadSongs();
        List<Song> results = new ArrayList<>();

        String lowerKeyword = keyword.toLowerCase().trim();
        for (Song song : allSongs) {
            if (song.getTitle() != null && song.getTitle().toLowerCase().contains(lowerKeyword) ||
                song.getArtist() != null && song.getArtist().toLowerCase().contains(lowerKeyword)) {
                results.add(song);
            }
        }

        return results;
    }

    @Override
    public Song.StorageType getStorageType() {
        return Song.StorageType.WEBDAV;
    }

    @Override
    public String getDescription() {
        return "WebDAV: " + baseUrl;
    }

    /**
     * Parse WebDAV PROPFIND response
     */
    private void parseWebDavResponse(String xml, List<Song> songs) {
        // Simple XML parsing for href elements
        Pattern hrefPattern = Pattern.compile("<D:href>([^<]+)</D:href>");
        Matcher matcher = hrefPattern.matcher(xml);

        while (matcher.find()) {
            String href = matcher.group(1);
            // Decode URL
            try {
                href = java.net.URLDecoder.decode(href, "UTF-8");
            } catch (Exception e) {
                continue;
            }

            // Check if it's an MP3 file
            if (href.toLowerCase().endsWith(".mp3")) {
                String fileName = href.substring(href.lastIndexOf('/') + 1);
                String title = fileName.replace(".mp3", "").replace(".MP3", "");

                // Build full URL
                String fullUrl = baseUrl;
                if (!baseUrl.endsWith("/")) {
                    fullUrl += "/";
                }
                fullUrl += fileName;

                Song song = new Song(
                        System.currentTimeMillis() + fileName.hashCode(),
                        title,
                        "Unknown Artist",
                        fullUrl,
                        Song.StorageType.WEBDAV
                );
                songs.add(song);
            }
        }
    }

    /**
     * Get Basic Auth header
     */
    private String getAuthHeader() {
        String credentials = username + ":" + password;
        return android.util.Base64.encodeToString(
                credentials.getBytes(),
                android.util.Base64.NO_WRAP
        );
    }

    /**
     * Open input stream for a file
     */
    public InputStream openFile(String filePath) throws IOException {
        Request request = new Request.Builder()
                .url(filePath)
                .header("Authorization", "Basic " + getAuthHeader())
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to open file: " + response.code());
        }

        return response.body().byteStream();
    }
}
