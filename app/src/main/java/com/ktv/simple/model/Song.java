package com.ktv.simple.model;

import java.io.Serializable;

/**
 * Song model representing a music track
 * 支持本地存储和在线音乐服务
 */
public class Song implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String title;
    private String artist;
    private String album;
    private String filePath;
    private String coverPath; // URL or file path to album cover
    private String lyricsPath; // Path to lyrics file
    private String duration; // Duration string (e.g., "3:45")
    private long durationMs; // Duration in milliseconds
    private StorageType storageType;
    
    // 在线音乐服务字段
    private String source;     // 音源：kw/kg/tx/wy/mg
    private String songId;     // 歌曲ID（在线服务使用）
    private String musicUrl;   // 播放地址
    private String quality;    // 音质：128k/320k/flac

    public enum StorageType {
        LOCAL,
        WEBDAV,
        FTP,
        SMB,
        ONLINE    // 在线音乐服务
    }

    public Song() {
    }

    public Song(long id, String title, String artist, String filePath, StorageType storageType) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.filePath = filePath;
        this.storageType = storageType;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public String getLyricsPath() {
        return lyricsPath;
    }

    public void setLyricsPath(String lyricsPath) {
        this.lyricsPath = lyricsPath;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        this.duration = formatDuration(durationMs);
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    // 在线音乐服务字段

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getMusicUrl() {
        return musicUrl;
    }

    public void setMusicUrl(String musicUrl) {
        this.musicUrl = musicUrl;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    /**
     * Format duration in milliseconds to MM:SS format
     */
    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0:00";
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 判断是否是在线歌曲
     */
    public boolean isOnline() {
        return storageType == StorageType.ONLINE;
    }

    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", source='" + source + '\'' +
                ", songId='" + songId + '\'' +
                ", duration='" + duration + '\'' +
                ", storageType=" + storageType +
                '}';
    }
}
