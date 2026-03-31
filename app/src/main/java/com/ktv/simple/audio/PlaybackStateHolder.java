package com.ktv.simple.audio;

import com.ktv.simple.model.PlayState;

/**
 * 全局播放状态持有者
 * 用于在 MusicPlayerService 和 HttpControlService 之间共享播放状态
 */
public class PlaybackStateHolder {
    private static PlaybackStateHolder instance;
    
    private PlayState playState = PlayState.IDLE;
    private int currentPosition = 0;
    private int duration = 0;
    private String currentSongTitle = "";
    private String currentSongArtist = "";
    
    private PlaybackStateHolder() {
    }
    
    public static synchronized PlaybackStateHolder getInstance() {
        if (instance == null) {
            instance = new PlaybackStateHolder();
        }
        return instance;
    }
    
    public void setPlayState(PlayState state) {
        this.playState = state;
    }
    
    public PlayState getPlayState() {
        return playState;
    }
    
    public boolean isPlaying() {
        return playState == PlayState.PLAYING;
    }
    
    public void setPosition(int position, int duration) {
        this.currentPosition = position;
        this.duration = duration;
    }
    
    public int getCurrentPosition() {
        return currentPosition;
    }
    
    public int getDuration() {
        return duration;
    }
    
    public void setCurrentSong(String title, String artist) {
        this.currentSongTitle = title != null ? title : "";
        this.currentSongArtist = artist != null ? artist : "";
    }
    
    public String getCurrentSongTitle() {
        return currentSongTitle;
    }
    
    public String getCurrentSongArtist() {
        return currentSongArtist;
    }
    
    /**
     * 获取播放进度百分比
     */
    public int getProgressPercent() {
        if (duration <= 0) return 0;
        return (int) ((currentPosition * 100.0) / duration);
    }
    
    /**
     * 格式化当前播放时间
     */
    public String getFormattedCurrentPosition() {
        return formatTime(currentPosition);
    }
    
    /**
     * 格式化总时长
     */
    public String getFormattedDuration() {
        return formatTime(duration);
    }
    
    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
