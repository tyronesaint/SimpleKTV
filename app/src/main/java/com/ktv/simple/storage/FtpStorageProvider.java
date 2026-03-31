package com.ktv.simple.storage;

import android.util.Log;

import com.ktv.simple.model.Song;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple FTP storage provider (basic implementation for Android 4.4)
 */
public class FtpStorageProvider implements StorageProvider {
    private static final String TAG = "FtpStorageProvider";

    private String server;
    private int port;
    private String username;
    private String password;
    private Socket controlSocket;
    private BufferedReader controlReader;
    private OutputStream controlWriter;
    private boolean connected;
    private String currentDirectory;

    public FtpStorageProvider(String server, int port, String username, String password) {
        this.server = server;
        this.port = port > 0 ? port : 21;
        this.username = username;
        this.password = password;
        this.connected = false;
        this.currentDirectory = "/";
    }

    @Override
    public boolean connect() throws Exception {
        try {
            // Connect to FTP server
            controlSocket = new Socket();
            controlSocket.connect(new InetSocketAddress(server, port), 10000);

            controlReader = new BufferedReader(
                    new InputStreamReader(controlSocket.getInputStream())
            );
            controlWriter = controlSocket.getOutputStream();

            // Read welcome message
            String response = readLine();
            if (!response.startsWith("220")) {
                throw new Exception("FTP welcome error: " + response);
            }

            // Login
            sendCommand("USER " + username);
            response = readLine();
            if (response.startsWith("331")) {
                sendCommand("PASS " + password);
                response = readLine();
            }

            if (!response.startsWith("230")) {
                throw new Exception("FTP login failed: " + response);
            }

            connected = true;
            return connected;

        } catch (Exception e) {
            Log.e(TAG, "FTP connect error", e);
            disconnect();
            throw new Exception("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        try {
            if (controlSocket != null) {
                sendCommand("QUIT");
                controlSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error", e);
        }
        controlSocket = null;
        controlReader = null;
        controlWriter = null;
    }

    @Override
    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }

    @Override
    public List<Song> loadSongs() throws Exception {
        List<Song> songs = new ArrayList<>();

        try {
            // List files in current directory
            List<FtpFile> files = listFiles(currentDirectory);

            for (FtpFile file : files) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    // Recursively scan subdirectory
                    scanDirectory(file.getPath(), songs);
                } else if (file.isFile() && isMp3File(file)) {
                    String title = file.getName().replace(".mp3", "").replace(".MP3", "");
                    Song song = new Song(
                            System.currentTimeMillis() + file.getName().hashCode(),
                            title,
                            "Unknown Artist",
                            file.getPath(),
                            Song.StorageType.FTP
                    );
                    songs.add(song);
                }
            }

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
        return Song.StorageType.FTP;
    }

    @Override
    public String getDescription() {
        return "FTP: " + server + ":" + port;
    }

    /**
     * Recursively scan directory for MP3 files
     */
    private void scanDirectory(String directoryPath, List<Song> songs) throws Exception {
        List<FtpFile> files = listFiles(directoryPath);

        for (FtpFile file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                scanDirectory(file.getPath(), songs);
            } else if (file.isFile() && isMp3File(file)) {
                String title = file.getName().replace(".mp3", "").replace(".MP3", "");
                Song song = new Song(
                        System.currentTimeMillis() + file.getName().hashCode(),
                        title,
                        "Unknown Artist",
                        file.getPath(),
                        Song.StorageType.FTP
                );
                songs.add(song);
            }
        }
    }

    /**
     * List files in directory using PASV mode
     */
    private List<FtpFile> listFiles(String path) throws Exception {
        List<FtpFile> files = new ArrayList<>();

        // Enter passive mode
        sendCommand("PASV");
        String pasvResponse = readLine();
        if (!pasvResponse.startsWith("227")) {
            throw new Exception("PASV failed: " + pasvResponse);
        }

        // Parse PASV response to get data port
        String[] parts = pasvResponse.split("\\(|\\)|,");
        int dataPort = Integer.parseInt(parts[parts.length - 2]) * 256 + Integer.parseInt(parts[parts.length - 1]);

        // Open data connection
        Socket dataSocket = new Socket(server, dataPort);

        // Send LIST command
        sendCommand("LIST " + path);
        readLine(); // Read 150 response

        // Read directory listing
        InputStream dataStream = dataSocket.getInputStream();
        BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataStream));

        String line;
        while ((line = dataReader.readLine()) != null) {
            FtpFile file = parseFtpFile(line, path);
            if (file != null) {
                files.add(file);
            }
        }

        dataSocket.close();
        readLine(); // Read 226 response

        return files;
    }

    /**
     * Parse FTP LIST response line
     */
    private FtpFile parseFtpFile(String line, String parentPath) {
        try {
            // Parse Unix-style listing
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 9) {
                return null;
            }

            String type = parts[0];
            String name = parts[8];

            // Skip . and ..
            if (name.equals(".") || name.equals("..")) {
                return null;
            }

            boolean isDirectory = type.startsWith("d");
            String fullPath = parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;

            return new FtpFile(name, fullPath, isDirectory);

        } catch (Exception e) {
            Log.e(TAG, "Parse FTP file error", e);
            return null;
        }
    }

    /**
     * Check if file is MP3
     */
    private boolean isMp3File(FtpFile file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3");
    }

    /**
     * Send FTP command
     */
    private void sendCommand(String command) throws IOException {
        controlWriter.write((command + "\r\n").getBytes());
        controlWriter.flush();
    }

    /**
     * Read FTP response line
     */
    private String readLine() throws IOException {
        String line = controlReader.readLine();
        return line != null ? line : "";
    }

    /**
     * Inner class for FTP file representation
     */
    private static class FtpFile {
        private String name;
        private String path;
        private boolean directory;

        public FtpFile(String name, String path, boolean directory) {
            this.name = name;
            this.path = path;
            this.directory = directory;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public boolean isDirectory() {
            return directory;
        }

        public boolean isFile() {
            return !directory;
        }
    }
}
