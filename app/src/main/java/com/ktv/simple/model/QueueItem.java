package com.ktv.simple.model;

import java.io.Serializable;

/**
 * Play queue item model
 */
public class QueueItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private Song song;
    private long addedTime; // Timestamp when added to queue
    private int position;   // Position in queue

    public QueueItem() {
        this.addedTime = System.currentTimeMillis();
    }

    public QueueItem(long id, Song song) {
        this.id = id;
        this.song = song;
        this.addedTime = System.currentTimeMillis();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public long getAddedTime() {
        return addedTime;
    }

    public void setAddedTime(long addedTime) {
        this.addedTime = addedTime;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "QueueItem{" +
                "id=" + id +
                ", song=" + (song != null ? song.getTitle() : "null") +
                ", position=" + position +
                '}';
    }
}
