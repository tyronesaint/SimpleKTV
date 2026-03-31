package com.ktv.simple.model;

import java.util.List;

/**
 * 搜索数据模型
 * 对应 API data 字段：
 * {
 *   "keyword": "演员",
 *   "page": 1,
 *   "limit": 2,
 *   "results": [ ... ]
 * }
 */
public class SearchData {
    public String keyword;
    public int page;
    public int limit;
    public List<PlatformResult> results;  // 按平台分组的结果

    /**
     * 获取所有歌曲（从所有平台结果中提取）
     */
    public java.util.List<SongResult> getAllSongs() {
        java.util.List<SongResult> allSongs = new java.util.ArrayList<>();
        if (results != null) {
            for (PlatformResult platform : results) {
                if (platform.results != null) {
                    allSongs.addAll(platform.results);
                }
            }
        }
        return allSongs;
    }

    /**
     * 获取歌曲总数
     */
    public int getTotalCount() {
        int count = 0;
        if (results != null) {
            for (PlatformResult platform : results) {
                if (platform.results != null) {
                    count += platform.results.size();
                }
            }
        }
        return count;
    }
}
