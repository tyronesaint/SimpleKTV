package com.ktv.simple.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.ktv.simple.model.Lyrics;

/**
 * Lyrics display view with scrolling and highlighting
 * Compatible with Android 4.4 (API 19)
 */
public class LyricsView extends View {
    private static final String TAG = "LyricsView";

    private Paint normalTextPaint;
    private Paint highlightTextPaint;
    private Lyrics lyrics;
    private int currentLineIndex = -1;
    private float lineSpacing = 30; // Space between lines
    private float textSize = 16; // Text size in sp

    public LyricsView(Context context) {
        super(context);
        init();
    }

    public LyricsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LyricsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Normal text paint
        normalTextPaint = new Paint();
        normalTextPaint.setColor(Color.parseColor("#B3B3B3")); // Light gray
        normalTextPaint.setTextSize(spToPx(textSize));
        normalTextPaint.setAntiAlias(true);
        normalTextPaint.setTextAlign(Paint.Align.CENTER);

        // Highlight text paint
        highlightTextPaint = new Paint();
        highlightTextPaint.setColor(Color.parseColor("#FFFFFF")); // White
        highlightTextPaint.setTextSize(spToPx(textSize * 1.2f)); // 20% larger
        highlightTextPaint.setAntiAlias(true);
        highlightTextPaint.setTextAlign(Paint.Align.CENTER);
        highlightTextPaint.setShadowLayer(5, 0, 0, Color.parseColor("#2196F3")); // Blue shadow
    }

    /**
     * Set lyrics
     */
    public void setLyrics(Lyrics lyrics) {
        this.lyrics = lyrics;
        this.currentLineIndex = -1;
        postInvalidate();
    }

    /**
     * Update current line index
     */
    public void setCurrentLine(int index) {
        if (this.currentLineIndex != index) {
            this.currentLineIndex = index;
            postInvalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (lyrics == null || !lyrics.hasLyrics()) {
            drawNoLyrics(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2.0f;

        // Draw current line in center
        int linesToDraw = Math.min(5, lyrics.getLineCount()); // Show up to 5 lines

        // Calculate start index to center current line
        int startIndex = Math.max(0, currentLineIndex - 2);
        int endIndex = Math.min(lyrics.getLineCount() - 1, startIndex + 4);

        // Adjust if we can't center properly
        if (currentLineIndex >= 0) {
            startIndex = Math.max(0, currentLineIndex - 2);
            endIndex = Math.min(lyrics.getLineCount() - 1, currentLineIndex + 2);
        }

        // Draw lines
        float currentY = centerY;
        if (currentLineIndex >= 0 && startIndex <= currentLineIndex && currentLineIndex <= endIndex) {
            currentY = centerY - (currentLineIndex - startIndex) * lineSpacing;
        }

        for (int i = startIndex; i <= endIndex; i++) {
            Lyrics.LyricLine line = lyrics.getLine(i);
            if (line == null) continue;

            Paint paint = (i == currentLineIndex) ? highlightTextPaint : normalTextPaint;

            // Draw text
            canvas.drawText(line.text, width / 2.0f, currentY, paint);
            currentY += lineSpacing;
        }
    }

    /**
     * Draw no lyrics message
     */
    private void drawNoLyrics(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2.0f;

        canvas.drawText("No lyrics", width / 2.0f, centerY, normalTextPaint);
    }

    /**
     * Convert sp to px
     */
    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * Set text size
     */
    public void setTextSize(float sp) {
        this.textSize = sp;
        normalTextPaint.setTextSize(spToPx(sp));
        highlightTextPaint.setTextSize(spToPx(sp * 1.2f));
        postInvalidate();
    }

    /**
     * Set line spacing
     */
    public void setLineSpacing(float spacing) {
        this.lineSpacing = spacing;
        postInvalidate();
    }

    /**
     * Get current line count
     */
    public int getLineCount() {
        return lyrics != null ? lyrics.getLineCount() : 0;
    }
}
