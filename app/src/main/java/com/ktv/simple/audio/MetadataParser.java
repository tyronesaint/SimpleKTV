package com.ktv.simple.audio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.ktv.simple.cache.CacheManager;
import com.ktv.simple.model.Song;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Audio metadata parser using Android MediaMetadataRetriever
 * Extracts ID3 tags (title, artist, album, cover)
 */
public class MetadataParser {
    private static final String TAG = "MetadataParser";
    private static Context applicationContext;

    /**
     * Initialize with application context
     */
    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
    }

    /**
     * Parse metadata from local file
     */
    public static void parseMetadata(Song song, File file) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            parseMetadata(song, fis, file.getAbsolutePath());
            fis.close();

        } catch (Exception e) {
            Log.e(TAG, "Parse metadata error", e);
        }
    }

    /**
     * Parse metadata from input stream
     */
    public static void parseMetadata(Song song, InputStream inputStream, String sourcePath) {
        if (song == null || sourcePath == null || sourcePath.isEmpty()) {
            return;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            // Set data source
            retriever.setDataSource(sourcePath);

            // Extract title
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title != null && !title.isEmpty()) {
                song.setTitle(title);
            }

            // Extract artist
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist != null && !artist.isEmpty()) {
                song.setArtist(artist);
            }

            // Extract album
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (album != null && !album.isEmpty()) {
                song.setAlbum(album);
            }

            // Extract album artwork
            try {
                byte[] artworkData = retriever.getEmbeddedPicture();
                if (artworkData != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.length);
                    if (bitmap != null) {
                        // Save artwork to temporary file and set as cover path
                        String coverPath = saveBitmapToTemp(bitmap, song.getId());
                        if (coverPath != null) {
                            song.setCoverPath(coverPath);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract artwork", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse metadata error", e);
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.w(TAG, "Failed to release retriever", e);
            }
        }
    }

    /**
     * Load LRC lyrics file
     */
    public static String findLrcFile(String mp3Path) {
        if (mp3Path == null || mp3Path.isEmpty()) {
            return null;
        }

        try {
            // Replace .mp3 extension with .lrc (Java 7 compatible)
            String lrcPath;
            int lastDot = mp3Path.lastIndexOf('.');
            if (lastDot > 0) {
                String baseName = mp3Path.substring(0, lastDot);
                String extension = mp3Path.substring(lastDot).toLowerCase();
                if (extension.equals(".mp3")) {
                    lrcPath = baseName + ".lrc";
                } else {
                    lrcPath = mp3Path + ".lrc";
                }
            } else {
                lrcPath = mp3Path + ".lrc";
            }

            File lrcFile = new File(lrcPath);

            if (lrcFile.exists() && lrcFile.canRead()) {
                return lrcPath;
            }

        } catch (Exception e) {
            Log.e(TAG, "Find LRC file error", e);
        }

        return null;
    }

    /**
     * Save bitmap to cache
     */
    private static String saveBitmapToTemp(Bitmap bitmap, long songId) {
        if (applicationContext == null) {
            Log.w(TAG, "Application context not initialized");
            return null;
        }

        try {
            // Save to temp file first
            java.io.File tempDir = new java.io.File(
                    applicationContext.getCacheDir(),
                    "temp_cover"
            );

            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            java.io.File tempFile = new java.io.File(tempDir, "cover_" + songId + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Add to cache manager
            CacheManager cacheManager = CacheManager.getInstance(applicationContext);
            File cachedFile = cacheManager.addImageToCache(songId, tempFile);

            // Clean up temp file
            tempFile.delete();

            if (cachedFile != null) {
                Log.d(TAG, "Cover saved to cache: " + cachedFile.getAbsolutePath());
                return cachedFile.getAbsolutePath();
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "Save bitmap error", e);
            return null;
        }
    }

    /**
     * Get default cover drawable resource
     */
    public static int getDefaultCover() {
        return android.R.drawable.ic_menu_gallery;
    }
}
