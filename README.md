# 音乐悬浮条 · FloatingMusicBar 1.0

[![Build APK](https://github.com/SadEggs/musicwindow/actions/workflows/build.yml/badge.svg)](https://github.com/SadEggs/musicwindow/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/SadEggs/musicwindow)](https://github.com/SadEggs/musicwindow/releases)

**[中文](#中文) | [English](#english)**

---

## 中文

**给平板用的横条状半透明音乐悬浮窗。** 全屏玩游戏时它浮在游戏上层，**不抢焦点、不挡住操作**，
一根 3 cm 厚的细条上显示：专辑封面、歌名、进度、以及上一曲 / 播放暂停 / 下一曲。
点右端的文件夹按钮会展开一个面板，直接在文件夹里点歌切歌。

**1.0 的重点：随机播放由 App 自己完成。** 详见下面「随机 / 顺序」一节 ——
这是本项目花最多功夫的地方，也是它和别的悬浮窗最不一样的地方。

- **目标设备**：联想小新 Pad Pro 12.7 2025（TB375FC），Android 15，2944×1840 横屏。
  代码不绑定机型，Android 8.0+ 都能用。
- **播放器**：基于通用 **Android MediaSession**，**为 Poweramp 适配并实测**，
  其他规范实现的播放器（VLC、网易云、QQ 音乐等）**理论上同样可用**，欢迎反馈。
- **语言**：中文 / English，设置页一键切换（悬浮窗、面板、通知一起跟着变）。
- **零第三方依赖**：纯 framework API，不用 AndroidX、不用 Kotlin。APK 约 105 KB。
- **授权**：[MIT](LICENSE)，可自由编译、修改、发布，**需署名原作者 SadEggs**。

### 下载

**最新正式版**：https://github.com/SadEggs/musicwindow/releases/latest

| 版本 | 内容 |
| --- | --- |
| **v1.0.1** | 修好「英文界面下系统里仍显示中文名」：应用英文名改为 **FloatingMusicBar**，并把 App 内选择的语言**同步给系统**（Android 13+，桌面图标名/应用信息页/安装界面一起跟着变） |
| **v1.0** | **随机/顺序播放引擎**（App 自己排歌，突破播放器「文件夹队列不递归」的限制）、**面板四个开关**（随机/顺序、含子文件夹/仅本层、单曲循环、点歌带队列）、队列位置 `X/XX` 显示、中英双语完整覆盖、面板显示子文件夹歌曲数 |
| v0.22 ~ v0.29 | 逐版本打磨随机引擎与面板开关（详见 Releases 页） |
| v0.10 | 点歌不再关掉随机播放；面板改回从左到右、从上到下排序 |
| v0.9 | 修好「更新后检测不到播放器」（自动重新绑定通知监听 + 一键重新检测） |
| v0.8 | 滑动切歌改为「滑出预览 + 松手确认」 |
| v0.7 | 按编号点播（Poweramp）、暂停状态下点歌可用、媒体库面板 |
| v0.5 | 钉子（固定位置）按钮、固定签名密钥 |
| v0.1 ~ v0.4 | 悬浮条基础功能、滑动切歌、细线进度条 |

### 安装与升级

1. 平板上用浏览器打开上面的链接，下载 `app-release.apk`（**不是** `app-debug.apk`）。
2. 用文件管理器点开它，按提示允许「安装未知应用」。
3. 或者用数据线：`adb install -r app-release.apk`

**签名**：v0.5 起所有版本使用**固定密钥**签名，可以互相覆盖安装。只有从 v0.4 或更早升级，
才需要最后卸载重装一次。若下载后提示「应用未安装 / 软件包似乎无效」：先确认文件大小与 Releases
页显示的一致（下载中断是最常见的原因），换系统浏览器重下，或先卸载旧版再装。

### 首次使用：四步授权

打开 App，顶部就是授权清单，**点文字那一行**会跳到对应的系统设置。

| 权限 | 必须？ | 说明 |
| --- | --- | --- |
| 悬浮窗权限 | **必须** | 「显示在其他应用上层」，否则条画不出来 |
| 通知使用权 | **必须** | 读取播放器的媒体会话（歌名 / 进度 / 控制）。本 App **不读取通知内容** |
| 忽略电池优化 | 建议 | 不加白名单，系统可能在游戏时回收悬浮窗 |
| 音乐和音频 | 面板需要 | 扫描音乐文件夹，供面板浏览和点歌 |
| 通知权限 | 可选 | 只影响那条常驻通知是否可见，服务本身不受影响 |

> 1.0 的随机播放由 App 自己完成，**不再写任何 m3u 播放列表文件**，所以不再需要「所有文件访问」。
> 设置页里遗留的旧入口可以不给权限。

授权后回到 App，点 **启动悬浮条**。**第一次看不到条？** 默认开了「仅在播放时显示」——
没有正在播放/暂停的音乐时它会隐藏，先放一首歌就会出现。

### 日常操作

- **点条身文字区**：播放 / 暂停。条变淡时，第一下点击只是把条点亮（不会暂停音乐）。
- **在文字框内左右滑动**：下一首 / 上一曲 —— 滑动时会把**下一首的曲名滑进来预览**，
  **滑过阈值（默认 40%，可调）松手才真正切歌**，松手太早会自动弹回。
- **按钮**（上一曲 / 播放暂停 / 下一曲 / 钉子 / 文件夹）占满整条高度，宽 48dp（播放键 54dp），
  按下有轻微触感反馈，条再细也点得准。
- **长按条身拖动**：挪到任意位置（松手记住）；点**钉子**固定后完全不能移动（图标变琥珀色）。
- **点文件夹图标**：展开 / 收起媒体库面板（面板右边会多一个 ✕，面板头部也有）。
- **拖动中间的细线**：跳转进度。进度画成「2px 细线 + 一个圆点」，不挡游戏画面。
- **切歌提示音**：默认**只在蓝牙音频时**响，外放静音（可选 关闭 / 仅蓝牙 / 始终）。

### 随机 / 顺序：App 自己排歌（1.0 的核心）

**为什么需要它。** 播放器自己的队列覆盖多大范围，是播放器说了算。实测 **Poweramp 从文件夹播放时
只播本层，子文件夹的歌不会进队列**（在它自己的界面里也一样），而且它**不对外公开自己的播放队列**。
所以没有哪个 MediaSession 接口能把"一整棵树 + 随机"交给播放器 ——
早期版本试过把树写成 `.m3u` 交给 Poweramp，实测**行不通**（它会把 m3u 当成一首歌，报「播放失败」跳过；
也不会把外部 m3u 收进自己的播放列表）。

**1.0 的做法：App 自己当随机引擎。** 面板点歌时 App 自己排出队列（含子文件夹的整棵树），
一首一首交给播放器播，并盯着进度在曲末自动接下一首。代价是**每首之间约 0.3~1 秒的空隙**
（播放器无法被要求"无缝接下一首"），换来的是**完整的随机范围和队列位置显示**。

**面板头部的四个开关**（点一下切换，都会记住）：

| 开关 | 打开 | 关闭 |
| --- | --- | --- |
| **🔀 随机 / ➡ 顺序** | 随机播放（App 自己洗牌） | 按文件夹原本顺序依次播放，走完从头再来 |
| **含子文件夹 / 仅本层** | 范围 = 当前文件夹**及其下所有子文件夹** | 只在**当前文件夹自己的歌**里 |
| **🔂 单曲循环** | 点某一首歌就**一直重复这一首**，直到切换别的模式 | 正常播放 |
| **点歌带队列** | 点歌时这首歌作为**整个队列的第一首**（效果同「▶ 全部」，只是从这首开始） | 只播这一首 |

- **「▶ 全部」**（面板左上角）：按上面三个范围相关的开关播放**整个文件夹范围**。
- **点某一首歌**：● 单曲循环开着 → 只重复这一首；● 否则「点歌带队列」开着 → 这首歌是队列第一首
  （随机模式后面接随机顺序；顺序模式从这首歌往后排，到末尾回到前面）；● 都关 → 只播这一首。
- 悬浮窗上方会显示 **`X/XX`**，表示当前是队列里的第几首、一共几首。

**点歌技术阶梯**：面板点歌时依次尝试 ① 播放器自己的媒体浏览服务按编号点播（Poweramp 支持）
② `playFromSearch` ③ 直接播文件。每一步都会确认歌曲是否真的换了，没换才试下一种；
**全程不会把播放器切到前台，游戏不会被切出去**。暂停状态下点歌会自动补一个播放指令。

### 默认参数（设置页都能改）

| 项目 | 默认值 | 说明 |
| --- | --- | --- |
| 形状 | 横条，厚 **3 cm** | 用屏幕物理 DPI 换算（273 ppi → 约 322 px，屏高 ~17.5%） |
| 长度 | 屏幕宽度的 **60%** | 20%~100% 可调 |
| 位置 | 底部居中，距边 8 dp | 可拖到任意位置，也可改回顶部停靠 |
| 静止不透明度 | **35%** | 4 秒不碰自动淡到 35%，一碰回到 90% |
| 显示时机 | 仅在有播放/暂停会话时 | 彻底停止播放后自动隐藏 |
| 专辑封面 | 默认关闭 | 开启后左侧显示 46dp 缩略图 |
| 媒体库面板 | 3 列磁贴 | 子文件夹行显示「名字 · N 首」（N 含子文件夹） |

### 已知限制

1. **App 自己排歌的空隙**：每首之间约 0.3~1 秒（详见上文）。这是"突破播放器队列限制"的代价。
2. **条的矩形范围会吃掉触摸**：悬浮窗是一整个窗口，条盖住的地方游戏点不到。
   缓解：折叠成小把手、停靠在游戏 UI 空档、调低不透明度。这是 Android 悬浮窗的固有限制。
3. **不读播放器的随机开关**：Poweramp 的随机是**循环切换**（顺序→随机歌曲→随机分类→…），
   没有可用的读取接口（状态页 `随机接口=none`），任何"帮你按回去"的做法都会改掉你设的档位，
   所以 App **完全不碰它** —— 随机播放由 App 自己实现，与你播放器里的设置无关。
4. **部分游戏会主动隐藏悬浮窗**（Android 12+ 的 `setHideOverlayWindows`，或带反外挂的游戏），
   这类游戏里任何悬浮窗都出不来，无解。
5. **长歌名跑马灯**在非焦点窗口上不可靠，某些 ROM 会直接截断。
6. **面板读的是系统 `MediaStore` 索引**，不是播放器自己的曲库（点歌时才查播放器曲库）。
   新文件夹可能要等系统重新扫描。
7. **物理尺寸依赖系统上报的 DPI**，若 3 cm 量出来有偏差，按比例微调「短边厚度」。

### 更新后「检测不到播放器」？

Android 在 **App 更新后会解绑它的通知使用权**，悬浮条于是什么都读不到。App 会自己请系统重新绑定
并持续重试；若仍不行，按从轻到重处理：**① 点 App 里的「重新检测播放器」→ ② 设置里把通知使用权
关掉再打开 → ③ 重启平板 → ④ 清除数据**（会重置设置，需重新授权）。前两步不丢任何设置。

### 自己编译（无需本地环境）

代码在 GitHub Actions 上云端编译，本机不需要 JDK / Android SDK。
工作流：`.github/workflows/build.yml`（cmdline-tools + `platforms;android-35` + `build-tools;35.0.0`，
Gradle 8.9 / AGP 8.7.3 / Java 17）。

```powershell
git add -A
git commit -m "你的改动"
git push origin main          # 触发编译
git tag -a v1.0 -m "..."      # 打标签会额外生成带 APK 的 Release
git push origin v1.0
```

推 `main` = 稳定版线；推 `beta` = 实验版线。标签里的版本号决定 Release 名字。

### 目录结构

```
FloatingMusicBar/
├─ LICENSE                        MIT
├─ .github/workflows/build.yml    云端编译（Actions）
└─ app/src/main/
   ├─ AndroidManifest.xml
   ├─ java/com/musicbar/overlay/
   │  ├─ MainActivity.java         授权向导 + 设置页
   │  ├─ OverlayService.java       前台服务：悬浮窗、面板、随机引擎、点歌阶梯
   │  ├─ MusicBarView.java         条的 UI（封面/歌名/细线进度/五个按键/把手）
   │  ├─ FolderPanelView.java      面板（三列磁贴、四个开关、点歌）
   │  ├─ PlayerBrowser.java        播放器媒体浏览服务客户端（按编号点播）
   │  ├─ MediaLibrary.java         MediaStore 音乐库扫描与目录树
   │  ├─ MediaBridge.java          媒体会话读取、进度插值、控制、封面解码
   │  ├─ MediaListenerService.java 通知监听（换取 getActiveSessions 权限）
   │  ├─ Lang.java                 中英切换
   │  ├─ Prefs.java                所有配置项 + cm→px 物理换算
   │  └─ BootReceiver.java         开机自启
   └─ res/                         图标、圆角背景、进度条、中英文案
```

### 作者与署名（请保留）

- **原作者 / 需求提出**：**SadEggs** — https://github.com/SadEggs
- **代码编写**：**DeepSeek V4 Flash**（人提需求 → AI 写代码 → GitHub Actions 云端编译出 APK）
- **授权**：[MIT](LICENSE)

**你可以**自由编译、修改、二次开发、重新打包，并把自编译的 APK 发布到任何地方（自用、送人、上架都行）。

**唯一要求**：**必须署名原作者 SadEggs**，并保留本仓库的版权声明与 LICENSE：

> 原始项目：FloatingMusicBar by SadEggs — https://github.com/SadEggs/musicwindow

---

## English

**A thin, translucent music overlay bar for Android tablets.** It floats above a full-screen game
**without stealing focus or blocking input**: one 3 cm-tall strip showing cover art, title,
progress and previous / play-pause / next. The folder button at its right end unfolds a browser
panel that plays any song you tap.

**The 1.0 headline: the app is its own shuffle engine.** See "Shuffle and order" below — it is the
part of this project that took the most work, and the thing that sets it apart.

- **Target device**: Lenovo Xiaoxin Pad Pro 12.7 2025 (TB375FC), Android 15, 2944×1840 landscape.
  Nothing is device-specific; Android 8.0+ works.
- **Players**: built on the standard **Android MediaSession** APIs — **adapted for and tested with
  Poweramp**; any other conforming player (VLC, NetEase Cloud Music, QQ Music and so on) **should
  work as well**, and feedback is welcome.
- **Languages**: Chinese and English, switched with one row in the settings — the bar, the panel and
  the notification all follow.
- **Zero third-party dependencies**: plain framework APIs, no AndroidX, no Kotlin. About 105 KB.
- **License**: [MIT](LICENSE) — free to compile, modify and publish, **crediting SadEggs**.

### Download

**Latest stable**: https://github.com/SadEggs/musicwindow/releases/latest

| Version | What is in it |
| --- | --- |
| **v1.0.1** | Fixes the Chinese name still shown by the system in an English interface: the English app name is now **FloatingMusicBar**, and the language chosen inside the app is **handed to the system** (Android 13+, so the launcher label, the app-info page and the installer follow) |
| **v1.0** | **A shuffle/order engine of its own** (the app builds the queue, working around players whose folder queue is not recursive), **four panel switches** (shuffle/order, with/without sub-folders, repeat one, tap-carries-the-queue), the `X/XX` queue position, complete Chinese/English coverage, sub-folder song counts in the panel |
| v0.22 – v0.29 | Iterations on the engine and the panel switches — see the Releases page |
| v0.10 | Picking a song no longer turns shuffle off; the panel orders left to right, top to bottom again |
| v0.9 | Fixes "detects nothing after an update" (self-rebinding listener plus a re-check button) |
| v0.8 | Swiping previews the incoming song and only switches on release past the threshold |
| v0.7 | Play by library id (Poweramp), point-song while paused, media library panel |
| v0.5 | Pin (lock position) button, fixed signing key |
| v0.1 – v0.4 | The core bar, swipe to change track, hairline progress bar |

### Install and upgrade

1. Open the link above on the tablet and download `app-release.apk` (**not** `app-debug.apk`).
2. Open it with a file manager and allow "install unknown apps" when asked.
3. Or use adb: `adb install -r app-release.apk`

**Signing**: every build from v0.5 on is signed with the same **fixed key**, so versions install
straight over each other; only an upgrade from v0.4 or earlier needs a one-time uninstall. If an
install reports "app not installed / package appears to be invalid", check that the file size
matches the Releases page first (an interrupted download is the usual cause), re-download with the
system browser, or uninstall the old version first.

### First run: grant the permissions

The checklist is at the top of the app; **tap the text row** to jump to the matching system screen.

| Permission | Required? | Why |
| --- | --- | --- |
| Display over other apps | **Yes** | Without it the bar cannot be drawn at all |
| Notification access | **Yes** | Reads the player's media session (title / progress / controls). The app does **not** read notification content |
| Ignore battery optimisation | Recommended | Otherwise the system may kill the overlay during a game |
| Music and audio | For the panel | Scans music folders so the panel can browse and play them |
| Notifications | Optional | Only affects the ongoing notification's visibility |

> 1.0 shuffles by itself and **writes no m3u playlist files at all**, so it no longer needs
> "all files access"; the leftover row in the settings can be denied.

Then tap **Start**. **No bar on first start?** "Show only while playing" is on by default, so with
nothing playing the bar stays hidden — play something and it appears.

### Everyday use

- **Tap the title area**: play / pause. When the bar is dimmed the first tap only wakes it.
- **Swipe left / right inside the title outline**: next / previous. The incoming song's title slides
  in as a preview, and the switch happens only if you release past the threshold (40% by default).
- **Buttons** (previous / play-pause / next / pin / folder) fill the bar's height and are 48dp wide
  (54dp for play), with a light haptic tick, so they stay easy to hit on a thin bar.
- **Long-press, then drag**: move it anywhere, remembered. The **pin** button locks it in place.
- **Folder button**: unfold / fold the library panel (a ✕ appears beside it while open).
- **Drag the hairline**: seek; the dot is the current position.
- **Track-change tone**: Bluetooth only by default, so the tablet speaker stays silent.

### Shuffle and order: the app builds the queue (the heart of 1.0)

**Why it is needed.** How far a player's queue reaches is the player's decision, and Poweramp plays
only a folder's own songs — its sub-folders stay separate even in Poweramp's own UI — and it **does
not publish its queue** to anyone. No MediaSession call can therefore hand a player "this whole tree,
shuffled". Older builds tried writing the tree as an `.m3u` for Poweramp: that **does not work** —
it treats the playlist as a single track, reports a playback failure and skips it, and it never
imports a foreign m3u into its own playlists.

**What 1.0 does: the app is the engine.** When you tap a song, the app builds the queue itself (the
whole tree, sub-folders included) and hands the player one song at a time, watching the position to
start the next just before the current one ends. The price is a **gap of roughly 0.3–1 second
between songs** (a player cannot be asked to follow on seamlessly); what you get is a complete
shuffle range and a real queue position.

**Four switches in the panel header** (tap to flip, all remembered):

| Switch | On | Off |
| --- | --- | --- |
| **🔀 Shuffle / ➡ In order** | Shuffled by the app | The folder's own order, straight through, then from the top |
| **+ subfolders / this folder** | Range = this folder **and everything below it** | Only the songs **directly in** this folder |
| **🔂 Repeat one** | Tapping a song repeats that song until another mode is chosen | Normal playback |
| **tap = queue / tap = one song** | The tapped song becomes the **first song of the whole queue** | That one song alone |

- **"▶ 全部"** (top left of the panel) plays the whole folder range according to those switches.
- **Tapping a song**: repeat-one on → that song over and over; otherwise tap-carries-the-queue on →
  that song leads the queue (shuffled after it, or the folder's own order from there on); both off →
  that one song alone.
- The bar shows **`X/XX`**: which song of the queue is playing, and how many there are.

**Point-song ladder**: the app tries ① the player's own media browser, playing by library id
(Poweramp supports this) ② `playFromSearch` ③ playing the file directly, checking after each attempt
whether the track really changed. The player is **never** brought to the foreground, so your game is
never switched out, and a song tapped while paused is resumed automatically.

### Defaults (all adjustable in the settings)

| Setting | Default | Notes |
| --- | --- | --- |
| Shape | 3 cm tall bar | From the screen's physical DPI (273 ppi → about 322 px, ~17.5% of the height) |
| Length | 60% of the screen width | 20%–100% |
| Position | Bottom centre, 8 dp from the edge | Draggable anywhere, or docked to the top |
| Idle opacity | **35%** | Fades after 4 seconds, returns to 90% on touch |
| Visibility | Only with an active session | Hidden once playback has fully stopped |
| Cover art | Off | A 46dp thumbnail on the left when on |
| Library panel | 3 columns | A sub-folder row reads "name · N songs" (N includes its own sub-folders) |

### Known limitations

1. **The gap between songs** when the app builds the queue: roughly 0.3–1 s. That is the price of
   going around the player's own queue limits.
2. **The bar's rectangle consumes touches** — an overlay is one window, so the game cannot be tapped
   where the bar covers it. Collapse it, dock it over unused UI, or lower the opacity. Inherent to
   Android overlays.
3. **The player's own shuffle switch is left alone**: Poweramp's shuffle is a **cycle**
   (sequential → random songs → random categories → …) with no readable accessor
   (`随机接口=none` on the status page), so any attempt to "help" it would change the mode you set.
   The app shuffles by itself instead, independently of that setting.
4. **Some games hide all overlays** (`setHideOverlayWindows` on Android 12+, or anti-cheat), and
   nothing can be done about those.
5. **Marquee titles** are unreliable on unfocused windows; some ROMs simply truncate.
6. **The panel reads the system `MediaStore` index**, not the player's own library (which is only
   consulted for point-song). New folders may need a media rescan.
7. **Sizing depends on the DPI the system reports**; if 3 cm measures off, adjust the thickness.

### "Detects nothing" after an update?

Android **unbinds an app's notification access when that app is updated**, leaving the bar unable to
read anything. The app asks the system to rebind by itself and keeps retrying; if that is not enough,
work through, lightest first: **① the app's "Re-check player" button → ② turn notification access
off and on again → ③ reboot → ④ clear data** (which resets the settings and needs every permission
again). The first two keep all your settings.

### Building

Everything is built in GitHub Actions; no local JDK or Android SDK is needed. See
`.github/workflows/build.yml` (cmdline-tools plus `platforms;android-35` and `build-tools;35.0.0`,
Gradle 8.9 / AGP 8.7.3 / Java 17).

```powershell
git add -A
git commit -m "your change"
git push origin main          # triggers a build
git tag -a v1.0 -m "..."      # a tag also publishes a GitHub release with the APKs
git push origin v1.0
```

Pushing `main` builds the stable line, `beta` the experimental one; the tag names the release.

### Credits and attribution (please keep)

- **Original author / requirements**: **SadEggs** — https://github.com/SadEggs
- **Code written by**: **DeepSeek V4 Flash** — a human set the requirements, the model wrote the
  code, and GitHub Actions builds the APK in the cloud.
- **License**: [MIT](LICENSE)

**You may** freely compile, modify, extend, repackage and publish this app — for yourself, to share,
or on a store.

**The one requirement**: credit the original author **SadEggs** and keep this repository's copyright
notice and LICENSE:

> Original project: FloatingMusicBar by SadEggs — https://github.com/SadEggs/musicwindow

---

[MIT](LICENSE) © 2026 SadEggs. The Android app is original work with no third-party dependencies;
Gradle and AGP only affect the build, not the shipped code.
