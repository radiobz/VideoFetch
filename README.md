# VideoFetch 视频下载（安卓）

一个开源的安卓视频下载器，采用 **双引擎** 架构：

- **主流平台引擎（yt-dlp）**：粘贴链接即解析下载，支持抖音、快手、B站、小红书、YouTube、TikTok 等 1000+ 国内外平台。
- **网页嗅探引擎（内置浏览器）**：在 App 内打开任意网页并播放视频，自动拦截嗅探 m3u8 / mp4 / ts 等媒体流，一键下载非主流网页上的视频。

无需 root，无需安装证书。基于 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 与 [youtubedl-android](https://github.com/yausername/youtubedl-android) 构建，UI 参考 [Seal](https://github.com/JunkFood02/Seal) 架构。

## 功能

- [x] 链接解析：标题 / 封面 / 时长 / 上传者 / 清晰度列表
- [x] 档位选择：最佳画质、1080P、720P、480P、仅音频 MP3
- [x] 网页嗅探：内置浏览器拦截 m3u8 / mp4 / webm / flv / ts 等媒体流
- [x] 下载管理：进度 / 速度 / 剩余时间、取消、重试、删除、打开文件
- [x] 任务持久化：重启应用不丢失
- [x] 引擎在线更新：设置页一键更新 yt-dlp 到最新版
- [x] 保存到公共下载目录 `Download/VideoFetch`，无需存储权限（Android 10+）

## 下载安装

- 在 [Releases](../../releases) 下载最新 APK（arm64-v8a），或查看 GitHub Actions 构建产物
- 安装后授予网络权限即可使用；Android 9 及以下首次使用时需允许存储权限

## 使用说明

### 方式一：链接解析（主流平台）

1. 在抖音 / 快手 / B站 / 小红书 / YouTube 等 App 中复制视频分享链接
2. 打开 VideoFetch → 粘贴链接 → 点「解析链接」
3. 选择清晰度 → 开始下载

> 需要登录才能观看的内容（会员、私密视频）可能无法下载；部分平台对非登录下载有限速或风控，可尝试在设置中更新引擎。

### 方式二：网页嗅探（非主流网页）

1. 进入「网页嗅探」页
2. 输入目标网页地址（如某个视频网站的播放页）并打开
3. 在浏览器中播放视频，页面加载的 m3u8 / mp4 媒体流会自动出现在下方列表
4. 点击「下载」即可保存

> 原理：拦截 WebView 的媒体资源请求，识别后交给 yt-dlp 下载。部分网页使用系统播放器或加密混淆加载视频，可能无法识别；可尝试更换播放器（如 hls.js）或使用电脑版页面。

## 从源码构建

环境要求：JDK 17、Android SDK（platform 35、build-tools 35）。

```bash
git clone https://github.com/radiobz/VideoFetch.git
cd VideoFetch
# 在 local.properties 中配置 sdk.dir
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/
```

## 目录结构

```
app/src/main/java/com/radiobz/videofetch/
├── data/          # 任务模型、仓库、引擎封装（EngineManager / DownloadEngine）
├── sniff/         # 网页媒体流嗅探器
└── ui/            # Compose 界面（home / browser / downloads / settings）
```

## 开源许可

本项目以 [GPL-3.0](LICENSE) 发布。核心组件：

- [yt-dlp](https://github.com/yt-dlp/yt-dlp)（Unlicense）
- [youtubedl-android](https://github.com/yausername/youtubedl-android)（GPL-3.0）
- UI 架构参考 [Seal](https://github.com/JunkFood02/Seal)（GPL-3.0）

## 免责声明

本项目仅供学习与个人合法使用。请仅下载你拥有版权或获得授权的内容，尊重各平台服务条款与创作者权益。因使用本工具产生的任何问题由使用者自行承担。
