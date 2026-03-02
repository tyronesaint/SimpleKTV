package com.ktv.simple.storage;

import com.ktv.simple.model.Song;

import java.util.List;

/**
 * Interface for storage providers (Local, WebDAV, FTP, SMB)
 */
public interface StorageProvider {
    /**
     * Connect to the storage
     */
    boolean connect() throws Exception;

    /**
     * Disconnect from storage
     */
    void disconnect();

    /**
     * Check if connected
     */
    boolean isConnected();

    /**
     * Load all MP3 files from the storage
     */
    List<Song> loadSongs() throws Exception;

    /**
     * Search songs by keyword (title or artist)
     */
    List<Song> searchSongs(String keyword) throws Exception;

    /**
     * Get storage type
     */
    Song.StorageType getStorageType();

    /**
     * Get storage description
     */
    String getDescription();
}
