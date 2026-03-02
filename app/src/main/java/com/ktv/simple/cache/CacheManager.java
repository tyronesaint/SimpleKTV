package com.ktv.simple.cache;

import android.content.Context;
import android.util.Log;

import com.ktv.simple.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache manager for managing audio and image caches
 * Implements LRU (Least Recently Used) eviction policy
 */
public class CacheManager {
    private static final String TAG = "CacheManager";
    private static CacheManager instance;

    private Context context;
    private File audioCacheDir;
    private File imageCacheDir;
    private long maxCacheSize = 100 * 1024 * 1024; // 100MB default
    private long maxAge = 30 * 24 * 60 * 60 * 1000; // 30 days

    private Map<Long, CacheEntry> cacheEntries;

    private CacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.cacheEntries = new HashMap<>();

        // Initialize cache directories
        audioCacheDir = new File(context.getCacheDir(), "audio_cache");
        if (!audioCacheDir.exists()) {
            audioCacheDir.mkdirs();
        }

        imageCacheDir = new File(context.getExternalCacheDir(), "image_cache");
        if (!imageCacheDir.exists()) {
            imageCacheDir.mkdirs();
        }

        // Load cache info from SharedPreferences
        loadCacheInfo();
    }

    public static synchronized CacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new CacheManager(context);
        }
        return instance;
    }

    /**
     * Get or create cache entry for a song
     */
    public File getCachedAudioFile(Song song) {
        if (song == null) {
            return null;
        }

        CacheEntry entry = cacheEntries.get(song.getId());
        if (entry != null && entry.file.exists()) {
            // Update last used time
            entry.lastUsed = System.currentTimeMillis();
            saveCacheInfo();
            Log.d(TAG, "Cache hit for song: " + song.getTitle());
            return entry.file;
        }

        return null;
    }

    /**
     * Add audio file to cache
     */
    public File addAudioToCache(Song song, File sourceFile) {
        if (song == null || sourceFile == null || !sourceFile.exists()) {
            return null;
        }

        String fileName = "song_" + song.getId() + ".mp3";
        File cacheFile = new File(audioCacheDir, fileName);

        // Copy file to cache
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            fis.close();
            fos.close();

            // Add to cache entries
            CacheEntry entry = new CacheEntry();
            entry.songId = song.getId();
            entry.file = cacheFile;
            entry.size = totalBytes;
            entry.created = System.currentTimeMillis();
            entry.lastUsed = System.currentTimeMillis();
            entry.type = CacheType.AUDIO;

            cacheEntries.put(song.getId(), entry);
            saveCacheInfo();

            Log.i(TAG, "Added to cache: " + song.getTitle() + " (" + totalBytes + " bytes)");

            // Check if we need to cleanup
            cleanupIfNeeded();

            return cacheFile;

        } catch (Exception e) {
            Log.e(TAG, "Add audio to cache error", e);
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            return null;
        }
    }

    /**
     * Add image to cache (for album covers)
     */
    public File addImageToCache(long songId, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }

        String fileName = "cover_" + songId + ".jpg";
        File cacheFile = new File(imageCacheDir, fileName);

        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fis.close();
            fos.close();

            // Add to cache entries
            CacheEntry entry = new CacheEntry();
            entry.songId = songId;
            entry.file = cacheFile;
            entry.size = cacheFile.length();
            entry.created = System.currentTimeMillis();
            entry.lastUsed = System.currentTimeMillis();
            entry.type = CacheType.IMAGE;

            cacheEntries.put(songId, entry);
            saveCacheInfo();

            Log.i(TAG, "Added image to cache: song_" + songId);

            // Check if we need to cleanup
            cleanupIfNeeded();

            return cacheFile;

        } catch (Exception e) {
            Log.e(TAG, "Add image to cache error", e);
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            return null;
        }
    }

    /**
     * Get cached image file
     */
    public File getCachedImageFile(long songId) {
        CacheEntry entry = cacheEntries.get(songId);
        if (entry != null && entry.file.exists() && entry.type == CacheType.IMAGE) {
            entry.lastUsed = System.currentTimeMillis();
            saveCacheInfo();
            return entry.file;
        }
        return null;
    }

    /**
     * Cleanup cache if needed
     */
    private void cleanupIfNeeded() {
        long currentSize = getCurrentCacheSize();
        if (currentSize > maxCacheSize) {
            Log.i(TAG, "Cache size limit reached (" + currentSize + " > " + maxCacheSize + "), starting cleanup");
            cleanupLRU();
        }

        // Also cleanup old files
        cleanupOldFiles();
    }

    /**
     * Cleanup using LRU (Least Recently Used) policy
     */
    private void cleanupLRU() {
        List<CacheEntry> entries = new ArrayList<>(cacheEntries.values());
        Collections.sort(entries, new Comparator<CacheEntry>() {
            @Override
            public int compare(CacheEntry e1, CacheEntry e2) {
                return Long.compare(e1.lastUsed, e2.lastUsed);
            }
        });

        long targetSize = (long) (maxCacheSize * 0.8); // Cleanup to 80% of max size
        long currentSize = getCurrentCacheSize();

        for (CacheEntry entry : entries) {
            if (currentSize <= targetSize) {
                break;
            }

            if (entry.file.exists()) {
                entry.file.delete();
                currentSize -= entry.size;
                cacheEntries.remove(entry.songId);
                Log.d(TAG, "Removed from cache: " + entry.file.getName());
            }
        }

        saveCacheInfo();
        Log.i(TAG, "LRU cleanup completed, new size: " + getCurrentCacheSize());
    }

    /**
     * Cleanup files older than maxAge
     */
    private void cleanupOldFiles() {
        long currentTime = System.currentTimeMillis();
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, CacheEntry> entry : cacheEntries.entrySet()) {
            if (currentTime - entry.getValue().lastUsed > maxAge) {
                toRemove.add(entry.getKey());
            }
        }

        for (Long songId : toRemove) {
            CacheEntry entry = cacheEntries.get(songId);
            if (entry.file.exists()) {
                entry.file.delete();
                Log.d(TAG, "Removed old cache file: " + entry.file.getName());
            }
            cacheEntries.remove(songId);
        }

        if (!toRemove.isEmpty()) {
            saveCacheInfo();
            Log.i(TAG, "Cleaned up " + toRemove.size() + " old cache files");
        }
    }

    /**
     * Get total cache size
     */
    public long getCurrentCacheSize() {
        long totalSize = 0;
        for (CacheEntry entry : cacheEntries.values()) {
            if (entry.file.exists()) {
                totalSize += entry.size;
            }
        }
        return totalSize;
    }

    /**
     * Get cache size formatted as string
     */
    public String getCacheSizeFormatted() {
        long size = getCurrentCacheSize();
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * Clear all cache
     */
    public void clearCache() {
        long totalSize = getCurrentCacheSize();
        int fileCount = 0;

        for (CacheEntry entry : cacheEntries.values()) {
            if (entry.file.exists()) {
                entry.file.delete();
                fileCount++;
            }
        }

        cacheEntries.clear();
        saveCacheInfo();

        Log.i(TAG, "Cleared " + fileCount + " cache files (" + totalSize + " bytes)");
    }

    /**
     * Clear audio cache only
     */
    public void clearAudioCache() {
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, CacheEntry> entry : cacheEntries.entrySet()) {
            if (entry.getValue().type == CacheType.AUDIO && entry.getValue().file.exists()) {
                entry.getValue().file.delete();
                toRemove.add(entry.getKey());
            }
        }

        for (Long songId : toRemove) {
            cacheEntries.remove(songId);
        }

        saveCacheInfo();
    }

    /**
     * Clear image cache only
     */
    public void clearImageCache() {
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, CacheEntry> entry : cacheEntries.entrySet()) {
            if (entry.getValue().type == CacheType.IMAGE && entry.getValue().file.exists()) {
                entry.getValue().file.delete();
                toRemove.add(entry.getKey());
            }
        }

        for (Long songId : toRemove) {
            cacheEntries.remove(songId);
        }

        saveCacheInfo();
    }

    /**
     * Set maximum cache size
     */
    public void setMaxCacheSize(long maxSize) {
        this.maxCacheSize = maxSize;
        cleanupIfNeeded();
    }

    /**
     * Get maximum cache size
     */
    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Set maximum cache age
     */
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
        cleanupOldFiles();
    }

    /**
     * Load cache info from SharedPreferences
     */
    private void loadCacheInfo() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("cache_info", Context.MODE_PRIVATE);
            String cacheData = prefs.getString("cache_entries", "");

            if (!cacheData.isEmpty()) {
                // Parse JSON or simple format
                // For simplicity, we'll rebuild from actual files
                rebuildCacheInfo();
            } else {
                rebuildCacheInfo();
            }
        } catch (Exception e) {
            Log.e(TAG, "Load cache info error", e);
            rebuildCacheInfo();
        }
    }

    /**
     * Rebuild cache info from actual files
     */
    private void rebuildCacheInfo() {
        cacheEntries.clear();

        // Scan audio cache directory
        File[] audioFiles = audioCacheDir.listFiles();
        if (audioFiles != null) {
            for (File file : audioFiles) {
                if (file.isFile() && file.getName().endsWith(".mp3")) {
                    try {
                        long songId = extractSongId(file.getName());
                        if (songId > 0) {
                            CacheEntry entry = new CacheEntry();
                            entry.songId = songId;
                            entry.file = file;
                            entry.size = file.length();
                            entry.created = file.lastModified();
                            entry.lastUsed = file.lastModified();
                            entry.type = CacheType.AUDIO;
                            cacheEntries.put(songId, entry);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse audio cache file: " + file.getName());
                    }
                }
            }
        }

        // Scan image cache directory
        if (imageCacheDir != null) {
            File[] imageFiles = imageCacheDir.listFiles();
            if (imageFiles != null) {
                for (File file : imageFiles) {
                    if (file.isFile() && file.getName().endsWith(".jpg")) {
                        try {
                            long songId = extractSongId(file.getName());
                            if (songId > 0) {
                                CacheEntry entry = new CacheEntry();
                                entry.songId = songId;
                                entry.file = file;
                                entry.size = file.length();
                                entry.created = file.lastModified();
                                entry.lastUsed = file.lastModified();
                                entry.type = CacheType.IMAGE;
                                cacheEntries.put(songId, entry);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse image cache file: " + file.getName());
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Rebuilt cache info: " + cacheEntries.size() + " entries");
    }

    /**
     * Extract song ID from file name
     */
    private long extractSongId(String fileName) {
        try {
            // Format: song_123456789.mp3 or cover_123456789.jpg
            String[] parts = fileName.split("_|\\.");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract song ID from: " + fileName);
        }
        return -1;
    }

    /**
     * Save cache info to SharedPreferences
     */
    private void saveCacheInfo() {
        // For simplicity, we'll just save basic info
        // In production, use JSON serialization
        android.content.SharedPreferences prefs = context.getSharedPreferences("cache_info", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("last_update", System.currentTimeMillis());
        editor.putInt("cache_count", cacheEntries.size());
        editor.putLong("cache_size", getCurrentCacheSize());
        editor.apply();
    }

    /**
     * Cache entry class
     */
    private static class CacheEntry {
        long songId;
        File file;
        long size;
        long created;
        long lastUsed;
        CacheType type;
    }

    /**
     * Cache type enum
     */
    private enum CacheType {
        AUDIO,
        IMAGE
    }
}
