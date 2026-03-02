package com.ktv.simple.storage;

import android.util.Log;

import com.ktv.simple.model.Song;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jcifs.Config;
import jcifs.UniAddress;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * SMB storage provider using jcifs 1.3.17
 */
public class SmbStorageProvider implements StorageProvider {
    private static final String TAG = "SmbStorageProvider";

    private String serverUrl;
    private String username;
    private String password;
    private NtlmPasswordAuthentication auth;
    private boolean connected;

    public SmbStorageProvider(String serverUrl, String username, String password) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.connected = false;
    }

    @Override
    public boolean connect() throws Exception {
        try {
            // Configure jcifs for Android 4.4 compatibility
            jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
            jcifs.Config.setProperty("jcifs.smb.client.responseTimeout", "30000");
            jcifs.Config.setProperty("jcifs.netbios.hostname", "android");

            // Create authentication
            auth = new NtlmPasswordAuthentication(null, username, password);

            // Set default authentication
            jcifs.Config.setProperty("jcifs.smb.client.username", username);
            jcifs.Config.setProperty("jcifs.smb.client.password", password);

            // Test connection by listing files
            SmbFile smbFile = new SmbFile(serverUrl, auth);
            connected = smbFile.exists();

            if (!connected) {
                Log.e(TAG, "SMB connection failed: path does not exist");
            }

            return connected;

        } catch (Exception e) {
            Log.e(TAG, "SMB connect error", e);
            throw new Exception("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        auth = null;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public List<Song> loadSongs() throws Exception {
        List<Song> songs = new ArrayList<>();

        try {
            SmbFile smbDir = new SmbFile(serverUrl, auth);
            if (!smbDir.isDirectory()) {
                throw new Exception("Not a directory");
            }

            scanDirectory(smbDir, songs);

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
        return Song.StorageType.SMB;
    }

    @Override
    public String getDescription() {
        return "SMB: " + serverUrl;
    }

    /**
     * Recursively scan directory for MP3 files
     */
    private void scanDirectory(SmbFile directory, List<Song> songs) throws SmbException {
        if (!directory.canRead()) {
            return;
        }

        SmbFile[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (SmbFile file : files) {
            if (file.isDirectory()) {
                // Skip hidden directories
                if (!file.getName().startsWith(".")) {
                    scanDirectory(file, songs);
                }
            } else if (file.isFile() && isMp3File(file)) {
                String title = file.getName().replace(".mp3", "").replace(".MP3", "");
                Song song = new Song(
                        System.currentTimeMillis() + file.getName().hashCode(),
                        title,
                        "Unknown Artist",
                        file.getPath(),
                        Song.StorageType.SMB
                );
                songs.add(song);
            }
        }
    }

    /**
     * Check if file is MP3
     */
    private boolean isMp3File(SmbFile file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3");
    }

    /**
     * Open input stream for a file
     */
    public InputStream openFile(String filePath) throws Exception {
        SmbFile smbFile = new SmbFile(filePath, auth);
        return new SmbFileInputStream(smbFile);
    }
}
