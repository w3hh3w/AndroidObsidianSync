# ObsidianSync

ObsidianSync 是一个 Android 应用，用于通过 Gitee/GitHub 同步你的 Obsidian 笔记库。

## 功能特点

- **多平台支持**：支持 Gitee 和 GitHub 两个代码托管平台
- **OAuth 认证**：安全地通过 OAuth 2.0 进行用户认证
- **仓库管理**：添加、删除本地 Git 仓库
- **双向同步**：支持 Pull（拉取）和 Push（推送）操作
- **定时同步**：支持设置自动同步间隔（需手动启用）
- **安全存储**：使用 EncryptedSharedPreferences 安全存储 OAuth Token
- **后台同步**：使用 WorkManager 实现后台定时同步
- **仓库状态**：查看本地仓库的修改状态

## 使用步骤

### 1. 配置 OAuth 应用

在使用应用之前，你需要在 Gitee 或 GitHub 上创建 OAuth 应用：

**Gitee 配置：**
1. 访问 [Gitee OAuth 设置](https://gitee.com/oauth/applications)
2. 点击「创建应用」
3. 填写应用信息：
   - 应用名称：ObsidianSync
   - 应用主页：任意 URL（如 https://obsidiansync.app）
   - 应用回调地址：`obsidiansync://oauth/callback/gitee`
4. 获取 Client ID 和 Client Secret

**GitHub 配置：**
1. 访问 [GitHub OAuth Settings](https://github.com/settings/developers)
2. 点击「New OAuth App」
3. 填写应用信息：
   - Application name：ObsidianSync
   - Homepage URL：任意 URL
   - Authorization callback URL：`obsidiansync://oauth/callback/github`
4. 获取 Client ID 和 Client Secret

### 2. 在应用中配置 OAuth

1. 首次打开应用会进入登录页面
2. 点击右上角「设置」按钮进入设置页面
3. 在「OAuth 配置」区域：
   - 输入 Gitee 的 Client ID 和 Client Secret，点击「保存」
   - 或输入 GitHub 的 Client ID 和 Client Secret，点击「保存」
4. 配置状态显示「已配置」即表示成功

### 3. 登录账号

1. 在设置页面，找到对应的平台
2. 点击「登录」按钮
3. 浏览器会打开授权页面，点击「授权」
4. 授权成功后自动返回应用

### 4. 添加仓库

1. 返回主页面，点击右下角「+」按钮
2. 选择平台（Gitee 或 GitHub）
3. 选择本地文件夹（用于存放笔记）
4. 输入仓库 URL（如 `https://gitee.com/username/obsidian-notes`）
5. 等待仓库克隆完成

### 5. 同步笔记

- **手动同步**：点击仓库列表中的同步图标
- **自动同步**：在仓库设置中开启自动同步，并设置同步间隔

## 打包说明

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 17
- Gradle 8.0+
- Android SDK 34

### 打包步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd ObsidianSync
   ```

2. **在 Android Studio 中打开**
   - File -> Open -> 选择 ObsidianSync 目录
   - 等待 Gradle 同步完成

3. **构建 Debug APK**
   - Build -> Build Bundle(s) / APK(s) -> Build APK(s)
   - 或使用终端命令：`./gradlew assembleDebug`

4. **构建 Release APK**
   - 首先创建签名配置（如果没有）：
     - 在 Project Structure -> Signing 中添加签名
   - Build -> Generate Signed Bundle / APK
   - 选择 APK
   - 选择或创建密钥库
   - 完成签名配置
   - 点击 Build

5. **APK 位置**
   - Debug: `app/build/outputs/apk/debug/app-debug.apk`
   - Release: `app/build/outputs/apk/release/app-release.apk`

### 注意事项

- 确保在 `local.properties` 中正确配置了 Android SDK 路径
- Release 构建需要配置签名密钥
- 首次构建可能需要较长时间下载依赖

## 常见问题

### Q1: OAuth 配置失败怎么办？

确保：
- Client ID 和 Client Secret 输入正确
- 应用回调地址与代码中一致：
  - Gitee: `obsidiansync://oauth/callback/gitee`
  - GitHub: `obsidiansync://oauth/callback/github`
- Gitee/GitHub 账号有权限创建 OAuth 应用

### Q2: 仓库克隆失败怎么办？

- 检查网络连接
- 确认仓库 URL 正确
- 确认已登录对应平台
- 检查本地存储空间是否充足

### Q3: 同步失败如何排查？

- 检查网络是否稳定
- 确认 Token 未过期（尝试重新登录）
- 查看仓库是否有冲突文件
- 检查本地文件夹权限

### Q4: 如何更新 Token？

在设置页面登出后重新登录即可获取新的 Token。

### Q5: 支持哪些分支？

当前版本默认同步 `main` 分支。如需支持其他分支，需要修改代码中的 `setBranch("main")` 调用。

### Q6: 数据存储在哪里？

- 仓库数据：应用内部存储 `app.getExternalFilesDir(null)`
- 配置信息：EncryptedSharedPreferences（安全加密存储）
- 数据库：Room SQLite 数据库

## 项目结构

```
ObsidianSync/
├── app/
│   └── src/main/
│       ├── java/com/obsidiansync/
│       │   ├── ObsidianSyncApp.kt        # Application 类
│       │   ├── domain/                    # 业务逻辑层
│       │   │   ├── AuthManager.kt        # 认证管理
│       │   │   └── SyncManager.kt        # 同步管理
│       │   ├── data/local/                # 数据层
│       │   │   └── GitRepositoryManager.kt # Git操作
│       │   ├── ui/                        # UI层
│       │   │   ├── auth/                  # 登录页面
│       │   │   ├── main/                  # 主页面
│       │   │   └── settings/              # 设置页面
│       │   └── service/                   # 服务层
│       └── res/                           # 资源文件
├── build.gradle                           # 项目构建配置
└── settings.gradle                        # 项目设置
```

## 技术栈

- **语言**：Kotlin
- **最小 SDK**：24 (Android 7.0)
- **目标 SDK**：34 (Android 14)
- **主要依赖**：
  - AndroidX Core
  - Room (数据库)
  - Retrofit (网络请求)
  - JGit (Git 操作)
  - WorkManager (后台任务)
  - Kotlin Coroutines (异步编程)
  - Security-Crypto (加密存储)

## 许可证

MIT License
