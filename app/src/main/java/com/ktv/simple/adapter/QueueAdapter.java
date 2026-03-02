package com.ktv.simple.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.ktv.simple.R;
import com.ktv.simple.model.QueueItem;
import com.ktv.simple.model.Song;

import java.util.List;

/**
 * Adapter for play queue
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    public static final int ACTION_PLAY = 1;
    public static final int ACTION_MOVE_TO_TOP = 2;
    public static final int ACTION_REMOVE = 3;

    private List<QueueItem> queue;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onQueueItemClick(QueueItem item, int action);
    }

    public QueueAdapter(com.ktv.simple.MainActivity context, List<QueueItem> queue, OnItemClickListener listener) {
        this.queue = queue;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final QueueItem item = queue.get(position);
        Song song = item.getSong();

        holder.positionTextView.setText(String.valueOf(position + 1));

        if (song != null) {
            holder.titleTextView.setText(song.getTitle());
            holder.artistTextView.setText(song.getArtist());
        } else {
            holder.titleTextView.setText("Unknown Song");
            holder.artistTextView.setText("Unknown Artist");
        }

        // Play button
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onQueueItemClick(item, ACTION_PLAY);
                }
            }
        });

        // Move to top button
        holder.moveToTopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onQueueItemClick(item, ACTION_MOVE_TO_TOP);
                }
            }
        });

        // Remove button
        holder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onQueueItemClick(item, ACTION_REMOVE);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return queue.size();
    }

    public void setQueue(List<QueueItem> queue) {
        this.queue = queue;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView positionTextView;
        TextView titleTextView;
        TextView artistTextView;
        Button moveToTopButton;
        Button removeButton;

        public ViewHolder(View itemView) {
            super(itemView);
            positionTextView = itemView.findViewById(R.id.queuePositionTextView);
            titleTextView = itemView.findViewById(R.id.songTitleTextView);
            artistTextView = itemView.findViewById(R.id.songArtistTextView);
            moveToTopButton = itemView.findViewById(R.id.moveToTopButton);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
    }
}
