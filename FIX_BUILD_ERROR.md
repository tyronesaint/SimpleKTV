# 构建错误修复指南

## 错误信息

```
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
Error: Process completed with exit code 1.
```

## 问题原因

Gradle Wrapper 需要一个 jar 文件（`gradle-wrapper.jar`），但这个文件是二进制文件，无法通过文本编辑创建。

## 解决方案

### 方案 1：下载 gradle-wrapper.jar（推荐）

```bash
# 在项目根目录执行
mkdir -p gradle/wrapper
curl -L https://github.com/gradle/gradle/raw/v4.10.1/gradle/wrapper/gradle-wrapper.jar \
  -o gradle/wrapper/gradle-wrapper.jar
chmod +x gradlew
./gradlew assembleDebug
```

### 方案 2：使用系统 Gradle（最快）

```bash
# 直接使用系统 gradle
gradle assembleDebug
gradle assembleRelease
```

或使用提供的构建脚本：

```bash
chmod +x build.sh
./build.sh
```

### 方案 3：重新生成 Gradle Wrapper

如果系统已安装 Gradle：

```bash
gradle wrapper --gradle-version=4.10.1
```

## 验证

执行以下命令验证文件结构：

```bash
ls -la gradle/wrapper/
```

应该看到：
```
gradle-wrapper.jar
gradle-wrapper.properties
```

## GitHub Actions

GitHub Actions 已配置为自动下载 wrapper jar，所以 CI/CD 环境无需手动处理。

## 常见问题

### Q: 下载 wrapper jar 失败？
A: 检查网络连接，或使用方案 2（系统 Gradle）

### Q: 系统没有 Gradle？
A: 安装 Gradle：
```bash
# Ubuntu/Debian
sudo apt-get install gradle

# macOS
brew install gradle
```

### Q: Gradle 版本不兼容？
A: 使用 Gradle 4.10.1 或更高版本，确保兼容性
