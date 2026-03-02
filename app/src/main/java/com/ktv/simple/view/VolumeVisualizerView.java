package com.ktv.simple.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Simple volume-based visualizer view
 * Uses audio session level to create bar visualization
 * No permissions required, compatible with Android 4.4
 */
public class VolumeVisualizerView extends View {
    private Paint barPaint;
    private float[] volumeLevels; // Volume levels for each bar
    private int barCount = 8;
    private float barWidth;
    private float barGap;
    private float animationSpeed = 0.3f; // Smoothing factor

    public VolumeVisualizerView(Context context) {
        super(context);
        init();
    }

    public VolumeVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VolumeVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint = new Paint();
        barPaint.setColor(Color.parseColor("#2196F3"));
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);

        volumeLevels = new float[barCount];
    }

    /**
     * Update volume level (0.0 to 1.0)
     */
    public void updateVolume(float volume) {
        // Smooth transitions
        for (int i = 0; i < barCount; i++) {
            float targetLevel = volume * (1.0f - Math.abs(i - barCount / 2.0f) / (barCount / 2.0f));
            volumeLevels[i] += (targetLevel - volumeLevels[i]) * animationSpeed;
        }

        postInvalidate();
    }

    /**
     * Reset volume levels
     */
    public void reset() {
        for (int i = 0; i < barCount; i++) {
            volumeLevels[i] = 0;
        }
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDimensions(w, h);
    }

    private void calculateDimensions(int width, int height) {
        barGap = width * 0.02f; // 2% of width
        float totalGap = (barCount - 1) * barGap;
        barWidth = (width - totalGap) / barCount;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int height = getHeight();

        // Recalculate dimensions if needed
        if (barWidth <= 0) {
            calculateDimensions(getWidth(), height);
        }

        // Draw bars
        for (int i = 0; i < barCount; i++) {
            float level = Math.max(0, Math.min(1, volumeLevels[i]));
            float barHeight = height * level * 0.9f;
            float x = i * (barWidth + barGap);
            float y = (height - barHeight) / 2;

            // Draw bar with gradient-like color (simpler approach: change opacity)
            barPaint.setAlpha((int) (100 + 155 * level));
            canvas.drawRect(x, y, x + barWidth, y + barHeight, barPaint);
        }
    }

    /**
     * Simulated audio data for demo purposes
     * In real implementation, this would receive actual audio data
     */
    public void simulateAudioData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(50);
                        float simulatedVolume = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
                        updateVolume(simulatedVolume);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }).start();
    }
}
