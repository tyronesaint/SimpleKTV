package com.ktv.simple.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics model for LRC file parsing and display
 */
public class Lyrics {
    private List<LyricLine> lyricLines;
    private boolean hasLyrics;

    public Lyrics() {
        lyricLines = new ArrayList<>();
        hasLyrics = false;
    }

    /**
     * Parse LRC format lyrics
     * Format: [mm:ss.xx]Lyric text
     */
    public static Lyrics parse(InputStream inputStream) throws IOException {
        Lyrics lyrics = new Lyrics();
        if (inputStream == null) {
            return lyrics;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");

        while ((line = reader.readLine()) != null) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.find()) {
                try {
                    int minutes = Integer.parseInt(matcher.group(1));
                    int seconds = Integer.parseInt(matcher.group(2));
                    int milliseconds = Integer.parseInt(matcher.group(3));

                    // Convert to milliseconds
                    long time = minutes * 60000 + seconds * 1000 + milliseconds;

                    // Extract lyric text
                    int textStart = matcher.end();
                    String text = line.substring(textStart).trim();

                    if (!text.isEmpty()) {
                        lyrics.lyricLines.add(new LyricLine(time, text));
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid time format
                }
            }
        }

        // Sort by time
        Collections.sort(lyrics.lyricLines, new Comparator<LyricLine>() {
            @Override
            public int compare(LyricLine l1, LyricLine l2) {
                return (int) (l1.time - l2.time);
            }
        });

        lyrics.hasLyrics = !lyrics.lyricLines.isEmpty();
        return lyrics;
    }

    /**
     * Find current lyric line based on playback time
     */
    public int findCurrentLine(long currentTimeMs) {
        if (!hasLyrics) {
            return -1;
        }

        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentTimeMs < lyricLines.get(i).time) {
                return i > 0 ? i - 1 : -1;
            }
        }

        // If current time is greater than all lines, return the last line
        return lyricLines.size() - 1;
    }

    public List<LyricLine> getLyricLines() {
        return lyricLines;
    }

    public boolean hasLyrics() {
        return hasLyrics;
    }

    public int getLineCount() {
        return lyricLines.size();
    }

    public LyricLine getLine(int index) {
        if (index >= 0 && index < lyricLines.size()) {
            return lyricLines.get(index);
        }
        return null;
    }

    /**
     * Inner class for a single lyric line
     */
    public static class LyricLine {
        public long time; // Time in milliseconds
        public String text;

        public LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format("[%d]%s", time, text);
        }
    }
}
