package com.ktv.simple.model;

/**
 * Playback state enumeration
 */
public enum PlayState {
    IDLE,       // Not playing, nothing loaded
    PREPARING,  // Preparing to play
    PLAYING,    // Currently playing
    PAUSED,     // Paused
    COMPLETED,  // Song finished
    ERROR       // Error occurred
}
