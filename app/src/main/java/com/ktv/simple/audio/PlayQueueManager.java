package com.ktv.simple.audio;

import android.util.Log;

import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Play queue manager for managing the play queue
 */
public class PlayQueueManager {
    private static final String TAG = "PlayQueueManager";

    private List<QueueItem> queue;
    private int currentIndex; // Index of currently playing song
    private static PlayQueueManager instance;

    private PlayQueueManager() {
        this.queue = new ArrayList<>();
        this.currentIndex = -1;
    }

    public static synchronized PlayQueueManager getInstance() {
        if (instance == null) {
            instance = new PlayQueueManager();
        }
        return instance;
    }

    /**
     * Add song to queue
     */
    public QueueItem addToQueue(Song song) {
        if (song == null) {
            return null;
        }

        QueueItem item = new QueueItem(
                System.currentTimeMillis(),
                song
        );
        item.setPosition(queue.size());

        queue.add(item);
        Log.i(TAG, "Added song to queue: " + song.getTitle());

        return item;
    }

    /**
     * Add songs to queue
     */
    public void addToQueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return;
        }

        for (Song song : songs) {
            addToQueue(song);
        }
    }

    /**
     * Remove song from queue by index
     */
    public boolean removeFromQueue(int index) {
        if (index < 0 || index >= queue.size()) {
            return false;
        }

        queue.remove(index);

        // Update positions
        for (int i = index; i < queue.size(); i++) {
            queue.get(i).setPosition(i);
        }

        // Adjust current index if needed
        if (currentIndex >= queue.size()) {
            currentIndex = queue.size() - 1;
        } else if (currentIndex >= index) {
            currentIndex = currentIndex - 1;
        }

        Log.i(TAG, "Removed song from queue at index: " + index);
        return true;
    }

    /**
     * Remove song from queue by ID
     */
    public boolean removeFromQueueById(long queueItemId) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == queueItemId) {
                return removeFromQueue(i);
            }
        }
        return false;
    }

    /**
     * Move song to top of queue
     */
    public boolean moveToTop(int index) {
        if (index <= 0 || index >= queue.size()) {
            return false;
        }

        QueueItem item = queue.remove(index);
        queue.add(0, item);

        // Update positions
        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setPosition(i);
        }

        // Adjust current index
        if (currentIndex == index) {
            currentIndex = 0;
        } else if (currentIndex < index) {
            currentIndex = currentIndex + 1;
        }

        Log.i(TAG, "Moved song to top: " + item.getSong().getTitle());
        return true;
    }

    /**
     * Get next song in queue
     */
    public QueueItem getNext() {
        if (currentIndex < queue.size() - 1) {
            currentIndex++;
            return queue.get(currentIndex);
        }
        return null; // No next song
    }

    /**
     * Check if has next song
     */
    public boolean hasNext() {
        return currentIndex < queue.size() - 1;
    }

    /**
     * Get previous song in queue
     */
    public QueueItem getPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            return queue.get(currentIndex);
        }
        return null; // No previous song
    }

    /**
     * Check if has previous song
     */
    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    /**
     * Get currently playing song
     */
    public QueueItem getCurrent() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    /**
     * Get song at specific index
     */
    public QueueItem get(int index) {
        if (index >= 0 && index < queue.size()) {
            return queue.get(index);
        }
        return null;
    }

    /**
     * Get all songs in queue
     */
    public List<QueueItem> getQueue() {
        return new ArrayList<>(queue);
    }

    /**
     * Get queue size
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Clear queue
     */
    public void clearQueue() {
        queue.clear();
        currentIndex = -1;
        Log.i(TAG, "Queue cleared");
    }

    /**
     * Set current index
     */
    public void setCurrentIndex(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
        }
    }

    /**
     * Get current index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Jump to specific index and start playing
     */
    public boolean jumpTo(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            Log.i(TAG, "Jumped to index: " + index);
            return true;
        }
        return false;
    }

    /**
     * Move to next song (advance index)
     */
    public void next() {
        if (hasNext()) {
            currentIndex++;
            Log.i(TAG, "Moved to next index: " + currentIndex);
        }
    }

    /**
     * Move to previous song (retreat index)
     */
    public void previous() {
        if (hasPrevious()) {
            currentIndex--;
            Log.i(TAG, "Moved to previous index: " + currentIndex);
        }
    }
}
