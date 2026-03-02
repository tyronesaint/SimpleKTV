# SimpleKTV - 极简本地 KTV 应用

## 项目说明

这是一款运行在 Android 4.4+ 系统上的极简本地 KTV 应用，支持本地及网络存储音频文件的播放和管理。

### 系统要求

- 最低兼容：Android 4.4 (API 19)
- 编译工具：JDK 8, Gradle 4.10.1, Android SDK 28

## 本地构建

### 环境准备

1. 安装 JDK 8
2. 安装 Android SDK 28
3. 设置环境变量：
   ```bash
   export JAVA_HOME=/path/to/jdk8
   export ANDROID_HOME=/path/to/android-sdk
   ```

### 构建步骤

```bash
# 1. 给 gradlew 添加执行权限（Linux/Mac）
chmod +x gradlew

# 2. 构建 Debug 版本
./gradlew assembleDebug

# 3. 构建 Release 版本（未签名）
./gradlew assembleRelease
```

### 输出位置

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## GitHub Actions 自动构建

本项目配置了 GitHub Actions 自动构建，每次 push 或 PR 都会触发构建：

1. 进入仓库的 **Actions** 标签
2. 点击最新的构建任务
3. 在 **Artifacts** 区域下载 APK 文件

## 主要功能

- 本地音频文件播放
- WebDAV/FTP/SMB 网络存储支持
- 歌词同步显示
- 音频可视化
- HTTP 远程控制
- 二维码扫码控制

## 技术栈

- Android SDK 28 (兼容 API 19)
- Gradle 4.10.1
- Java 7
- Support Library 28.0.0
- OkHttp 3.12.13
- NanoHTTPD 2.3.1
- ZXing 3.4.0
- jcifs-ng 1.5.1

## 注意事项

- Release APK 未签名，需要手动签名才能发布
- 仅支持 armeabi-v7a (32位) 架构
- 使用 TLS 1.2（避开 TLS 1.3 以兼容旧系统）

## 故障排查

### 构建失败

如果构建失败，请检查：
1. JDK 版本是否为 1.8
2. Android SDK 28 是否安装
3. 网络连接是否正常（需要下载依赖）
4. 查看构建日志了解具体错误

### APK 安装失败

- Debug APK 使用 debug 密钥签名，可以直接安装
- Release APK 未签名，需要签名后才能安装：
  ```bash
  jarsigner -keystore your.keystore app-release-unsigned.apk your-alias
  zipalign -v 4 app-release-unsigned.apk app-release.apk
  ```

## 许可证

MIT License
