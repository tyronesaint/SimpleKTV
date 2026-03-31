#!/bin/bash

# 简听KTV 构建脚本（不使用 Gradle Wrapper）
# 需要系统已安装 Gradle 4.10.1 或更高版本

set -e

echo "======================================"
echo "简听KTV 构建脚本"
echo "======================================"

# 检查 Java 版本
echo ""
echo "[1/5] 检查 Java 环境..."
if ! command -v java &> /dev/null; then
    echo "错误：未找到 Java"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
echo "Java 版本: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt "8" ]; then
    echo "警告：建议使用 Java 8"
fi

# 检查 Gradle 版本
echo ""
echo "[2/5] 检查 Gradle 环境..."
if ! command -v gradle &> /dev/null; then
    echo "错误：未找到 Gradle"
    echo ""
    echo "请安装 Gradle 4.10.1 或更高版本："
    echo "  - Ubuntu/Debian: sudo apt-get install gradle"
    echo "  - macOS: brew install gradle"
    echo "  - 或访问: https://gradle.org/install/"
    exit 1
fi

GRADLE_VERSION=$(gradle --version | grep "Gradle" | awk '{print $2}')
echo "Gradle 版本: $GRADLE_VERSION"

# 清理之前的构建
echo ""
echo "[3/5] 清理之前的构建..."
gradle clean || {
    echo "警告：clean 失败，继续构建..."
}

# 构建调试版 APK
echo ""
echo "[4/5] 构建调试版 APK..."
gradle assembleDebug --stacktrace || {
    echo ""
    echo "错误：构建失败！"
    echo ""
    echo "常见问题排查："
    echo "  1. 检查 ANDROID_HOME 环境变量"
    echo "  2. 确保已安装 Android SDK 28"
    echo "  3. 检查网络连接（需要下载依赖）"
    echo ""
    exit 1
}

# 查找生成的 APK
echo ""
echo "[5/5] 查找生成的 APK..."
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)

if [ -z "$APK_PATH" ]; then
    echo "错误：未找到生成的 APK 文件"
    exit 1
fi

echo ""
echo "======================================"
echo "构建成功！"
echo "======================================"
echo ""
echo "APK 位置: $APK_PATH"
echo "文件大小: $(du -h "$APK_PATH" | cut -f1)"
echo ""
echo "安装到设备："
echo "  adb install $APK_PATH"
echo ""
