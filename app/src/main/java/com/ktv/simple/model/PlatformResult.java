package com.ktv.simple.model;

import java.util.List;

/**
 * 平台搜索结果模型
 * 对应 API results 数组中的每个元素：
 * {
 *   "platform": "kw",
 *   "name": "酷我音乐",
 *   "keyword": "演员",
 *   "page": 1,
 *   "results": [ ... ]
 * }
 */
public class PlatformResult {
    public String platform;      // 平台标识：kw/kg/tx/wy/mg
    public String name;          // 平台名称：酷我音乐
    public String keyword;
    public int page;
    public List<SongResult> results;  // 歌曲列表

    /**
     * 获取歌曲数量
     */
    public int getSongCount() {
        return results != null ? results.size() : 0;
    }
}
