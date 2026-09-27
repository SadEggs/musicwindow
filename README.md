# 音乐悬浮条 FloatingMusicBar

给平板用的**横条状半透明音乐悬浮窗**：全屏游戏时浮在游戏上层，不抢焦点、不挡操作，
显示当前歌曲 + 进度条 + 上一曲 / 播放暂停 / 下一曲。基于通用 **Android MediaSession**，
所以 Poweramp、网易云、QQ 音乐、B 站等任何规范实现的播放器都能读。

设备目标：联想小新 Pad Pro 12.7 2025（TB375FC），Android 15。
（代码不绑定机型，其它 Android 8.0+ 设备也能装。）

---

## 1. 默认参数（都能在设置页改）

| 项目 | 默认值 | 说明 |
| --- | --- | --- |
| 形状 | 横条，厚 **3 cm** | 用屏幕物理 DPI 换算成像素（你的板子约 273 ppi → 3cm ≈ 322 px，约屏高 17.5%） |
| 长度 | 屏幕宽度的 **60%** | 可用 20%~100% 灵活调 |
| 位置 | 底部居中，距边 8 dp | 可拖到任意位置，也可改回顶部停靠 |
| 静止不透明度 | **35%** | 4 秒不碰它自动淡到 35%，一碰回到 90% |
| 显示时机 | 仅在有播放/暂停会话时出现 | 彻底停止播放后自动隐藏 |
| 折叠把手 | 1.6 cm 圆把手 | 点条右端箭头折叠，点把手展开 |
| 专辑封面 | 默认关闭 | 开启后左侧显示 46dp 缩略图，把手内显示圆形封面 |

---

## 2. 怎么拿到 APK（GitHub Actions 云端编译）

本机没有 JDK / Android SDK，也不需要装——全部在 GitHub 上编译。

### 一次性准备

```powershell
cd "D:\AI Program\Workspace\FloatingMusicBar"
git init -b main
git add .
git commit -m "FloatingMusicBar v1.0"
```

然后在 GitHub 网页上新建一个**空仓库**（不要勾选 README），拿到地址后：

