# FloatingMusicBar 下一步（交接文件）

## 当前状态（截至 v0.24）

- **需求 1 已完成**（v0.24）：`MusicBarView` 新增 `shuffleView`，挂在 `statusRow` 里
  `timeView` 之后、`leftMargin = dp(14)`（故意离时钟远一点）；公开方法
  `setShuffleInfo(position, total)`，position<=0 时隐藏；`OverlayService.enginePlayCurrent()`
  里调用 `bar.setShuffleInfo(engineIndex + 1, engineQueue.size())`，`stopEngine()` 里清 0。
  文案 `R.string.shuffle_pos` =「随机 %1$d/%2$d」/「Shuffle %1$d/%2$d」。
- **v0.25**：修两个实测 bug（切歌留在树内、暂停/换文件夹后随机起不来）。
- **v0.26**：① 切歌改为在引擎列表里前后走（`stepEngine(int delta)`，只有走到末尾才重新洗牌）；
  ② 子文件夹行显示「名字 · N 首」（N = `songCountInTree`，文案 `R.string.folder_songs`），
  并**移除**了 `FolderPanelView` 里平铺子文件夹歌曲的那一段（`Prefs.showSubSongs` 因此失效）；
  ③ 主界面新增 `queuePosView`（`MusicBarView`），在 `statusRow` 之下、专辑封面之上，
  靠左、`leftMargin = dp(6)`，显示 `X/XX`（与状态栏同一个 `setShuffleInfo` 一起更新）。
- **v0.27**：面板头部新增「🔀 随机 / ➡ 顺序」开关（`Prefs.K_PANEL_SHUFFLE` / `panelShuffle` /
  `setPanelShuffle`；`FolderPanelView.modeButton` + `refreshMode()`）。
  随机模式：`onPlayFolder` → `startEngine(folder, null, true)`；`onPlaySong` → **即使整树随机已在跑
  也重开随机**（`startEngine(song.folder, song, true)`）—— 这是修掉"引擎在跑时点歌只播一首"的关键。
  顺序模式：`▶ 全部` → `startEngine(folder, null, false)`（`engineShuffle=false`，不洗牌，走完从头再来）；
  点歌 → `stopEngine()` + `playFromLibrary(song)`（只播这一首）。
  ⚠️ `startEngine` 现在有第三个参数 `boolean shuffle`，所有调用点都必须带上。
  设置里「显示子文件夹歌曲」开关已删除。
- **坑（新）**：**不要用 PowerShell 的 `Set-Content -Encoding UTF8` 改仓库里的文本文件** ——
  Windows PowerShell 会写入 UTF-8 BOM，Gradle 解析 `build.gradle` 直接失败（只报 "1 error"，看不出原因）。
  静态检查已加"任何 .gradle/.xml/.java 都不许有 BOM"。另外：给 GitHub API 传长文本时，
  **不要内联进 shell 的 node -e**（双引号会被截断），写成临时文件再读。
- **待定/未做**：顺序队列（非随机）的位置无法显示 —— Poweramp 不公开自己的播放队列；
  设置里「显示子文件夹歌曲」开关已失效，待清理。

## v0.24 实测反馈的两个 bug（已在 v0.25 修复）

1. **在子文件夹里"切歌"不会自动开始随机该子文件夹**
   - 现象：在子文件夹里按悬浮条的上一首/下一首（切歌）时，只是播放器自己在自己的队列里跳，
     **不会启动本 App 的引擎**，所以随机范围还是 Poweramp 的（常常落回大文件夹本层）。
   - 方向：悬浮条 `Callback` 里的 next/prev（`onNext` / `onPrev`，走
     `MediaBridge` 的 `skipToNext` / `skipToPrevious` 那两条）在 `Prefs.treePlay` 打开时，
     应先取"当前正在播放歌曲所在的文件夹"，再调用 `startEngine(folder, null)`，
     即切歌 = 以当前歌曲所在文件夹为根重新开始随机。注意 `engineOn` 会挡住递归，
     必要时在切歌入口先 `stopEngine()` 再 `startEngine(...)`。
   - 需要确认的语义：按"上一首"时是从该文件夹重新随机（推荐），还是要按随机列表往回走。

2. **暂停状态下、或从子文件夹返回上级大文件夹后按「▶ 全部」→ 播放器无响应，随机起不来**
   - 现象：播放器毫无反应，整树随机没开始。
   - 怀疑点（按可能性排序）：
     a. `playFromLibrary` 里 `Prefs.folderPlay(this)` 那段会先 `browser.keepSubscribed(
        browser.lastParent())` 再 `handler.postDelayed(...)` 点歌，且**用 token 校验**；
        面板返回上级/切换文件夹期间 token 很可能已经变了 → 整个播放请求被丢弃 → 表现就是"没响应"。
        引擎路径不需要"订阅文件夹队列"，应绕开这一段（例如给 `playFromLibrary` 加一个
        `boolean plain` 参数，或在引擎里直接走 `startPlayAttempts`）。
     b. 暂停状态下部分播放器不接受 `playFromMediaId`，需要先 `transport.play()`
        （或点歌后再补一次 play）。
     c. `PlayerBrowser.findMediaId(..., budgetMs)` 超时后再走 URI，整体拖到 2.5s+ 才动，
        用户观感也是"没响应"。
   - 复现要点：① 暂停 → 按「▶ 全部」 ② 进子文件夹 → 用面板返回上级 → 按「▶ 全部」。

