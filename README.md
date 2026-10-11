# TFRU Loading Screen

A declarative vector loading screen for the TerraFirma Rescue Unofficial (TFRU) modpack. The mod replaces the
vanilla / Forge splash screen with a scene written as a small JSON file, draws it with the Minecraft 1.7.10
fixed-function pipeline, and drives it from FML's mod-loading lifecycle. The creation of this mod have generative-AI involved.

一个为 TFRU 整合包制作的声明式矢量加载界面。本 mod 用一份小巧的 JSON 场景文件取代原版 / Forge 的开屏画面，
使用 Minecraft 1.7.10 的固定管线渲染，并由 FML 的 mod 加载生命周期驱动。本mod有生成式人工智能参与编写。

## Features 功能特性

- **Declarative scene language.** A scene is a JSON file of nodes (quads, triangles, polygons, text, images)
  plus keyframed tracks (position, rotation, scale, alpha, colour, points). A shared palette, a global camera
  and an optional final-frame logo keep the file small.
- **Two timelines.** *Actual frames* tell the story and advance only while the load grants "allowance"; *loop
  frames* (fire, steam, anvil, spark) run on the wall clock, so the picture stays alive even while a mod sits
  inside one long handler.
- **Pacing that waits for the load.** Progress grants story time; a starved story stops dead (never slow-motion)
  and resumes only once enough allowance has banked, then plays out the tail at the finish rate.
- **A coremod, not just a mod.** It rewrites `Minecraft.loadScreen()`, turns Forge's own splash off, and hooks
  FML's `ProgressManager$ProgressBar.step` for a main-thread heartbeat. The screen starts as early as the window
  exists, so the black window before the first frame is short.
- **A dedicated frame pump.** A second thread with its own GL context and its own font draws at up to 60 fps
  while the game thread is busy loading mods, and degrades gracefully when it cannot start
  (`-Dtfruloading.pump=false` turns it off).
- **Progress from the mod lifecycle.** One tick per mod per phase (construction, pre-initialization,
  initialization, post-initialization, load complete) - the same coarse-but-honest source BetterLoadingScreen uses.
- **Configurable HUD.** The title, the progress-bar text and colours, and the tips line live in their own file,
  so a pack can rebrand the screen without re-authoring the scene.