```powershell
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

### 拿到 APK（两条路，任选）

**A. 直接下载 APK（最省事，推荐）**

```powershell
git tag v1.0
git push origin v1.0
```

推送 tag 后，Actions 会自动编译，并在仓库的 **Releases** 页面附上两个 APK，
点一下就能直接下 APK 文件（不是 zip）。

**B. 从 Actions 构件下载**

仓库 → **Actions** 标签 → 左侧 “Build APK” → 右上 **Run workflow**（或直接等 push 触发）
→ 跑完后在该次运行页面底部 **Artifacts** 下载 `FloatingMusicBar-debug`（是 zip，
解压后里面的 `app-debug.apk` 就是要装的文件）。

> 首次编译约 3~5 分钟（要下 Gradle 和 SDK）。
> 工作流文件：`.github/workflows/build.yml`

### 出问题了？

- Android Studio 版本较老、想本地编译：把根目录 `build.gradle` 里的 AGP 版本
  `8.7.3` 调低（如 `8.5.2`），`gradle.properties` 不用动。
- 想看编译日志：Actions 页面点进那次运行，展开 `Build debug APK` 步骤。

---

## 3. 装到平板

1. 把 APK 传到平板（微信文件传输助手 / 数据线 / 网盘都行）。
2. 用平板的文件管理器点开 APK，按提示允许「安装未知应用」。
3. 或者用数据线 + adb：`adb install -r app-debug.apk`

---

## 4. 首次使用：四步授权

打开 App，页面顶部就是授权清单，**点文字那一行**就会跳到对应系统设置：

| 权限 | 必须？ | 说明 |
| --- | --- | --- |
| 悬浮窗权限 | **必须** | 「显示在其他应用上层」，否则条根本画不出来 |
| 通知使用权 | **必须** | 用来读取播放器的媒体会话（歌名/进度/控制）。本 App 不读取通知内容 |
| 忽略电池优化 | 建议 | 不加白名单，系统可能在游戏时把悬浮条回收 |
| 通知权限 | 可选 | 只影响那条常驻通知是否可见，服务本身不受影响 |

授权后回到 App，点 **启动悬浮条**，底部就会出现横条。

> **第一次启动看不到条？** 默认开了「仅在播放时显示」——没有正在播放/暂停的音乐时，
> 条会自动隐藏。先用 Poweramp 放一首歌它就会出来；想随时验证效果，把设置里
> 「仅在播放时显示」关掉即可。

### 日常操作

- **单击条身文字区**：播放 / 暂停
- **拖动条身**：挪到任意位置（松手后自动记住）
- **点右端箭头**：折叠成小把手（把手内显示专辑封面或音符图标）
- **点小把手**：展开回横条
- **拖动进度条**：跳转进度
- **上一曲 / 播放暂停 / 下一曲**：三个按键

---

## 5. Poweramp 注意事项

- Poweramp 需要保持 **通知（媒体控制）** 功能开启，系统才会暴露它的媒体会话。
  如果通知被彻底关掉，悬浮条会读不到歌名。
- 如果条上一直显示「请授予通知使用权」，但权限其实给了：说明系统把
  NotificationListener 回收了，去通知使用权页面把它关掉再重新打开即可。
- 建议在 Poweramp 设置里把「通知」保持开启，并在最近任务里给两个 App 都加锁。

---

## 6. 设置项一览

- **尺寸与位置**：短边厚度（cm）、长度占屏比（%）、停靠边缘（顶/底）、边缘间距（dp）、
  折叠把手尺寸（cm）、清除拖动位置
- **外观与行为**：静止不透明度、触摸时不透明度、静止淡出延迟（0 = 不淡出）、
  仅在播放时显示、显示歌手名、显示时间文字、显示专辑封面、开机自动启动
- **高级**：优先控制的播放器包名（默认 `com.maxmpz.audioplayer`）、打开系统设置页

改任何设置都会**立即生效**（正在运行的悬浮条会原地重建）。

---

## 7. 已知限制（重要）

1. **条的矩形区域会吃掉触摸**。悬浮窗是一整个窗口，条覆盖的地方游戏点不到。
   缓解办法：折叠成小把手、把条停靠在游戏 UI 空档处（顶部/侧边）、调低不透明度。
   这是 Android 悬浮窗的固有限制，不是 bug。
2. **权限缺失时不显示**：没给悬浮窗权限就画不出来；没给通知使用权只能显示提示文字。
3. **歌名跑马灯**：Android 对非焦点窗口的跑马灯支持不稳定，某些 ROM 上长歌名会直接
   截断不滚动。如果遇到，把「长度占屏比」调大即可。
4. **部分游戏会主动隐藏悬浮窗**（Android 12+ 的 `setHideOverlayWindows`，或带反外挂的
   游戏）。这类游戏里任何悬浮窗都出不来，无解。
5. **物理尺寸依赖系统上报的 DPI**。若 3cm 量出来有偏差，直接把「短边厚度（厘米）」
   按比例微调（例如实测量到 3.3cm，就填 2.7）。

---

## 8. 目录结构

```
FloatingMusicBar/
├─ .github/workflows/build.yml   云端编译（Actions）
├─ settings.gradle / build.gradle / gradle.properties
└─ app/
   ├─ build.gradle               无第三方依赖，纯 framework API
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/musicbar/overlay/
      │  ├─ MainActivity.java        授权向导 + 设置页
      │  ├─ OverlayService.java      前台服务，持有悬浮窗（尺寸/位置/淡出/拖动/折叠）
      │  ├─ MusicBarView.java        条的 UI（歌名/进度条/三个按键/封面/把手）
      │  ├─ MediaBridge.java         媒体会话读取、进度插值、控制、封面解码
      │  ├─ MediaListenerService.java 通知监听（换取 getActiveSessions 权限）
      │  ├─ BootReceiver.java        开机自启
      │  └─ Prefs.java               所有配置项 + cm→px 物理换算
      └─ res/                        图标、圆角背景、进度条、中文文案
```

工程刻意做到**零第三方依赖**（不用 AndroidX、不用 Kotlin），所以编译快、体积小、
不会因为依赖下载失败而挂掉。
