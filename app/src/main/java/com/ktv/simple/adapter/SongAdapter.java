package com.ktv.simple.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.ktv.simple.R;
import com.ktv.simple.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for song list
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> songs;
    private List<Song> allSongs;
    private OnItemClickListener listener;
    private boolean isSearchResult;

    public interface OnItemClickListener {
        void onSongClick(Song song);
        void onAddToQueueClick(Song song);
    }

    public SongAdapter(com.ktv.simple.MainActivity context, List<Song> songs, OnItemClickListener listener) {
        this.allSongs = new ArrayList<>(songs);
        this.songs = songs;
        this.listener = listener;
        this.isSearchResult = false;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Song song = songs.get(position);

        holder.titleTextView.setText(song.getTitle());
        holder.artistTextView.setText(song.getArtist());
        holder.albumTextView.setText(song.getAlbum() != null ? song.getAlbum() : "Unknown Album");

        // Load album cover
        if (song.getCoverPath() != null) {
            Glide.with(holder.itemView.getContext())
                    .load(new File(song.getCoverPath()))
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.coverImageView);
        } else {
            holder.coverImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Click on item to play directly
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onSongClick(song);
                }
            }
        });

        // Add to queue button
        holder.addToQueueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onAddToQueueClick(song);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    public void updateAllSongs(List<Song> songs) {
        this.allSongs = new ArrayList<>(songs);
        this.isSearchResult = false;
        this.songs = this.allSongs;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImageView;
        TextView titleTextView;
        TextView artistTextView;
        TextView albumTextView;
        Button addToQueueButton;

        public ViewHolder(View itemView) {
            super(itemView);
            coverImageView = itemView.findViewById(R.id.albumCoverImageView);
            titleTextView = itemView.findViewById(R.id.songTitleTextView);
            artistTextView = itemView.findViewById(R.id.songArtistTextView);
            albumTextView = itemView.findViewById(R.id.songAlbumTextView);
            addToQueueButton = itemView.findViewById(R.id.addToQueueButton);
        }
    }
}
