# 简听KTV - 极简 KTV 应用

## 项目说明

简听KTV 是一款运行在 Android 4.4+ 系统上的极简 KTV 应用，专为电视、音箱等横屏设备设计。

**重要说明：**
- 本项目**不提供任何音源和服务器**
- 用户需要自行搭建或获取音乐服务地址
- 由于 Android 4.4 系统限制，**仅支持 MP3 格式播放**

### 系统要求

- 最低兼容：Android 4.4 (API 19)
- 目标设备：电视、音箱等横屏设备
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

- **在线搜索**：通过配置的服务器搜索歌曲
- **歌词同步**：实时歌词显示
- **远程控制**：扫码连接手机/电脑控制
- **横屏优化**：专为电视、音箱等横屏设备设计

## 技术栈

- Android SDK 28 (兼容 API 19)
- Gradle 4.10.1 + Android Gradle Plugin 3.2.1
- Java 7 (无 Lambda/Stream API)
- Support Library 28.0.0 (非 AndroidX)
- OkHttp 3.12.13 (TLS 1.2 支持)
- NanoHTTPD 2.3.1 (远程控制服务)
- ZXing 3.4.0 (二维码生成)
- Glide 4.9.0 (图片加载)
- Gson 2.8.6 (JSON 解析)

## 配置说明

### 首次使用

1. 打开应用，首次使用会提示"请先配置音乐服务器地址"
2. 点击"去配置"进入设置页面
3. 在"服务器地址"输入框中填入您的服务器地址
4. 点击"测试连接"按钮验证
5. 显示成功后配置自动保存

### 服务器地址格式

```
https://域名/API_KEY
https://192.168.1.100:8080/your-key
```

系统会自动解析服务器地址和密钥。

### 关于音源

- 本项目**不提供任何音源**
- 用户需要自行搭建音乐服务或使用已有的服务
- 服务返回的音频地址必须为 **MP3 格式**（Android 4.4 不支持其他格式）

## 注意事项

- 仅编译 armeabi-v7a (32位) 架构
- 使用 TLS 1.2 以兼容 Android 4.4
- 屏幕方向固定为横屏（sensorLandscape）
- **仅支持 MP3 格式播放**
- 不预设任何服务器地址

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
