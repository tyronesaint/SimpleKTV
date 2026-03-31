package com.ktv.simple.storage;

import android.content.Context;
import android.os.Environment;

import com.ktv.simple.audio.MetadataParser;
import com.ktv.simple.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local storage provider for reading MP3 files from device storage
 */
public class LocalStorageProvider implements StorageProvider {
    private Context context;
    private File rootDirectory;
    private boolean connected;

    public LocalStorageProvider(Context context) {
        this.context = context;
        this.connected = false;
    }

    public LocalStorageProvider(Context context, String path) {
        this.context = context;
        this.rootDirectory = new File(path);
        this.connected = false;
    }

    @Override
    public boolean connect() {
        if (rootDirectory == null || !rootDirectory.exists()) {
            // Use default music directory
            rootDirectory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
            );
        }

        if (rootDirectory == null || !rootDirectory.exists()) {
            // Fallback to external storage root
            rootDirectory = Environment.getExternalStorageDirectory();
        }

        connected = rootDirectory != null && rootDirectory.exists() && rootDirectory.canRead();
        return connected;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public List<Song> loadSongs() throws Exception {
        List<Song> songs = new ArrayList<>();
        if (!connected) {
            throw new Exception("Storage not connected");
        }

        if (rootDirectory != null && rootDirectory.exists()) {
            scanDirectory(rootDirectory, songs);
        }

        // Sort by file name
        Collections.sort(songs, new Comparator<Song>() {
            @Override
            public int compare(Song s1, Song s2) {
                return s1.getTitle().compareToIgnoreCase(s2.getTitle());
            }
        });

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
        return Song.StorageType.LOCAL;
    }

    @Override
    public String getDescription() {
        if (rootDirectory != null) {
            return "Local: " + rootDirectory.getAbsolutePath();
        }
        return "Local Storage";
    }

    /**
     * Recursively scan directory for MP3 files
     */
    private void scanDirectory(File directory, List<Song> songs) {
        if (directory == null || !directory.canRead()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Skip hidden directories
                if (!file.getName().startsWith(".")) {
                    scanDirectory(file, songs);
                }
            } else if (file.isFile() && isMp3File(file)) {
                // Use file name as default title
                String title = file.getName().replace(".mp3", "").replace(".MP3", "");
                Song song = new Song(
                        System.currentTimeMillis() + file.hashCode(),
                        title,
                        "Unknown Artist",
                        file.getAbsolutePath(),
                        Song.StorageType.LOCAL
                );

                // Parse metadata from the file
                MetadataParser.parseMetadata(song, file);

                songs.add(song);
            }
        }
    }

    /**
     * Check if file is MP3
     */
    private boolean isMp3File(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3");
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String path) {
        this.rootDirectory = new File(path);
    }
}
