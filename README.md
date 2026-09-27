# 音乐悬浮条 · FloatingMusicBar

[![Build APK](https://github.com/SadEggs/musicwindow/actions/workflows/build.yml/badge.svg)](https://github.com/SadEggs/musicwindow/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/SadEggs/musicwindow)](https://github.com/SadEggs/musicwindow/releases)

**[中文](#中文文档) | [English](#english)**

---

## 中文文档

给平板用的**横条状半透明音乐悬浮窗**：全屏游戏时浮在游戏上层，不抢焦点、不挡操作，
显示当前歌曲 + 进度条 + 上一曲 / 播放暂停 / 下一曲，并且可以直接从它展开的面板里**点歌切歌**。
基于通用 **Android MediaSession**，所以 Poweramp、VLC、网易云、QQ 音乐等任何规范实现的播放器都能读。

- **目标设备**：联想小新 Pad Pro 12.7 2025（TB375FC），Android 15。代码不绑定机型，Android 8.0+ 均可使用。
- **来历**：这个 App 由 **DeepSeek V4 Flash** 编写（需求由人提，代码由 AI 写，编译在 GitHub Actions 上跑）。
- **授权**：[MIT](LICENSE) —— **任何人都可以自由编译、修改、发布**（包括自己重新打包分发），
  但**必须署名原作者 SadEggs** 并保留版权声明。详见文末「作者与署名」。
- **零第三方依赖**：纯 framework API，不用 AndroidX、不用 Kotlin，编译快、体积小（release 仅 ~63 KB）。

### ⚠️ 安装 / 更新必读：先"重置"，再判断是不是坏了

**覆盖更新之后，如果出现"什么都没检测到"、悬浮条不显示、或歌名进度不刷新，请先做一次重置，
不要急着认为新版本坏了。**

原因是 Android 系统本身的行为：**App 更新后，系统会解绑该 App 的「通知使用权」
（`NotificationListenerService`）**，而系统不一定马上重新绑定 —— 悬浮条于是"什么都检测不到"。
旧版本把这种情况误判成"没有授权"，只能靠清除数据恢复。

**v0.9 起已经修好**：App 会自己请系统重新绑定（`requestRebind`），并且在没有会话时持续自动重试，
正常情况下更新完直接就能用。检测不到时，先点 App 里的「重新检测播放器」按钮即可。

重置办法（从轻到重，第 1 步通常就够了）：

| 顺序 | 做法 | 影响 |
| --- | --- | --- |
| 0 | App 里点「**重新检测播放器**」（v0.9 起） | 最轻，**不丢任何设置**，App 请系统重新绑定通知监听 |
| 1 | 设置 → 通知使用权（通知访问）→ 把「音乐悬浮条」**关掉，再打开** | 轻，不丢任何设置 |
| 2 | 重启平板 | 轻，不丢设置 |
| 3 | 应用信息 → 存储 → **清除数据** | 最彻底；会重置厚度/位置/透明度等设置，之后需要**重新授权全部权限** |

> 下面这些情况也用同样的办法处理：状态页一直提示「请授予通知使用权」但权限其实已经给了；
> 悬浮条出来了但歌名/进度一直是旧的不更新。
>
> 三种都试过还是不行，请把 App 状态页的整段内容发出来（里面有点播能力和浏览服务的诊断信息），
> 那一段能直接定位问题。

### 下载

**稳定版（推荐）**：https://github.com/SadEggs/musicwindow/releases/latest

| 版本 | 内容 |
| --- | --- |
| **v0.10**（最新稳定版） | **点歌不再关掉随机播放**（自动记住并恢复随机/循环模式）；媒体库面板恢复**从左到右、从上到下**排序 |
| v0.9 | **修好"更新后检测不到播放器"**（自动重新绑定通知监听 + 一键重新检测）；✕ 改到文件夹图标右边；滑动阈值可在设置里调；切歌提示音可选关闭/仅蓝牙/始终 |
| v0.8 | 滑动切歌改为「**滑出下一首预览 + 松手确认**」 |
| v0.7 | 按编号点播（Poweramp）、暂停状态下点歌可用、主界面切换歌曲时不再闪烁消失、媒体库面板 |
| v0.5 | 钉子（固定位置）按钮、固定签名密钥 |
| v0.1 ~ v0.4 | 悬浮条基础功能、滑动切歌、细线进度条 |

**测试版**（实验功能，发布时标记 Pre-release，不会顶掉稳定版）：

| 版本 | 内容 |
| --- | --- |
| v0.6-beta1 | 面板长度与主界面一致、多路点歌、点播能力诊断 |
| v0.5-beta1 | 媒体库面板首次出现 |

平板上直接用浏览器打开上面的链接就能下载 APK。

### 安装与升级

1. 把 APK 传到平板（数据线 / 网盘 / 微信文件传输助手都行）。
2. 用文件管理器点开 APK，按提示允许「安装未知应用」。
3. 或者用数据线：`adb install -r app-release.apk`

**关于签名**：早期版本（v0.4 及更早）每次编译都用当场新生成的密钥签名，所以装新版本会报
「签名不一致」必须卸载。现在已改为**固定密钥**（`app/keystore.b64`，工作流第一次运行时生成并提交），
**v0.5 及以后的所有版本签名完全一致**，可以直接覆盖安装，不用再卸载。

> 只有从 **v0.4 或更早**升级时，才需要最后卸载重装一次。之后永久不用。
>
> 密钥口令写在 `app/build.gradle` 里，而仓库是公开的，所以这个密钥等于公开的。对自用 sideload
> 足够（别人拿到它也只能签出一个"同名"APK，无法往本仓库发版）。若在意，可改用 GitHub Actions
> Secret 保存密钥（需要给令牌加 Secrets 权限）。

### 首次使用：四步授权

打开 App，顶部就是授权清单，**点文字那一行**就会跳到对应系统设置：

| 权限 | 必须？ | 说明 |
| --- | --- | --- |
| 悬浮窗权限 | **必须** | 「显示在其他应用上层」，否则条根本画不出来 |
| 通知使用权 | **必须** | 用来读取播放器的媒体会话（歌名 / 进度 / 控制）。本 App **不读取通知内容** |
| 忽略电池优化 | 建议 | 不加白名单，系统可能在游戏时把悬浮条回收 |
| 音乐和音频 | 点歌需要 | 读取音乐文件夹，用于展开媒体库面板 |
| 通知权限 | 可选 | 只影响那条常驻通知是否可见，服务本身不受影响 |

授权后回到 App，点 **启动悬浮条** 即可。

> **第一次启动看不到条？** 默认开了「仅在播放时显示」—— 没有正在播放/暂停的音乐时，
> 条会自动隐藏。先用播放器放一首歌它就会出来；想随时验证效果，把设置里「仅在播放时显示」关掉。

### 日常操作

- **向左滑动条身**：下一首 ／ **向右滑动**：上一曲 —— 滑动时会把**下一首的曲名滑进来预览**
  （副标题显示「继续滑动 / 松手切换」），**滑过设定阈值（默认 40%，设置里可调）松手才真正切歌**；
  松手太早会自动弹回、歌曲不变。这样在不固定（可拖动）状态下也能一眼分清"我在切歌"还是"我在移条"。
- **切歌提示音**：默认**只在蓝牙音频时**响一声（外放静音），设置里可选「关闭 / 仅蓝牙 / 始终」。
  悬浮条和面板上的按钮都关掉了系统"触摸提示音"，所以外放时**任何操作都不会有提示音**。
  （若蓝牙时还听到不同的"嘟嘟"声，那是耳机自己在响应 AVRCP 切歌，App 无法控制。）
- **单击条身文字区**：播放 / 暂停（文字区有**淡边框**标出可滑动区域，滑动时边框会点亮）。
  注意：条处于**变淡的静止状态**时，**第一下点击只是把条点亮**（不会暂停音乐）—— 想点播放/暂停
  请点第二下，或者点中间的播放按钮。这是为了避免"想让它变清楚一点，结果音乐被暂停了"。
- **按钮**（上一曲 / 播放暂停 / 下一曲 / 钉子 / 文件夹）：**占满整条高度**、宽 48dp（播放键 54dp），
  轻按下会有**轻微触感反馈**，所以即使条很细也点得准
- **长按条身后拖动**：挪到任意位置（松手后自动记住）
- **点钉子按钮**：固定 / 取消固定。钉住后条**完全不能移动**（长按拖动也失效），图标变琥珀色，避免游戏中误碰挪走。
- **点右端文件夹图标**：展开 / 收起媒体库面板。**面板展开时它右边会多出一个 ✕ 按钮**，
  点它就关闭面板（面板头部也有一个 ✕）。（**长按**文件夹图标仍是折叠成小把手）
- **点小把手**：展开回横条
- **拖动中间的细线**：跳转进度（细线上的圆点就是当前位置）
- 进度显示是「一根 2px 细线 + 一个圆点」：`12:34 . . . . o . . . . 45:07`，没有醒目的色块。

### 点歌（在面板里点一首歌直接切过去）

这是本项目最花功夫的部分，因为播放器对外部点歌的支持各不相同。点一首歌时按下面的顺序尝试，
每一步都会连续确认歌曲是否真的换了，没换才试下一种：

1. **按编号点播**（`playFromMediaId`）—— 最准。先连上播放器自己的**媒体浏览服务**
   （Android Auto 用的那套接口，Poweramp 的是 `com.maxmpz.audioplayer/...BrowserService`），
   在它的曲库里按标题 + 歌手匹配到这首歌，拿到播放器**自己给这首歌分配的编号**再点播。
   查找是严格有界的：一层层订阅、最多 300 个目录 / 4000 首、4 秒预算、命中精确匹配立刻停止 ——
   曲库再大也不会卡住悬浮条，而且一定会返回结果。
2. **按名称点播**（`playFromSearch`）—— Android Auto / 语音助手用的标准请求。
3. **按文件点播**（`playFromUri`）—— 直接给出该文件的 MediaStore 地址。

**随机播放模式会被保住**（v0.10 起）：播放器接到"播这一首"的请求时通常会**重建播放队列**，
而随机播放是跟着队列走的 —— 所以从面板点歌会顺手把随机播放关掉。现在点歌前会先记住
**随机 + 循环模式**，歌曲真正切过去之后再放回去；模式没变就完全不动它。
设置里的「**点歌时保持随机播放模式**」（默认开）可以关掉这个行为。

> 关于实现：这两个模式在不同 Android 版本上的接口位置并不统一，**编译用的 `android.jar` 里
> `PlaybackState` 既没有 `SHUFFLE_MODE_*` 常量也没有 `getShuffleMode()`**，直接调用根本编译不过
> —— 所以本 App 先用**反射**尝试读取；**如果设备上根本读不到**（Poweramp 就是这种情况，状态页会
> 显示 `随机接口=none`），就改用下面的办法：
>
> **播放器自己的通知栏按钮**。本 App 持有通知使用权，能直接看到播放器的媒体通知，而它的
> **随机播放按钮就是播放器真正的开关**（图标会随开/关变化）。做法是：点歌前记下这个按钮的
> **图标指纹**，点歌后再比一次 —— 图标变了，说明播放器自己把随机关掉了，就**按一下它的按钮**
> 按回来；图标没变就什么也不做（绝不盲目切换，避免"本来没开随机"反被打开）。
>
> 设置里的「**点歌后恢复随机播放**」可选用哪种办法：**自动（推荐）** = 能读就用接口、读不到就用
> 通知按钮；也可强制用**接口指令**或**播放器自定义动作**。状态页会显示 `随机接口=`、`通知按钮=`、
> `自定义动作=`，按不动按钮时把这三项发我。
>
> 另外「**点播方式**」可以把点歌请求固定成 `按编号 / 按名称 / 按文件` —— 不同请求在播放器里可能走
> 不同代码路径，如果其中某一种能自然保住随机播放，固定它就是最干净的解法。

**暂停状态下点歌也已修复**：有些播放器（例如 Poweramp）在暂停时收到点播请求，
会把新歌装进队列**但不开始播放**，旧版本因此误判失败并去试下一种，结果状态被搅乱。
现在检测到"歌换了但还在暂停"会**自动补一个播放指令**，所以暂停时点歌也能直接唱起来。

三种都试完还没换歌，才会提示「播放器没有响应点播请求」。全程**不会**把播放器切到前台，游戏不会被切出去。

App 状态页底部有诊断信息，出问题时把这段发给我即可：

```
点播支持: 按名称=是 按文件=是 按编号=是
播放器浏览服务: 已按编号精确匹配到曲目 com.maxmpz.audioplayer
媒体浏览服务
  com.android.bluetooth/com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService
  com.maxmpz.audioplayer/com.maxmpz.audioplayer.data.external.BrowserService
  org.videolan.vlc/org.videolan.vlc.PlaybackService
```

- **点播支持**读的是播放器自己声明的能力（MediaSession transport actions）。三个都是「否」，
  说明这个播放器根本不打算接受外部点歌，那就只能用上一曲/下一曲。
- **播放器浏览服务**显示本次查找的结果：精确匹配 / 近似匹配 / 曲库里没有 / 未连接。

### 默认参数（都能在设置页改）

| 项目 | 默认值 | 说明 |
| --- | --- | --- |
| 形状 | 横条，厚 **3 cm** | 用屏幕物理 DPI 换算（273 ppi → 3cm ≈ 322 px，约屏高 17.5%） |
| 长度 | 屏幕宽度的 **60%** | 可用 20%~100% 灵活调 |
| 位置 | 底部居中，距边 8 dp | 可拖到任意位置，也可改回顶部停靠 |
| 静止不透明度 | **35%** | 4 秒不碰它自动淡到 35%，一碰回到 90% |
| 显示时机 | 仅在有播放/暂停会话时出现 | 彻底停止播放后自动隐藏 |
| 折叠把手 | 1.6 cm 圆把手 | 点条右端箭头折叠，点把手展开 |
| 专辑封面 | 默认关闭 | 开启后左侧显示 46dp 缩略图 |
| 媒体库面板 | 3 列磁贴，占屏高约 1/3 | **从左到右、从上到下**排布，长度与主界面一致 |

### 已知限制（重要）

1. **条的矩形区域会吃掉触摸**。悬浮窗是一整个窗口，条覆盖的地方游戏点不到。缓解：折叠成小把手、
   停靠在游戏 UI 空档处、调低不透明度。这是 Android 悬浮窗的固有限制，不是 bug。
2. **权限缺失时不显示**：没给悬浮窗权限就画不出来；没给通知使用权只能显示提示文字。
3. **歌名跑马灯**：Android 对非焦点窗口的跑马灯支持不稳定，某些 ROM 上长歌名会直接截断不滚动。
4. **部分游戏会主动隐藏悬浮窗**（Android 12+ 的 `setHideOverlayWindows`，或带反外挂的游戏），
   这类游戏里任何悬浮窗都出不来，无解。
5. **物理尺寸依赖系统上报的 DPI**。若 3cm 量出来有偏差，把「短边厚度（厘米）」按比例微调。
6. **媒体库面板读的是系统的 `MediaStore` 索引**，不是播放器自己的曲库（点歌时才去查播放器曲库）。
   某些文件夹要等系统重新扫描后才会出现。

### 自己编译（无需本地环境）

代码在 GitHub Actions 上云端编译，本机不需要 JDK / Android SDK。工作流：`.github/workflows/build.yml`
（自装 cmdline-tools + `platforms;android-35` + `build-tools;35.0.0`，Gradle 8.9 / AGP 8.7.3 / Java 17）。

```powershell
git add -A
git commit -m "你的改动"
git push origin main          # 触发编译
git tag -a v0.8 -m "..."      # 打标签则会额外生成带 APK 附件的 Release
git push origin v0.8
```

- `main` 分支 = 稳定版；`beta` 分支 = 实验版（发布时自动标记 Pre-release）。
- 版本号规则：稳定版 `v0.7`，测试版 `v0.7-beta1`，测试版的 `versionCode` 低于同名稳定版，便于覆盖升级。

### 目录结构

```
FloatingMusicBar/
├─ LICENSE                        MIT
├─ .github/workflows/build.yml    云端编译（Actions）
├─ settings.gradle / build.gradle / gradle.properties
└─ app/
   ├─ build.gradle                无第三方依赖，纯 framework API
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/musicbar/overlay/
      │  ├─ MainActivity.java         授权向导 + 设置页 + 诊断
      │  ├─ OverlayService.java       前台服务：悬浮窗、折叠、媒体库面板、点歌阶梯
      │  ├─ MusicBarView.java         条的 UI（歌名/进度条/五个按键/封面/把手）
      │  ├─ FolderPanelView.java      媒体库面板（三列磁贴、逐级进入）
      │  ├─ PlayerBrowser.java        播放器媒体浏览服务客户端（按编号点播）
      │  ├─ MediaLibrary.java         MediaStore 音乐库扫描与目录树
      │  ├─ MediaBridge.java          媒体会话读取、进度插值、控制、封面解码
      │  ├─ MediaListenerService.java 通知监听（换取 getActiveSessions 权限）
      │  ├─ BootReceiver.java         开机自启
      │  └─ Prefs.java                所有配置项 + cm→px 物理换算
      └─ res/                         图标、圆角背景、进度条、中文文案
```

### 踩过的坑（开发笔记）

1. **不要用 `android-actions/setup-android@v3`**：它会去装早已下架的 `tools` 包，在 ubuntu-24.04 上
   直接报 `Failed to find package 'tools'` 把流水线搞挂。现在自装 cmdline-tools。
2. **推 `.github/workflows/` 下的文件，token 必须有 `Workflows` 权限**，否则报
   `refusing to allow a Personal Access Token to create or update workflow`。
3. **本机 git 的 Schannel 不可用时**（`schannel: AcquireCredentialsHandle failed`），推送加
   `-c http.sslBackend=openssl`。
4. **自定义 View 里不能写裸的 `MATCH_PARENT` / `WRAP_CONTENT`**，必须写
   `LayoutParams.MATCH_PARENT`（嵌套类成员不会继承进子类作用域）。
5. **设置页输入框不能只在失焦时保存**：触摸模式下点 Button / SeekBar / 空白处都不会让 EditText
   失焦，`onFocusChange(false)` 永不触发 —— 曾导致「改了没用」。现在改为每次输入即时保存 + 防抖。
6. **数字输入要容错**：中文输入法全角数字和逗号（`2，5`）会让 `Float.parseFloat` 抛异常。
7. **`MediaBrowserService` 有两个同名类**：framework 的是 `android.service.media.MediaBrowserService`，
   AndroidX 的是 `androidx.media.app.MediaBrowserService`；而清单 `<queries>` 里要写的是
   **接口动作名** `android.media.browse.MediaBrowserService`。写错任何一个都编译不过。

### 作者与署名（请保留）

- **原作者**：**SadEggs** — https://github.com/SadEggs
- **代码作者**：**DeepSeek V4 Flash**（人提需求 → AI 写代码 → GitHub Actions 云端编译出 APK）
- **授权**：[MIT](LICENSE)

**你可以**：自由编译、修改、二次开发、重新打包，并把自编译的 APK 发布到任何地方（自用、送人、上架都行）。

**唯一要求**：**必须署名原作者 SadEggs**，并保留本仓库的版权声明与 LICENSE。也就是说，转载、
发布或再分发时请注明：

> 原始项目：FloatingMusicBar by SadEggs — https://github.com/SadEggs/musicwindow

---

## English

A **thin, translucent music overlay bar for Android tablets**. It floats above a full-screen game
without stealing focus or blocking input, showing the current song, a hairline progress bar and
previous / play-pause / next. A panel that unfolds from the bar browses your music folders and
starts any song you tap. Built on the standard **Android MediaSession** APIs, so any player that
implements them works — Poweramp, VLC, NetEase Cloud Music, QQ Music and so on.

- **Target device**: Lenovo Xiaoxin Pad Pro 12.7 2025 (TB375FC), Android 15. Nothing is
  device-specific; Android 8.0+ works.
- **How it was made**: written by **DeepSeek V4 Flash** — a human set the requirements, the model
  wrote the code, and GitHub Actions builds the APK in the cloud.
- **License**: [MIT](LICENSE) — **anyone may freely compile, modify, repackage and publish it**
  (including redistributing your own build), as long as the original author **SadEggs** is credited
  and the copyright notice is kept. See "Credits and attribution" below.
- **Zero third-party dependencies**: plain framework APIs, no AndroidX, no Kotlin. Small and fast
  (the release APK is about 63 KB).

### ⚠️ Read this before installing or updating: reset first, then judge

**If, after updating over an existing install, the app "detects nothing", the bar does not appear,
or the title and progress stop refreshing — do a reset before assuming the new version is broken.**

The cause is Android itself, not this app: **after an app is updated, the system unbinds that app's
notification listener access (`NotificationListenerService`)**, so it stops handing the player's
media session to the app. A fresh install is normally unaffected; this shows up when installing
over an older version.

Reset methods, from lightest to heaviest — step 0 is usually enough:

| Order | What to do | Impact |
| --- | --- | --- |
| 0 | Tap **"Re-check player"** in the app (v0.9 and later) | Lightest, **keeps all your settings**; the app asks the system to bind the listener again |
| 1 | Settings → Notification access → turn **off**, then **on** again for FloatingMusicBar | Light, keeps every setting |
| 2 | Reboot the tablet | Light, keeps settings |
| 3 | App info → Storage → **Clear data** | Heaviest; resets thickness/position/opacity and you must **grant every permission again** |

> v0.9 fixes this properly: the app asks the system to rebind the listener by itself and keeps
> retrying while it finds no session, so an update normally just works.

> The same reset also fixes: the status page keeps asking for notification access even though it is
> granted, and a bar that appears but never updates its title or progress.
>
> If none of the three help, send the whole status page text — it contains the point-song capability
> and browser-service diagnostics, which pinpoint the problem.

### Download

**Stable (recommended)**: https://github.com/SadEggs/musicwindow/releases/latest

| Version | What is in it |
| --- | --- |
| **v0.10** (latest stable) | **Picking a song no longer turns shuffle off** (the shuffle / repeat mode is remembered and put back), and the library panel is ordered **left to right, then top to bottom** again |
| v0.9 | **Fixes "detects nothing after an update"** (the app asks the system to rebind the listener by itself, plus a one-tap re-check button); the **✕** moved next to the folder button; the swipe threshold became a setting; the track-change tone can be off / Bluetooth only / always |
| v0.8 | Swiping previews the incoming song and only switches when you let go past the threshold |
| v0.7 | Play by library id (Poweramp), point-song now works while paused, no more bar flicker when the track changes, media library panel |
| v0.5 | Pin (lock position) button, fixed signing key |
| v0.1 – v0.4 | The core bar, swipe to change track, hairline progress bar |

**Beta builds** are published as pre-releases and never take over the "Latest" badge:

| Version | What is in it |
| --- | --- |
| v0.6-beta1 | Panel length matched to the bar, multi-route point-song, capability diagnostics |
| v0.5-beta1 | First version of the media library panel |

### Install and upgrade

1. Copy the APK to the tablet (USB, cloud drive, or a chat app to yourself).
2. Open it with a file manager and allow "install unknown apps" when asked.
3. Or use adb: `adb install -r app-release.apk`

**About signing**: versions up to v0.4 were signed with a throwaway debug key generated on a fresh
CI runner each time, so upgrading required an uninstall ("signature mismatch"). All versions are now
signed with a **fixed key** (`app/keystore.b64`, generated and committed by the workflow on its first
run), so **v0.5 and later all share the same signature** and install straight over each other.

> Only an upgrade from **v0.4 or earlier** needs that one-time uninstall.
>
> The key password lives in `app/build.gradle`, and the repository is public, so the key is
> effectively public. That is fine for personal sideloading (someone else could only sign a
> same-named APK, not publish to this repository). If you care, move it into a GitHub Actions secret.

### First run: grant the permissions

The permission checklist is at the top of the app; **tap the text row** to jump to the matching
system screen.

| Permission | Required? | Why |
| --- | --- | --- |
| Display over other apps | **Yes** | Without it the bar cannot be drawn at all |
| Notification access | **Yes** | Reads the player's media session (title / progress / controls). The app does **not** read notification content |
| Ignore battery optimisation | Recommended | Otherwise the system may kill the overlay during a game |
| Music and audio | For point-song | Reads music folders for the library panel |
| Notifications | Optional | Only affects the ongoing notification's visibility |

Then tap **Start** in the app.

> **No bar on first start?** "Show only while playing" is on by default, and with nothing playing
> the bar stays hidden. Play something and it appears, or turn that option off to see it right away.

### Everyday use

- **Swipe left / right** on the bar: next / previous track. While you swipe, the incoming song's
  title slides in with a "keep sliding / let go to switch" hint, and the switch only happens if you
  release past the threshold (40% by default, adjustable in the settings) - release earlier and
  everything springs back with the song unchanged. That makes it obvious, even with the bar unpinned
  and draggable, whether a gesture is changing the song or moving the bar.
- **Track-change tone**: by default it only beeps **when audio is on Bluetooth**, so the tablet
  speaker stays silent; the setting offers off / Bluetooth only / always. Sound effects are switched
  off on the bar and the panel, so no system touch sound is heard either. A headset that beeps on its
  own when the track changes does so from its own firmware and cannot be controlled by any app.
- **Tap the title area**: play / pause. The title area carries a faint **outline** marking the swipe
  zone, which lights up while a swipe is in progress. When the bar is in its **dimmed resting state**,
  the first tap only brings it back to full strength instead of pausing the music — tap once to wake
  it, then again to toggle, or use the play button in the middle.
- **Buttons** (previous / play-pause / next / pin / folder): they fill the whole height of the bar and
  are 48dp wide (54dp for play) with a light haptic tick, so they stay easy to hit on a thin bar.
- **Long-press, then drag**: move it anywhere; the position is remembered.
- **Pin button**: locks the position so it cannot be moved by accident (the icon turns amber).
- **Folder button**: unfold / fold the library panel. While the panel is open a **✕** appears just to
  its right, to close the panel again. **Long-press** the folder button to collapse the bar instead.
- **Drag the hairline**: seek; the dot on the line is the current position.
- Progress is drawn as a 2px hairline with a dot: `12:34 . . . . o . . . . 45:07`.

### Point-song (tap a song in the panel to play it)

Players differ in what they accept, so a tap tries the following in order, checking after each
attempt whether the track really changed:

1. **By library id** (`playFromMediaId`) — the most accurate. The app connects to the player's own
   media browser service (the API Android Auto uses; Poweramp exposes
   `com.maxmpz.audioplayer/...BrowserService`), matches the song by title and artist in that
   library, and plays it by the id the player itself assigned. The lookup is strictly bounded:
   breadth-first subscription waves, at most 300 folders and 4000 items, a 4-second budget, and it
   stops on the first exact title match — so a huge library can never hang the bar.
2. **By name** (`playFromSearch`) — the standard request Android Auto and voice assistants use.
3. **By file** (`playFromUri`) — the file's MediaStore URI.

**The shuffle mode is preserved** (v0.10 and later): a player usually answers "play this one song" by
building a fresh queue, and shuffle belongs to the queue it discarded — which is why picking a song
used to turn shuffle off. The app now remembers the **shuffle and repeat** modes before the request
and puts them back once the song has really changed; if nothing changed, it does nothing at all. The
setting "Keep shuffle when picking songs" (on by default) turns this off.

> On the implementation: those accessors are not in a consistent place across Android versions, and
> the `android.jar` this app compiles against publishes **neither `SHUFFLE_MODE_*` constants nor
> `getShuffleMode()` on `PlaybackState`** — a direct call would not even compile. The app therefore
> tries reflection first; where the device publishes nothing at all (Poweramp is such a case, and the
> status page then says `随机接口=none`) it falls back to the player's **own notification button**,
> which is the player's real shuffle switch and carries its state in its icon. That icon is
> fingerprinted before a point-song and compared after it: if it changed, the player flipped the mode
> itself and one press puts it back; if it did not change, nothing is pressed at all, so shuffle is
> never switched on for someone who had it off.
>
> The setting "restore shuffle after a point-song" chooses the mechanism (automatic, transport command
> or the player's own custom action), and a "point-song route" setting pins the request to media id,
> search or file uri — if one of those routes keeps the player's shuffle, that is the cleanest fix.

**Point-song while paused is fixed**: some players (Poweramp among them) accept the request, load
the new track, and then just sit there because playback was paused — older builds misread that as a
failure and moved on to the next strategy, scrambling the state. The app now detects "track changed
but still paused" and sends a resume command, so tapping a song while paused actually starts it.

The player is **never** brought to the foreground, so your game is never switched out.

### Known limitations

1. **The bar's rectangle consumes touches** — an overlay is a single window, so the game cannot be
   tapped where the bar covers it. Mitigations: collapse it, dock it over unused UI, lower the
   opacity. Inherent to Android overlays.
2. **Nothing shows without permissions**; without notification access only a hint is displayed.
3. **Marquee text** for long titles is unreliable on unfocused windows and some ROMs just truncate.
4. **Some games hide all overlays** (`setHideOverlayWindows` on Android 12+, or anti-cheat), and
   nothing can be done about those.
5. **Physical sizing depends on the DPI the system reports**; if 3 cm measures off, adjust the
   thickness value proportionally.
6. **The library panel reads the system `MediaStore` index**, not the player's own library (the
   player's library is only consulted for point-song). New folders may need a media rescan.

### Building

Everything is built in GitHub Actions; no local JDK or Android SDK is needed. See
`.github/workflows/build.yml` (installs cmdline-tools plus `platforms;android-35` and
`build-tools;35.0.0`, Gradle 8.9 / AGP 8.7.3 / Java 17).

```powershell
git add -A
git commit -m "your change"
git push origin main          # triggers a build
git tag -a v0.8 -m "..."      # a tag also publishes a GitHub release with the APKs
git push origin v0.8
```

- `main` is the stable line, `beta` holds experiments and is published as a pre-release.
- Versioning: stable `v0.7`, beta `v0.7-beta1`, and the beta's `versionCode` stays below the stable
  one of the same name so it upgrades cleanly.

### Credits and attribution (please keep)

- **Original author**: **SadEggs** — https://github.com/SadEggs
- **Code written by**: **DeepSeek V4 Flash** — a human set the requirements, the model wrote the
  code, and GitHub Actions builds the APK in the cloud.
- **License**: [MIT](LICENSE)

**You may** freely compile, modify, extend, repackage and publish this app — for yourself, to share,
or on a store.

**The one requirement**: you must credit the original author **SadEggs** and keep this repository's
copyright notice and LICENSE. In practice, when you redistribute it or publish your own build,
link back to the original project:

> Original project: FloatingMusicBar by SadEggs — https://github.com/SadEggs/musicwindow

### License

[MIT](LICENSE) © 2026 SadEggs. The Android app is original work with no third-party dependencies;
the Gradle/AGP versions only affect the build, not the shipped code. Attribution to the original
author is required when you redistribute it or publish your own build.
