package com.ktv.simple.model;

/**
 * 歌曲详细信息模型
 * 对应 API musicInfo 字段：
 * {
 *   "id": "3248685",
 *   "name": "数字人生",
 *   "singer": "林子祥",
 *   "album": "最爱",
 *   "duration": 193,
 *   "interval": 193,
 *   "songmid": "3248685",
 *   "hash": "3248685",
 *   "albumid": "68600"
 * }
 */
public class MusicInfo {
    public String id;
    public String name;
    public String singer;
    public String album;
    public int duration;         // 时长（秒）
    public int interval;         // 时长（秒）
    public String songmid;
    public String hash;
    public String albumid;       // 专辑ID，可用于获取封面
}
