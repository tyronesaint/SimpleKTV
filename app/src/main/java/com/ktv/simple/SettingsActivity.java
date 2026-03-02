package com.ktv.simple;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.ktv.simple.storage.FtpStorageProvider;
import com.ktv.simple.storage.LocalStorageProvider;
import com.ktv.simple.storage.SmbStorageProvider;
import com.ktv.simple.storage.StorageProvider;
import com.ktv.simple.storage.WebDavStorageProvider;

import java.io.File;

/**
 * Settings activity for configuring storage providers
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText localPathEditText;
    private LinearLayout webDavLayout;
    private EditText webDavUrlEditText;
    private EditText webDavUsernameEditText;
    private EditText webDavPasswordEditText;

    private LinearLayout ftpLayout;
    private EditText ftpServerEditText;
    private EditText ftpPortEditText;
    private EditText ftpUsernameEditText;
    private EditText ftpPasswordEditText;

    private LinearLayout smbLayout;
    private EditText smbServerEditText;
    private EditText smbShareEditText;
    private EditText smbUsernameEditText;
    private EditText smbPasswordEditText;

    private RadioGroup storageTypeGroup;
    private Button saveButton;
    private Button cancelButton;
    private Button cacheManagementButton;

    private String currentStorageType = "local";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadSavedSettings();
        setupListeners();
    }

    private void initViews() {
        localPathEditText = (EditText) findViewById(R.id.localPathEditText);
        webDavLayout = (LinearLayout) findViewById(R.id.webDavLayout);
        webDavUrlEditText = (EditText) findViewById(R.id.webDavUrlEditText);
        webDavUsernameEditText = (EditText) findViewById(R.id.webDavUsernameEditText);
        webDavPasswordEditText = (EditText) findViewById(R.id.webDavPasswordEditText);

        ftpLayout = (LinearLayout) findViewById(R.id.ftpLayout);
        ftpServerEditText = (EditText) findViewById(R.id.ftpServerEditText);
        ftpPortEditText = (EditText) findViewById(R.id.ftpPortEditText);
        ftpUsernameEditText = (EditText) findViewById(R.id.ftpUsernameEditText);
        ftpPasswordEditText = (EditText) findViewById(R.id.ftpPasswordEditText);

        smbLayout = (LinearLayout) findViewById(R.id.smbLayout);
        smbServerEditText = (EditText) findViewById(R.id.smbServerEditText);
        smbShareEditText = (EditText) findViewById(R.id.smbShareEditText);
        smbUsernameEditText = (EditText) findViewById(R.id.smbUsernameEditText);
        smbPasswordEditText = (EditText) findViewById(R.id.smbPasswordEditText);

        storageTypeGroup = (RadioGroup) findViewById(R.id.storageTypeGroup);
        saveButton = (Button) findViewById(R.id.saveButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        cacheManagementButton = (Button) findViewById(R.id.cacheManagementButton);

        // Set default local path
        String defaultMusicPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
        ).getAbsolutePath();
        localPathEditText.setText(defaultMusicPath);
    }

    private void loadSavedSettings() {
        // Load saved storage type
        currentStorageType = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("storage_type", "local");

        // Set radio button
        if ("local".equals(currentStorageType)) {
            storageTypeGroup.check(R.id.localRadio);
            webDavLayout.setVisibility(View.GONE);
            ftpLayout.setVisibility(View.GONE);
            smbLayout.setVisibility(View.GONE);
        } else if ("webdav".equals(currentStorageType)) {
            storageTypeGroup.check(R.id.webDavRadio);
            webDavLayout.setVisibility(View.VISIBLE);
            ftpLayout.setVisibility(View.GONE);
            smbLayout.setVisibility(View.GONE);
        } else if ("ftp".equals(currentStorageType)) {
            storageTypeGroup.check(R.id.ftpRadio);
            webDavLayout.setVisibility(View.GONE);
            ftpLayout.setVisibility(View.VISIBLE);
            smbLayout.setVisibility(View.GONE);
        } else if ("smb".equals(currentStorageType)) {
            storageTypeGroup.check(R.id.smbRadio);
            webDavLayout.setVisibility(View.GONE);
            ftpLayout.setVisibility(View.GONE);
            smbLayout.setVisibility(View.VISIBLE);
        }

        // Load saved settings for each type
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        localPathEditText.setText(prefs.getString("local_path", ""));
        webDavUrlEditText.setText(prefs.getString("webdav_url", ""));
        webDavUsernameEditText.setText(prefs.getString("webdav_username", ""));
        webDavPasswordEditText.setText(prefs.getString("webdav_password", ""));
        ftpServerEditText.setText(prefs.getString("ftp_server", ""));
        ftpPortEditText.setText(prefs.getString("ftp_port", "21"));
        ftpUsernameEditText.setText(prefs.getString("ftp_username", ""));
        ftpPasswordEditText.setText(prefs.getString("ftp_password", ""));
        smbServerEditText.setText(prefs.getString("smb_server", ""));
        smbShareEditText.setText(prefs.getString("smb_share", ""));
        smbUsernameEditText.setText(prefs.getString("smb_username", ""));
        smbPasswordEditText.setText(prefs.getString("smb_password", ""));
    }

    private void setupListeners() {
        storageTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                webDavLayout.setVisibility(View.GONE);
                ftpLayout.setVisibility(View.GONE);
                smbLayout.setVisibility(View.GONE);

                if (checkedId == R.id.webDavRadio) {
                    webDavLayout.setVisibility(View.VISIBLE);
                    currentStorageType = "webdav";
                } else if (checkedId == R.id.ftpRadio) {
                    ftpLayout.setVisibility(View.VISIBLE);
                    currentStorageType = "ftp";
                } else if (checkedId == R.id.smbRadio) {
                    smbLayout.setVisibility(View.VISIBLE);
                    currentStorageType = "smb";
                } else {
                    currentStorageType = "local";
                }
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
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
                Intent intent = new Intent(SettingsActivity.this, CacheManagementActivity.class);
                startActivity(intent);
            }
        });
    }

    private void saveSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        // Save storage type
        editor.putString("storage_type", currentStorageType);

        // Save local path
        String localPath = localPathEditText.getText().toString().trim();
        if (!localPath.isEmpty()) {
            File dir = new File(localPath);
            if (dir.exists() && dir.isDirectory()) {
                editor.putString("local_path", localPath);
            } else {
                Toast.makeText(this, "本地路径不存在或不是文件夹", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Save WebDAV settings
        editor.putString("webdav_url", webDavUrlEditText.getText().toString().trim());
        editor.putString("webdav_username", webDavUsernameEditText.getText().toString().trim());
        editor.putString("webdav_password", webDavPasswordEditText.getText().toString().trim());

        // Save FTP settings
        editor.putString("ftp_server", ftpServerEditText.getText().toString().trim());
        editor.putString("ftp_port", ftpPortEditText.getText().toString().trim());
        editor.putString("ftp_username", ftpUsernameEditText.getText().toString().trim());
        editor.putString("ftp_password", ftpPasswordEditText.getText().toString().trim());

        // Save SMB settings
        editor.putString("smb_server", smbServerEditText.getText().toString().trim());
        editor.putString("smb_share", smbShareEditText.getText().toString().trim());
        editor.putString("smb_username", smbUsernameEditText.getText().toString().trim());
        editor.putString("smb_password", smbPasswordEditText.getText().toString().trim());

        editor.apply();

        Toast.makeText(this, "设置已保存\n当前使用: " + getStorageTypeName(currentStorageType), Toast.LENGTH_LONG).show();
        finish();
    }

    private String getStorageTypeName(String type) {
        if ("local".equals(type)) return "本地存储";
        if ("webdav".equals(type)) return "WebDAV";
        if ("ftp".equals(type)) return "FTP";
        if ("smb".equals(type)) return "SMB/CIFS";
        return "未知";
    }
}
