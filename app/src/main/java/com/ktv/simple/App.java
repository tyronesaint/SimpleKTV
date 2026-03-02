package com.ktv.simple;

import android.app.Application;
import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

/**
 * Application class for SimpleKTV
 * Provides global context and initialization
 */
public class App extends Application {

    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        initializeGlide();
    }

    private void initializeGlide() {
        // Glide automatically handles memory management
        // No manual configuration needed for Glide 4.x
    }

    public static Context getContext() {
        return context;
    }
}
