package com.ktv.simple.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Audio visualizer view displaying bar waveform
 * Compatible with Android 4.4 (API 19)
 */
public class VisualizerView extends View {
    private Visualizer visualizer;
    private Paint barPaint;
    private byte[] waveform;
    private int barCount = 16;
    private float barWidth;
    private float barGap;

    public VisualizerView(Context context) {
        super(context);
        init();
    }

    public VisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Setup paint for bars
        barPaint = new Paint();
        barPaint.setColor(Color.parseColor("#2196F3"));
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);

        waveform = new byte[256];
    }

    /**
     * Set visualizer to display audio data
     */
    public void setVisualizer(Visualizer visualizer) {
        if (this.visualizer != null) {
            this.visualizer.setEnabled(false);
            this.visualizer.release();
        }

        this.visualizer = visualizer;

        if (this.visualizer != null) {
            this.visualizer.setEnabled(false);
            this.visualizer.setCaptureSize(256);

            // Set waveform capture listener
            this.visualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                            updateWaveform(waveform);
                        }

                        @Override
                        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                            // Not using FFT for simple bar visualization
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    true,
                    false
            );

            this.visualizer.setEnabled(true);
        }
    }

    /**
     * Update waveform data
     */
    private void updateWaveform(byte[] newWaveform) {
        if (newWaveform != null && newWaveform.length > 0) {
            waveform = newWaveform;
            postInvalidate(); // Trigger redraw
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Calculate bar dimensions
        float totalGap = (barCount - 1) * barGap;
        float availableWidth = w - totalGap;
        barWidth = availableWidth / barCount;
        barGap = barWidth * 0.2f; // 20% of bar width
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (waveform == null || waveform.length == 0) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // Calculate bar dimensions
        barWidth = (width - (barCount - 1) * barGap) / barCount;

        // Draw bars
        for (int i = 0; i < barCount; i++) {
            // Get average amplitude for this bar
            int startIndex = (i * waveform.length) / barCount;
            int endIndex = ((i + 1) * waveform.length) / barCount;
            float amplitude = 0;

            for (int j = startIndex; j < endIndex; j++) {
                amplitude += Math.abs(waveform[j] - 128);
            }

            amplitude = amplitude / (endIndex - startIndex);
            amplitude = amplitude / 128.0f; // Normalize to 0-1

            // Calculate bar height
            float barHeight = height * amplitude * 0.8f; // Use 80% of max height
            float x = i * (barWidth + barGap);
            float y = (height - barHeight) / 2; // Center vertically

            // Draw bar
            canvas.drawRect(x, y, x + barWidth, y + barHeight, barPaint);
        }
    }

    /**
     * Release visualizer resources
     */
    public void release() {
        if (visualizer != null) {
            visualizer.setEnabled(false);
            visualizer.release();
            visualizer = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
