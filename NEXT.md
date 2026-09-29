# FloatingMusicBar 下一步（交接文件）

## 当前状态（截至 v0.23）

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