- 仓库 `SadEggs/musicwindow`，分支 `main`（稳定）/ `beta`（同步），标签 v0.23 已发布为 **pre-release**。
- v0.23 里「整树随机」**已可用**（用户 2025 实测确认："现在是整个树开始了"）。
- 引擎实现在 `app/src/main/java/com/musicbar/overlay/OverlayService.java`：
  - `startEngine(folder, first)`：取 `MediaLibrary.songsInTree(folder)`，点了的那首排第一，其余 `Collections.shuffle`。
  - `engineTick`（400ms）→ `engineStep()`：曲末前 `ENGINE_LEAD_MS`(1500ms) 或播放器自己切歌 → 推进下一首；
    一轮走完重新洗牌。`engineOn` 同时充当防递归开关。
  - 入口：面板「▶ 全部」→ `startEngine(folder, null)`；点歌 → `playFromLibrary` 里的
    `if (!engineOn && Prefs.treePlay(this) && treeIsBigger(song.folder)) startEngine(song.folder, song);`
  - 开关：`Prefs.treePlay`（`K_TREE_PLAY`，默认 **true**）。
- m3u 那条路**已经废弃**：正常使用不再生成任何 m3u；只有设置页「检查播放器能否识别播放列表」
  诊断按钮会创建它（结论：Poweramp 不接受外部播放列表）。
- 发布流程（无本地 Android 工具链）：
  静态检查 → `git -c credential.helper= -c http.sslBackend=openssl push <token-url>` → 等两个
  Actions 运行 → `.gh_dl.mjs` / `.gh_apkver.mjs` / `.gh_cert.mjs` / `.gh_apkstr.mjs` /
  `.gh_release_meta.mjs <tag>` → PATCH `prerelease:true` → 归档 `_apk\<tag>\` → 同步 `beta`。
- 签名证书 `d6c4b20644d3cdfb265f44860af5f64b06266916eb258fff10387bd0947b949e`（v0.5 起未变）。
- 版本号：`app/build.gradle` 的 `versionCode 24` / `versionName '0.23'`。

## 用户新提的三条需求（v0.24）

1. **状态栏显示「随机 第 N/M 首」**
   - 位置：**靠左侧，但要离左边的时钟远一点** → `MusicBarView` 的 `statusRow` 里，
     排在 `timeView` 之后（时钟 9sp 在最左，计数紧随其后，再是 `spacer` weight=1，最右是电量）。
   - 数据源：引擎的 `engineIndex` / `engineQueue.size()` 要能被 `MusicBarView` 读到
     （建议：`MusicBarView` 加静态 `setShuffleIndex(int index, int total)`，引擎每次
     `enginePlayCurrent()` 时调用；不在引擎中时传 (0,0) 隐藏该标签）。
   - 注意：视图目前每 ~1s 刷新状态条，计数只在切歌时变化，别每 tick 都重建。

2. **点歌 = 播这首歌 + 重开整树随机**（用户原话："点击大文件夹/小文件夹某一首歌开始播放这首歌，
   然后重新开始随机大文件夹内所有歌曲（包括子文件夹），若是小文件夹，则是开始随机小文件夹所有
   歌曲。切歌的第一首就是随机的第一首"）
   - **现版本已经是这个行为**，需用户确认；若不符合，要确认"随机范围"的根：
     当前取的是**被点歌曲所在文件夹**（`song.folder`），即在大文件夹点 → 整棵大树，
     在子文件夹点 → 那个子文件夹的树。
   - "切歌的第一首就是随机的第一首"：即点了的歌先播完，接着是洗牌后的第 2 首（当前如此）。

3. **子文件夹显示歌曲数量，但不列出歌曲**
   - 面板进入一个文件夹时，子文件夹行后面显示「(N 首)」。
   - 不要在最下方再把这些子文件夹的歌曲平铺列出来。
   - 需要读 `MusicBarView` 里构建列表的代码（`folderRow` / `showFolder` 之类），
     计数用 `MediaLibrary.songCountInTree(folder)` / `songCountIn(folder)`。

## 已知坑（这个项目踩过的）

- 中文 Java 源文件必须**纯 ASCII**（中文用 `\uXXXX` 转义），`strings.xml` 里**撇号要写 `\'`**，
  否则 aapt2 报 `Invalid unicode escape sequence`。
- 用编辑工具时**不要把行尾换行吃掉**（会造成两行粘连、编译失败）——改完跑一次
  "粘连行"检查：`;\s{4,}\S`、`\*/\s{2,}\S`、`\)\s{4,}(private|public|void|int|boolean|String)`。
- 静态检查要排除 `android.R.string.*`（系统资源），否则误报"缺字符串"。
- 编辑工具常报 "file changed since it was read"：重新读一小段再改即可。
- 每次发布都要让用户实测回报（Agent 无设备）。

## 收尾事项

- 会话结束前删除 `D:\AI Program\Workspace\.gh_token`，并让用户到 GitHub 撤销该 PAT。
