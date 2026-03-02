#!/bin/bash

# SimpleKTV 构建修复脚本

set -e

echo "======================================"
echo "SimpleKTV 构建修复"
echo "======================================"

# 1. 清理构建缓存
echo ""
echo "[1/6] 清理构建缓存..."
rm -rf .gradle/
rm -rf app/build/
rm -rf build/
echo "✓ 清理完成"

# 2. 检查环境变量
echo ""
echo "[2/6] 检查环境变量..."
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "警告：未设置 ANDROID_HOME 环境变量"
    echo ""
    echo "请设置环境变量："
    echo "  export ANDROID_HOME=/path/to/android/sdk"
    echo "  export PATH=\$PATH:\$ANDROID_HOME/tools:\$ANDROID_HOME/platform-tools"
    echo ""
    echo "或继续（使用默认路径）..."
fi

# 3. 检查 Java 版本
echo ""
echo "[3/6] 检查 Java 版本..."
if command -v java &> /dev/null; then
    java -version
else
    echo "错误：未找到 Java"
    exit 1
fi

# 4. 检查 Gradle 版本
echo ""
echo "[4/6] 检查 Gradle 版本..."
if command -v gradle &> /dev/null; then
    gradle --version
else
    echo "错误：未找到 Gradle"
    exit 1
fi

# 5. 尝试构建（使用 --no-daemon 避免守护进程问题）
echo ""
echo "[5/6] 开始构建..."
echo "（使用 --no-daemon 模式）"
echo ""

# 尝试构建，捕获详细错误
set +e
gradle clean assembleDebug --no-daemon --stacktrace 2>&1 | tee build.log
BUILD_STATUS=${PIPESTATUS[0]}
set -e

# 6. 显示结果
echo ""
echo "[6/6] 构建结果..."
if [ $BUILD_STATUS -eq 0 ]; then
    echo "✓ 构建成功！"
    echo ""
    APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)
    if [ -n "$APK_PATH" ]; then
        echo "APK 位置: $APK_PATH"
        echo "文件大小: $(du -h "$APK_PATH" | cut -f1)"
    fi
else
    echo "✗ 构建失败"
    echo ""
    echo "查看详细错误："
    echo "  cat build.log"
    echo ""
    echo "常见问题："
    echo "  1. 依赖下载失败 -> 检查网络连接"
    echo "  2. Android SDK 未安装 -> 安装 SDK 28"
    echo "  3. 代码编译错误 -> 查看 build.log"
    echo "  4. 内存不足 -> 增加 Java 堆内存：export GRADLE_OPTS=\"-Xmx2048m\""
fi

echo ""
echo "======================================"
