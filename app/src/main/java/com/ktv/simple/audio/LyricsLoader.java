package com.ktv.simple.audio;

import android.util.Log;

import com.ktv.simple.model.Lyrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lyrics loader for LRC format
 */
public class LyricsLoader {
    private static final String TAG = "LyricsLoader";

    /**
     * Load lyrics from file path
     */
    public static Lyrics loadFromFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return new Lyrics();
        }

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Lyrics file not found or cannot read: " + filePath);
            return new Lyrics();
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            Lyrics lyrics = Lyrics.parse(fis);
            fis.close();

            if (lyrics.hasLyrics()) {
                Log.i(TAG, "Loaded " + lyrics.getLineCount() + " lyric lines");
            }

            return lyrics;

        } catch (Exception e) {
            Log.e(TAG, "Load lyrics error", e);
            return new Lyrics();
        }
    }

    /**
     * Load lyrics from input stream
     */
    public static Lyrics loadFromStream(InputStream inputStream) {
        if (inputStream == null) {
            return new Lyrics();
        }

        try {
            return Lyrics.parse(inputStream);

        } catch (Exception e) {
            Log.e(TAG, "Load lyrics from stream error", e);
            return new Lyrics();
        }
    }

    /**
     * Check if lyrics file exists
     */
    public static boolean lyricsFileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(filePath);
        return file.exists() && file.canRead();
    }
}
