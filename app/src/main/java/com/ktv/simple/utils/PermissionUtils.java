package com.ktv.simple.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限工具类 - Android 4.4 兼容
 */
public class PermissionUtils {

    /**
     * 检查权限是否已授予
     */
    public static boolean hasPermission(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查多个权限是否已授予
     */
    public static boolean hasPermissions(Activity activity, String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 请求权限
     */
    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    /**
     * 检查权限请求结果
     */
    public static boolean verifyPermissions(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取未授予的权限列表
     */
    public static List<String> getDeniedPermissions(Activity activity, String[] permissions) {
        List<String> deniedPermissions = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
        }
        return deniedPermissions;
    }

    /**
     * 检查是否应该显示权限说明
     */
    public static boolean shouldShowRequestPermissionRationale(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
        }
        return false;
    }
}
