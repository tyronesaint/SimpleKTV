package com.ktv.simple.model;

import java.util.List;

/**
 * 音源模型
 * 从服务器动态获取的音源信息
 */
public class MusicSource {
    private String id;                          // 音源ID
    private String name;                        // 音源名称（如"六音音源"）
    private String description;                 // 音源描述
    private List<String> supportedSources;      // 支持的平台标识列表（如 ["kw", "kg", "tx", "wy", "mg"]）
    private boolean isDefault;                  // 是否默认音源

    public MusicSource() {
    }

    public MusicSource(String id, String name, String description, 
                       List<String> supportedSources, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.supportedSources = supportedSources;
        this.isDefault = isDefault;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getSupportedSources() {
        return supportedSources;
    }

    public void setSupportedSources(List<String> supportedSources) {
        this.supportedSources = supportedSources;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    @Override
    public String toString() {
        return "MusicSource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", supportedSources=" + supportedSources +
                ", isDefault=" + isDefault +
                '}';
    }
}