- **Tips line.** One tip per line, UTF-8, shown in a fixed-seed shuffle so a given moment always shows the same tip.
- **Ends on any screen.** The screen stops the moment the game is about to show a GUI (the main menu, a crash
  report, a mod's config screen) and hands the window back, so it can never paint over the game.
- **Pure-Java scene core.** `SceneCore` / `SceneIO` have no Minecraft imports, so the geometry and animation maths
  is the same one the offline web previewer runs and can be unit-tested off-line.

- **声明式场景语言。** 一个场景就是一份 JSON 文件，由节点（矩形、三角形、多边形、文字、图片）和关键帧轨道
  （位移、旋转、缩放、透明度、颜色、顶点）组成。共享调色板、全局相机和可选的片尾 logo 让文件保持精简。
- **双时间轴。** *实际帧* 讲述剧情，只有在加载提供“额度”时才推进；*循环帧*（火焰、蒸汽、铁砧、火花）按
  真实时间播放，因此即使某个 mod 卡在一个很长的处理流程里，画面依然活着。
- **会等待加载的节奏。** 进度提供剧情时间；额度不足时剧情直接停住（绝不慢放），累积到足够额度后才恢复，
  最后再以结束速率把片尾播完。
- **不只是 mod，还是一枚 coremod。** 它重写 `Minecraft.loadScreen()`，关闭 Forge 自带的开屏，并挂钩 FML 的
  `ProgressManager$ProgressBar.step` 以获得主线程心跳。窗口一存在就开始显示，首帧前的黑屏很短。
- **专用的帧泵线程。** 第二个线程拥有自己的 GL 上下文和字体，在游戏主线程忙于加载 mod 时以最高 60 fps 绘制，
  无法启动时会优雅降级（`-Dtfruloading.pump=false` 可将其关闭）。
- **由 mod 生命周期驱动进度。** 每个 mod 每个阶段 tick 一次（构造、预初始化、初始化、后初始化、加载完成）——
  与 BetterLoadingScreen 使用的是同一套虽粗糙但诚实的数据源。
- **可配置的 HUD。** 标题、进度条文本与配色、以及底部提示行都放在各自独立的文件里，整合包作者无需重做整个
  场景即可更换品牌信息。
- **提示行。** 每行一条提示，UTF-8 编码，按固定种子打乱，因此某一时刻永远显示同一条提示。
- **遇到任何界面即结束。** 当游戏即将显示某个界面（主菜单、崩溃报告、mod 配置界面）时，加载界面立即停止并交还
  窗口，绝不会覆盖到游戏画面之上。
- **纯 Java 场景核心。** `SceneCore` / `SceneIO` 不含任何 Minecraft 引用，因此其几何与动画数学与离线网页预览器
  完全一致，并可脱离游戏进行单元测试。

## Configuration 配置

Everything is read from `<gameDir>/config/loadingscreen/`, once, when the screen starts. Each file is written out
from the bundled default the first time it is missing, and an edit shows up on the next launch.

- `loading_screen.json` - the HUD: `labels` (top-left title), `progress` (bar text templates and colours) and
  `tips` (enable, file, colour, delay, rotation interval). Every field is optional; a broken or missing file simply
  falls back to the defaults.
- `tips.txt` - one tip per line; blank lines and lines starting with `#` are ignored. Keep a tip under ~77
  characters so it does not have to be trimmed on screen.
- `scenes/tfru_intro.json` - the scene that is played. Bundled scenes live under
  `assets/tfruloading/loading/scenes/` in the jar.

Forge's own splash is switched off for you through `<gameDir>/config/splash.properties` (`enabled=false`).

所有配置均在加载界面启动时从 `<gameDir>/config/loadingscreen/` 一次性读取。文件缺失时会从内置默认值写出，
修改后需在下次启动生效。

- `loading_screen.json` - HUD：`labels`（左上角标题）、`progress`（进度条文本模板与配色）以及 `tips`
  （开关、文件名、颜色、延迟、轮换间隔）。所有字段都是可选的；文件损坏或缺失时回退到默认值。
- `tips.txt` - 每行一条提示；空行与以 `#` 开头的行会被忽略。单条提示建议控制在约 77 个字符以内，避免在屏幕上被截断。
- `scenes/tfru_intro.json` - 被播放的场景。内置场景位于 jar 内的 `assets/tfruloading/loading/scenes/`。

Forge 自带的开屏会通过 `<gameDir>/config/splash.properties`（`enabled=false`）自动关闭。

## Build 构建

Requires JDK 8 and the Minecraft 1.7.10 / Forge toolchain (ForgeGradle 1.2).

```
./gradlew build
```

The jar lands in `build/libs`, alongside a `sources` jar and a `dev` jar. The coremod registers itself through the
jar manifest (`FMLCorePlugin` / `FMLCorePluginContainsFMLMod`), so the built jar is all you drop into `mods/`.

需要 JDK 8 以及 Minecraft 1.7.10 / Forge 工具链（ForgeGradle 1.2）。

```
./gradlew build
```

产物位于 `build/libs`，同时还会生成 `sources` 与 `dev` jar。coremod 通过 jar 清单
（`FMLCorePlugin` / `FMLCorePluginContainsFMLMod`）注册，构建出的 jar 直接放入 `mods/` 即可。

## Credits 致谢

The coremod loading-screen replacement follows the approach used by BetterLoadingScreen, and the `tips.txt` format
is ported from it as well. The dedicated pump font follows the `SplashFontRenderer` idea in Forge's `SplashProgress`.
Made for the TerraFirma Rescue Unofficial modpack.

coremod 替换加载界面的思路参考自 BetterLoadingScreen，`tips.txt` 格式亦移植自该 mod；帧泵专用字体沿用 Forge
`SplashProgress` 中 `SplashFontRenderer` 的做法。本 mod 为群峦救援非官方版（TFRU）整合包制作。

## License 协议

The mod's **source code** is licensed under the
[GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html); the full text is in
[LICENSE](LICENSE). Every Java source file carries a short AGPLv3 header.

The **bundled assets** (`src/main/resources/assets/tfruloading/` - the logo texture, the loading-screen HUD config, the
scene files and the tips) are licensed under
[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/);
the full text is in [LICENSE-ASSETS](LICENSE-ASSETS).

本 mod 的**源代码**遵循 [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html) 开源协议，
全文见 [LICENSE](LICENSE)；每个 Java 源文件均带有简短的 AGPLv3 声明头。

**内置资源文件**（`src/main/resources/assets/tfruloading/`，即 logo 贴图、加载界面 HUD 配置、场景文件与提示）
采用 [知识共享 署名-非商业性使用-相同方式共享 4.0 国际许可协议 (CC BY-NC-SA 4.0)](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh)
授权，全文见 [LICENSE-ASSETS](LICENSE-ASSETS)。
