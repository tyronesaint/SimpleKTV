package com.ktv.simple.model;

/**
 * 播放地址响应数据模型
 * 对应 API /api/music/url 响应：
 * {
 *   "code": 200,
 *   "msg": "获取成功",
 *   "data": {
 *     "url": "http://xxx.mp3",    // 播放地址
 *     "type": "320k",             // 音质类型
 *     "source": "kw",             // 来源平台
 *     "quality": "320k",          // 音质
 *     "lyric": "[ti:xxx]...",     // 标准歌词（LRC格式）
 *     "tlyric": "",               // 翻译歌词
 *     "rlyric": "",               // 罗马音歌词
 *     "lxlyric": "",              // 逐字歌词
 *     "cached": false             // 是否来自缓存
 *   }
 * }
 */
public class MusicUrlData {
    // === 播放必需 ===
    public String url;          // 播放地址，直接传给播放器
    
    // === 歌词相关 ===
    public String lyric;        // 标准歌词，解析后显示
    public String tlyric;       // 翻译歌词（可选）
    public String rlyric;       // 罗马音（可选）
    public String lxlyric;      // 逐字歌词（可选）
    
    // === 显示信息 ===
    public String type;         // "320k"
    public String quality;      // "320k"
    public String source;       // "kw"
    public boolean cached;      // 是否缓存
    
    /**
     * 是否有歌词
     */
    public boolean hasLyric() {
        return lyric != null && !lyric.isEmpty();
    }
    
    /**
     * 是否有翻译歌词
     */
    public boolean hasTranslation() {
        return tlyric != null && !tlyric.isEmpty();
    }
    
    @Override
    public String toString() {
        return "MusicUrlData{" +
                "url='" + (url != null ? url.substring(0, Math.min(50, url.length())) + "..." : "null") + '\'' +
                ", type='" + type + '\'' +
                ", quality='" + quality + '\'' +
                ", hasLyric=" + hasLyric() +
                '}';
    }
}
