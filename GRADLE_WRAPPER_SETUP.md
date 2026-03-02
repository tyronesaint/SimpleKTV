# Gradle Wrapper 安装说明

由于 Gradle Wrapper 的 jar 文件是二进制文件，需要单独下载。

## 方法 1：下载 Gradle Wrapper jar

```bash
# 在 SimpleKTV 根目录执行以下命令：

# 创建 gradle/wrapper 目录（如果不存在）
mkdir -p gradle/wrapper

# 下载 gradle-wrapper.jar（Gradle 4.10.1）
curl -L https://github.com/gradle/gradle/raw/v4.10.1/gradle/wrapper/gradle-wrapper.jar \
  -o gradle/wrapper/gradle-wrapper.jar

# 或者使用 wget
wget https://github.com/gradle/gradle/raw/v4.10.1/gradle/wrapper/gradle-wrapper.jar \
  -O gradle/wrapper/gradle-wrapper.jar
```

## 方法 2：使用系统 Gradle 构建（推荐）

如果系统已安装 Gradle，可以直接使用：

```bash
# 使用系统 Gradle 构建
gradle assembleDebug
gradle assembleRelease
```

## 方法 3：初始化 Gradle Wrapper

```bash
# 如果系统有 Gradle 4.10.1，可以重新生成 wrapper
gradle wrapper --gradle-version=4.10.1
```

## 方法 4：从标准项目复制

如果你有其他使用 Gradle 的项目，可以复制：
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

## 验证安装

安装完成后，验证文件结构：

```
SimpleKTV/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar  ← 必须存在
│       └── gradle-wrapper.properties
├── gradlew          ← Unix/Linux/Mac
└── gradlew.bat      ← Windows
```

然后执行构建：

```bash
chmod +x gradlew
./gradlew assembleDebug --stacktrace
```

## GitHub Actions 配置说明

GitHub Actions 会自动处理 Gradle Wrapper，所以 CI/CD 环境不需要手动下载。
