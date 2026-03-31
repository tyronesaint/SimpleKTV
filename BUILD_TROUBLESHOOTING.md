# 构建错误诊断指南

## 常见构建错误及解决方案

### 错误 1：依赖下载失败

**症状**：
```
Could not resolve com.android.support:appcompat-v7:28.0.0
```

**解决方案**：
```bash
# 清理缓存
rm -rf ~/.gradle/caches/
rm -rf .gradle/

# 重新构建
gradle assembleDebug --refresh-dependencies
```

### 错误 2：Android SDK 未配置

**症状**：
```
sdk.dir is missing
```

**解决方案**：

创建 `local.properties` 文件：
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

或手动设置：
```bash
export ANDROID_HOME=/path/to/android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
```

### 错误 3：内存不足

**症状**：
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：
```bash
export GRADLE_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
gradle assembleDebug
```

### 错误 4：守护进程问题

**症状**：
```
Gradle daemon has been stopped
```

**解决方案**：
```bash
# 停止所有守护进程
gradle --stop

# 使用 --no-daemon 构建
gradle assembleDebug --no-daemon
```

### 错误 5：许可证未接受

**症状**：
```
You have not accepted the license agreements of the following SDK components
```

**解决方案**：
```bash
# 接受许可证
yes | sdkmanager --licenses || true
```

### 错误 6：编译错误

**症状**：
```
error: package android.support.v7.app does not exist
```

**解决方案**：
```bash
# 清理并重新构建
gradle clean
gradle assembleDebug --stacktrace
```

### 错误 7：资源文件错误

**症状**：
```
error: resource not found
```

**解决方案**：
检查资源文件是否完整：
```bash
ls -la app/src/main/res/
ls -la app/src/main/assets/
```

## 获取详细错误信息

### 方法 1：查看完整日志
```bash
gradle assembleDebug --stacktrace --info 2>&1 | tee build.log
cat build.log
```

### 方法 2：使用修复脚本
```bash
chmod +x fix-build.sh
./fix-build.sh
```

### 方法 3：查看最后 100 行错误
```bash
gradle assembleDebug --stacktrace 2>&1 | tail -n 100
```

## 环境检查清单

```bash
# 检查 Java
java -version

# 检查 Gradle
gradle --version

# 检查 Android SDK
echo $ANDROID_HOME

# 检查 SDK 工具
ls $ANDROID_HOME/build-tools/28.0.3/

# 检查 SDK 平台
ls $ANDROID_HOME/platforms/android-28/

# 检查网络连接
curl -I https://dl.google.com/android/maven2/
```

## 最简构建测试

如果所有方法都失败，尝试最简单的构建：

```bash
# 只编译代码，不打包 APK
gradle compileDebugSources --stacktrace

# 如果编译成功，再尝试打包
gradle assembleDebug --stacktrace
```

## GitHub Issues

如果问题仍然存在，请提供以下信息：

1. 完整的错误日志（`gradle assembleDebug --stacktrace` 的输出）
2. 系统信息：
   ```bash
   java -version
   gradle --version
   echo $ANDROID_HOME
   ```
3. 操作系统版本
4. 错误发生步骤

## 推荐构建流程

```bash
# 1. 清理
gradle clean

# 2. 接受许可证（如果需要）
yes | sdkmanager --licenses || true

# 3. 构建调试版
gradle assembleDebug --no-daemon --stacktrace

# 4. 如果失败，查看日志
cat build.log
```
