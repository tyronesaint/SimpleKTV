package com.ktv.simple;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ktv.simple.api.OnlineMusicService;
import com.ktv.simple.model.MusicSource;

import java.util.List;

/**
 * Settings activity for configuring online music API
 * 在线音乐服务设置页面
 * 
 * 用户只需输入管理地址，系统自动解析 baseUrl 和 apiKey
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText adminUrlEditText;
    private TextView statusTextView;
    private TextView sourcesTextView;
    private LinearLayout sourcesLayout;
    private Button saveButton;
    private Button testButton;
    private Button cancelButton;
    private Button cacheManagementButton;

    private OnlineMusicService musicService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        adminUrlEditText = (EditText) findViewById(R.id.adminUrlEditText);
        statusTextView = (TextView) findViewById(R.id.statusTextView);
        sourcesTextView = (TextView) findViewById(R.id.sourcesTextView);
        sourcesLayout = (LinearLayout) findViewById(R.id.sourcesLayout);
        saveButton = (Button) findViewById(R.id.saveButton);
        testButton = (Button) findViewById(R.id.testButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        cacheManagementButton = (Button) findViewById(R.id.cacheManagementButton);
    }

    private void initData() {
        musicService = OnlineMusicService.getInstance(this);
        loadSavedSettings();
    }

    private void loadSavedSettings() {
        // 显示已保存的配置
        String baseUrl = musicService.getBaseUrl();
        String apiKey = musicService.getApiKey();

        if (!baseUrl.isEmpty() && !apiKey.isEmpty()) {
            // 显示已配置的管理地址（格式：baseUrl/apiKey）
            adminUrlEditText.setText(baseUrl + "/" + apiKey);
        }

        updateStatus();
        displayCachedSources();
    }

    private void setupListeners() {
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        cacheManagementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCache();
            }
        });
    }

    /**
     * 保存设置
     * 自动解析管理地址并获取音源列表
     */
    private void saveSettings() {
        String adminUrl = adminUrlEditText.getText().toString().trim();

        if (adminUrl.isEmpty()) {
            Toast.makeText(this, "请输入管理地址", Toast.LENGTH_SHORT).show();
            return;
        }

        statusTextView.setText("正在解析管理地址...");

        // 解析管理地址
        if (!musicService.updateConfigFromAdminUrl(adminUrl)) {
            statusTextView.setText("管理地址格式错误");
            Toast.makeText(this, "管理地址格式错误\n正确格式: https://example.com/your-key", 
                Toast.LENGTH_LONG).show();
            return;
        }

        statusTextView.setText("正在获取音源列表...");

        // 获取音源列表
        musicService.getMusicSources(new OnlineMusicService.SourcesCallback() {
            @Override
            public void onSuccess(final List<MusicSource> sources) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("配置成功!\n\n");
                        sb.append("服务器: ").append(musicService.getBaseUrl()).append("\n");
                        sb.append("API Key: ").append(musicService.getApiKey()).append("\n\n");
                        sb.append("可用音源:\n");
                        
                        for (MusicSource source : sources) {
                            sb.append("• ").append(source.getName());
                            if (source.isDefault()) {
                                sb.append(" (默认)");
                            }
                            sb.append("\n");
                            sb.append("  支持平台: ");
                            if (source.getSupportedSources() != null) {
                                sb.append(android.text.TextUtils.join(", ", source.getSupportedSources()));
                            }
                            sb.append("\n");
                        }

                        statusTextView.setText(sb.toString());
                        displayCachedSources();

                        Toast.makeText(SettingsActivity.this, 
                            "配置成功，已获取 " + sources.size() + " 个音源", 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTextView.setText("配置失败: " + message);
                        Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 测试连接并获取音源列表
     * 测试成功后自动保存配置
     */
    private void testConnection() {
        String adminUrl = adminUrlEditText.getText().toString().trim();

        if (adminUrl.isEmpty()) {
            Toast.makeText(this, "请输入管理地址", Toast.LENGTH_SHORT).show();
            return;
        }

        statusTextView.setText("正在测试连接...");

        // 临时解析（不保存）
        final String originalBaseUrl = musicService.getBaseUrl();
        final String originalApiKey = musicService.getApiKey();

        if (!musicService.updateConfigFromAdminUrl(adminUrl)) {
            statusTextView.setText("管理地址格式错误");
            Toast.makeText(this, "管理地址格式错误", Toast.LENGTH_SHORT).show();
            musicService.updateConfig(originalBaseUrl, originalApiKey);
            return;
        }

        // 获取音源列表测试
        musicService.getMusicSources(new OnlineMusicService.SourcesCallback() {
            @Override
            public void onSuccess(final List<MusicSource> sources) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("连接成功!\n\n");
                        sb.append("服务器: ").append(musicService.getBaseUrl()).append("\n");
                        sb.append("API Key: ").append(musicService.getApiKey()).append("\n\n");
                        sb.append("找到 ").append(sources.size()).append(" 个音源:\n");
                        
                        for (MusicSource source : sources) {
                            sb.append("• ").append(source.getName());
                            if (source.isDefault()) {
                                sb.append(" (默认)");
                            }
                            sb.append("\n");
                        }

                        statusTextView.setText(sb.toString());
                        displayCachedSources();

                        // 测试成功后自动保存配置
                        musicService.saveConfig();
                        
                        Toast.makeText(SettingsActivity.this, 
                            "配置成功，已保存 " + sources.size() + " 个音源", 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 恢复原始配置
                        musicService.updateConfig(originalBaseUrl, originalApiKey);
                        
                        statusTextView.setText("连接失败: " + message);
                        Toast.makeText(SettingsActivity.this, "连接失败: " + message, 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 显示缓存的音源列表
     */
    private void displayCachedSources() {
        List<MusicSource> sources = musicService.getCachedMusicSources();
        
        if (sources.isEmpty()) {
            sourcesTextView.setText("暂无音源信息，请先配置并连接服务器");
            sourcesLayout.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("已加载音源:\n");
        
        for (MusicSource source : sources) {
            sb.append("\n【").append(source.getName()).append("】");
            if (source.isDefault()) {
                sb.append(" - 默认");
            }
            sb.append("\n");
            sb.append("描述: ").append(source.getDescription()).append("\n");
            if (source.getSupportedSources() != null && !source.getSupportedSources().isEmpty()) {
                sb.append("平台: ").append(android.text.TextUtils.join(", ", 
                    source.getSupportedSources())).append("\n");
            }
        }

        sourcesTextView.setText(sb.toString());
        sourcesLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 清除缓存
     */
    private void clearCache() {
        try {
            // 清除歌词缓存
            java.io.File cacheDir = getCacheDir();
            java.io.File[] files = cacheDir.listFiles();
            int count = 0;
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.getName().startsWith("lyric_")) {
                        file.delete();
                        count++;
                    }
                }
            }

            // 清除 Glide 图片缓存
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        com.bumptech.glide.Glide.get(SettingsActivity.this).clearDiskCache();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }).start();

            Toast.makeText(this, "已清除 " + count + " 个缓存文件", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "清除缓存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 更新状态显示
     */
    private void updateStatus() {
        String baseUrl = musicService.getBaseUrl();
        String apiKey = musicService.getApiKey();

        StringBuilder sb = new StringBuilder();
        sb.append("当前配置:\n");
        sb.append("服务器: ").append(baseUrl.isEmpty() ? "未设置" : baseUrl).append("\n");
        sb.append("API Key: ").append(apiKey.isEmpty() ? "未设置" : apiKey).append("\n");

        List<MusicSource> sources = musicService.getCachedMusicSources();
        sb.append("音源数量: ").append(sources.size());

        statusTextView.setText(sb.toString());
    }
}
