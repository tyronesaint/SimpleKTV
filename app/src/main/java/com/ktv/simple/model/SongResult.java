package com.ktv.simple.model;

/**
 * 搜索结果歌曲模型
 * 对应 API 搜索结果中的歌曲：
 * {
 *   "id": "3248685",
 *   "name": "数字人生",
 *   "singer": "林子祥",
 *   "album": "最爱",
 *   "source": "kw",
 *   "interval": 193,
 *   "hash": "3248685",
 *   "musicInfo": { ... }
 * }
 * 
 * 关键字段：
 * - source: 获取播放地址必须传给 /api/music/url
 * - id: 获取播放地址必须传给 /api/music/url 的 songId
 * - name: 歌曲名（UI 展示）
 * - singer: 歌手（UI 展示）
 * - interval: 时长（秒，UI 展示）
 */
public class SongResult {
    public String id;            // 歌曲ID ★ 用于获取播放地址
    public String name;          // 歌曲名
    public String singer;        // 歌手
    public String album;         // 专辑
    public String source;        // 来源平台 ★ 用于获取播放地址
    public int interval;         // 时长（秒）
    public String hash;
    public MusicInfo musicInfo;  // 详细信息

    /**
     * 获取格式化的时长字符串 (mm:ss)
     */
    public String getFormattedDuration() {
        int minutes = interval / 60;
        int seconds = interval % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 转换为 Song 对象（用于播放队列）
     */
    public Song toSong() {
        Song song = new Song();
        song.setTitle(name);
        song.setArtist(singer);
        song.setAlbum(album);
        song.setSongId(id);
        song.setSource(source);
        song.setStorageType(Song.StorageType.ONLINE);
        
        // 设置时长（转换为毫秒）
        song.setDurationMs(interval * 1000L);
        
        // 如果有 musicInfo，可以提取更多信息
        if (musicInfo != null) {
            if (musicInfo.albumid != null && !musicInfo.albumid.isEmpty()) {
                // 可以用于获取封面
            }
        }
        
        return song;
    }

    @Override
    public String toString() {
        return String.format("%s - %s [%s]", singer, name, getFormattedDuration());
    }
}
