package com.ktv.simple.model;

import java.util.List;

/**
 * 搜索响应数据模型
 * 对应 API 响应格式：
 * {
 *   "code": 200,
 *   "msg": "搜索成功",
 *   "data": { ... }
 * }
 */
public class SearchResponse {
    public int code;
    public String msg;
    public SearchData data;

    public boolean isSuccess() {
        return code == 200;
    }

    public String getErrorMessage() {
        return msg != null ? msg : "未知错误";
    }
}
