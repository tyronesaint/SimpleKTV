package com.ktv.simple;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ktv.simple.cache.CacheManager;

import java.io.File;

/**
 * Cache management activity
 * Allows users to view and manage cache settings
 */
public class CacheManagementActivity extends AppCompatActivity {

    private TextView cacheSizeTextView;
    private TextView audioCacheSizeTextView;
    private TextView imageCacheSizeTextView;
    private Spinner maxCacheSizeSpinner;
    private Spinner autoCleanupSpinner;
    private Button clearAudioCacheButton;
    private Button clearImageCacheButton;
    private Button clearAllCacheButton;

    private CacheManager cacheManager;

    private static final long[] CACHE_SIZE_OPTIONS = {
            50 * 1024 * 1024,   // 50MB
            100 * 1024 * 1024,  // 100MB
            200 * 1024 * 1024,  // 200MB
            500 * 1024 * 1024,  // 500MB
            1024 * 1024 * 1024  // 1GB
    };

    private static final long[] CACHE_AGE_OPTIONS = {
            7 * 24 * 60 * 60 * 1000L,   // 7 days
            14 * 24 * 60 * 60 * 1000L,  // 14 days
            30 * 24 * 60 * 60 * 1000L,  // 30 days
            60 * 24 * 60 * 60 * 1000L,  // 60 days
            -1                           // Never
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_management);

        cacheManager = CacheManager.getInstance(this);

        initViews();
        setupListeners();
        updateCacheInfo();
        loadSettings();
    }

    private void initViews() {
        cacheSizeTextView = findViewById(R.id.cacheSizeTextView);
        audioCacheSizeTextView = findViewById(R.id.audioCacheSizeTextView);
        imageCacheSizeTextView = findViewById(R.id.imageCacheSizeTextView);
        maxCacheSizeSpinner = findViewById(R.id.maxCacheSizeSpinner);
        autoCleanupSpinner = findViewById(R.id.autoCleanupSpinner);
        clearAudioCacheButton = findViewById(R.id.clearAudioCacheButton);
        clearImageCacheButton = findViewById(R.id.clearImageCacheButton);
        clearAllCacheButton = findViewById(R.id.clearAllCacheButton);

        // Setup max cache size spinner
        String[] cacheSizeLabels = {
                "50 MB",
                "100 MB",
                "200 MB",
                "500 MB",
                "1 GB"
        };
        ArrayAdapter<String> cacheSizeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cacheSizeLabels
        );
        cacheSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxCacheSizeSpinner.setAdapter(cacheSizeAdapter);

        // Setup auto cleanup spinner
        String[] cleanupLabels = {
                "7 天",
                "14 天",
                "30 天",
                "60 天",
                "从不"
        };
        ArrayAdapter<String> cleanupAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cleanupLabels
        );
        cleanupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        autoCleanupSpinner.setAdapter(cleanupAdapter);
    }

    private void setupListeners() {
        clearAudioCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(CacheManagementActivity.this)
                        .setTitle(R.string.confirm_clear_audio_cache)
                        .setMessage(R.string.clear_audio_cache_warning)
                        .setPositiveButton(android.R.string.yes, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                cacheManager.clearAudioCache();
                                updateCacheInfo();
                                Toast.makeText(CacheManagementActivity.this,
                                        R.string.audio_cache_cleared,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        });

        clearImageCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(CacheManagementActivity.this)
                        .setTitle(R.string.confirm_clear_image_cache)
                        .setMessage(R.string.clear_image_cache_warning)
                        .setPositiveButton(android.R.string.yes, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                cacheManager.clearImageCache();
                                updateCacheInfo();
                                Toast.makeText(CacheManagementActivity.this,
                                        R.string.image_cache_cleared,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        });

        clearAllCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(CacheManagementActivity.this)
                        .setTitle(R.string.confirm_clear_all_cache)
                        .setMessage(R.string.clear_all_cache_warning)
                        .setPositiveButton(android.R.string.yes, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                cacheManager.clearCache();
                                updateCacheInfo();
                                Toast.makeText(CacheManagementActivity.this,
                                        R.string.all_cache_cleared,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        });

        maxCacheSizeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveSettings();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        autoCleanupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveSettings();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void updateCacheInfo() {
        long totalSize = cacheManager.getCurrentCacheSize();
        cacheSizeTextView.setText(formatSize(totalSize));

        // Calculate audio cache size
        File audioCacheDir = new File(getCacheDir(), "audio_cache");
        long audioSize = getDirectorySize(audioCacheDir);
        audioCacheSizeTextView.setText(formatSize(audioSize));

        // Calculate image cache size
        File imageCacheDir = new File(getExternalCacheDir(), "image_cache");
        long imageSize = getDirectorySize(imageCacheDir);
        imageCacheSizeTextView.setText(formatSize(imageSize));
    }

    private void loadSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("cache_settings", MODE_PRIVATE);

        long maxSize = prefs.getLong("max_cache_size", 100 * 1024 * 1024);
        int maxSizeIndex = 0;
        for (int i = 0; i < CACHE_SIZE_OPTIONS.length; i++) {
            if (CACHE_SIZE_OPTIONS[i] == maxSize) {
                maxSizeIndex = i;
                break;
            }
        }
        maxCacheSizeSpinner.setSelection(maxSizeIndex);

        long maxAge = prefs.getLong("max_cache_age", 30 * 24 * 60 * 60 * 1000L);
        int maxAgeIndex = 2; // Default 30 days
        if (maxAge == -1) {
            maxAgeIndex = 4;
        } else {
            for (int i = 0; i < CACHE_AGE_OPTIONS.length - 1; i++) {
                if (CACHE_AGE_OPTIONS[i] == maxAge) {
                    maxAgeIndex = i;
                    break;
                }
            }
        }
        autoCleanupSpinner.setSelection(maxAgeIndex);
    }

    private void saveSettings() {
        int maxSizeIndex = maxCacheSizeSpinner.getSelectedItemPosition();
        int maxAgeIndex = autoCleanupSpinner.getSelectedItemPosition();

        android.content.SharedPreferences prefs = getSharedPreferences("cache_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("max_cache_size", CACHE_SIZE_OPTIONS[maxSizeIndex]);
        editor.putLong("max_cache_age", CACHE_AGE_OPTIONS[maxAgeIndex]);

        editor.apply();

        // Apply settings
        cacheManager.setMaxCacheSize(CACHE_SIZE_OPTIONS[maxSizeIndex]);
        cacheManager.setMaxAge(CACHE_AGE_OPTIONS[maxAgeIndex]);
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    private long getDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }

        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }
        return size;
    }
}
