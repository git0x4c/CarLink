# Legacy（Android 4.4/5.x）构建

CarLink 主 app 的运行时下限是 **API 25（Android 7.1）**，这一条由实现方式决定，不是配置问题：

| 组成 | 现状 | 对 API 19 的影响 |
|---|---|---|
| `:app` | minSdk 25，Compose（51/101 个文件用 Compose） | Compose 需要 API 21+，Dalvik 上无法运行 |
| `:carlink-core` | minSdk 25，依赖 core-ktx 1.19、work、libsu | 这些依赖本身要求 API 23+ |
| `vendor/diplay`（引擎 0.2.22） | minSdk 24，`common` 里还有 Compose | 同上 |

所以在 GL19（2019 帝豪 GL / Android 4.4.2 / API 19）这类车机上，**CarLink 主 app 无法安装或运行**，降低 minSdk 也不会改变这一点。

## Legacy 模块

仓库内新增一个独立产品：`:legacy-mobile`，minSdk 18，纯原生 View，不依赖 AndroidX 的新 API，也不含 Compose。

```
vendor/diplay-legacy/
├── shared/     CarPlay/iAP2/MFi/AirPlay 引擎（API18 兼容层）
├── common/     原生 View 的连接/设置/诊断界面
├── mobile/     APK（minSdk 18，multidex，仅 ARMv7 发布）
└── jmdns/      旧系统可用的 mDNS 实现
```

来源：`git0x4c/DiPlay-Geely-Android43` 的 GL19 分支（DiPlay 0.2.10 + API18 适配 + GK19Play 实车修复 + 自动连接）。与 `vendor/diplay`（0.2.22）互不影响：两条产品线不共享 Compose/AndroidX 运行时。

验证包：`dist/legacy/CarLink-Legacy-0.1.5-ARMv7.apk`（versionCode 8，SHA-256 `86bdb008…748ab0e`，含认证资产）。

诊断报告写到 `DiPlay-diagnostics/` 目录（沿用引擎的清理与命名约定），文件名 `CarLinkLegacy-report-yyyyMMdd-HHmmss.txt`；没有可写外部存储时落到应用私有目录并在弹窗里说明。报告含：设备/系统、连接与自动开关、画面与音频偏好、MFi 状态、自动连接自检与最近动作、开机诊断、音频统计，以及脱敏后的 `diplay.log`。

### 实车验证

按 [GL19 实车验证清单](legacy/CAR-TEST.md) 执行：装包、首页自检、手动连接、自动连接、断线恢复、手动断开、失败取证，附判定表与已知限制。

### 构建

```sh
# 只验证安装（不含 CarPlay 认证身份）
./gradlew :legacy-mobile:assembleDebug -Pdiplay.gl19Diagnostics=true

# 可实际连接 iPhone（需显式提供合法认证资产）
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/assets \
  ./gradlew :legacy-mobile:assembleStandaloneRelease \
  -Pdiplay.gl19Diagnostics=true -Pdiplay.gl19AudioTest=aopus -Pdiplay.gl19Release=true
```

输出：`vendor/diplay-legacy/mobile/build/outputs/apk/{debug,release}/`。

### 界面（第一步）

`LegacyHomeActivity`（`vendor/diplay-legacy/common/.../legacy/`）是 Legacy 自己的首页，原生 View：

- 状态：CarPlay 是否连接、蓝牙、已配对 iPhone 数、车机热点状态、已选手机。
- 操作：连接 CarPlay（进引擎的 `CarPlayHostActivity` 投影面）、断开、选择 iPhone、读取车机热点、开启热点。
- 自动连接：打开时连接 / 自动开启车机热点 / 车机启动后打开，三个开关直接写引擎既有偏好。
- 首次使用会弹一次三步引导（配对 → 选手机 → 存热点）。
- 投影面里的返回与“设置”都回到 CarLink 自己的屏幕（`showDiPlayHome` 已改指向），只有 Wi-Fi Direct 恢复页仍用引擎流程。
- 按屏幕宽度做了一档缩放（≥900dp ×1.15、≥1200dp ×1.35），1024×600 与 1920×720 车机不会出现“字太小”。
- 自动连接流程由 `AutoConnectPolicy`（与主产品同一套判定）驱动：热点已开→连；正在开→每 2s 重读；关闭/未知且开了开关→请求开启；未选手机或未存热点→提示而不空转。冷启动时最多等 20s 让蓝牙就绪再连。
- 状态卡里有一行**内联自检**（蓝牙/热点读取在后台线程每 2 秒刷新，主线程只做绘制）（自检：就绪 / 自动连接已关闭 / 还没有保存车机热点信息 / 还没有选择 iPhone / 蓝牙未开启 / 车机热点未开启 / 认证信息不可用），每秒刷新；点“连接自检”按钮则显示完整报告与上次自动动作。
- 开机广播与"打开时连接"都指向这个首页；引擎自带的 `DiPlayActivity` 不再是启动入口（`exported=false`，无 LAUNCHER）。

应用身份：`com.carlink.legacy`（release 变体 `.a`），应用名 `CarLink Legacy`，当前版本 `0.1.1`。

**设置页（第二步）**：`LegacySettingsActivity`，原生 View，直接写引擎自己的偏好，下一次连接生效：

| 分组 | 项 |
|---|---|
| 画面 | 分辨率（原始 / 80% / 60%）、帧率（30 / 60）、HEVC |
| 音频 | 音乐缓冲（300 / 500 / 1000 ms）、吉利原厂音频通道（仅检测到 H52 能力时显示） |
| 诊断 | 导出诊断报告（U 盘优先）、连接自检 |
| 关于 | 应用名、版本、包名、适用系统 |

仍未做：CarLink 主 app 的其余屏幕（Home v2 视觉、手机切换、诊断导出、更新器）都还没移植。

### 当前能力

- 无线 CarPlay（蓝牙引导 + 车机热点 + iAP2/MFi + AirPlay），USB 有线兜底。
- 自动连接：开机/打开即连、热点未就绪时自动开启并等待、断线恢复时补开热点（见 `vendor/diplay-legacy/docs/geely-android43/GL19-AUTO-CONNECT.md`）。
- 视频硬解、音频（含 API18–20 软件 Opus）、触控回传、媒体按键、诊断报告导出。

### 尚未完成

- **其余界面**：首页之外仍是引擎的投影面；CarLink 的 Home v2 视觉、设置分组、诊断与切换器尚未移植。
- **CarLink 自有能力**：多手机切换、Android Auto、浮动切换器、应用内更新都只在主 app（API 25+）里，Legacy 不含。
- **实车验证**：Legacy 包在 GL19 上的安装与连接需要上车确认。目前已有的证据：构建成功、APK 属性（minSdk18 / 仅 armv7 / v1+v2 / 认证资产）、launcher 指向新首页、`legacy-common` 与 `legacy-mobile` 的 lint `NewApi=0`、Robolectric 冒烟测试（首页与设置页都能创建、渲染出预期文案、走完 pause/stop/destroy）、`legacy-shared`/`legacy-common` 全部单测通过。**仍然没有**在 4.4.2 真机或 Dalvik 模拟器上启动过：本机没有 API19 镜像，`avdmanager` 建 AVD 报 system image 的 `package.xml` 解析错误。

### 许可

`vendor/diplay-legacy` 为 DiPlay（GPL-3.0）的衍生副本，`vendor/jmdns` 为 Apache-2.0。CarLink 以 AGPL-3.0 分发，AGPL-3.0 §13 允许与 GPL-3.0 代码组合；分发 Legacy 包时需同时提供对应源码。

## 三步计划

1. **壳 + 能连（本次）**：CarLink Legacy 首页、应用身份、开机入口；复用已验证的引擎与自动连接。
2. **设置与诊断**：把 CarLink 的设置项（画面尺寸/帧率/音频缓冲/音频路由）和诊断导出搬到原生 View 设置页。
3. **体验收口**：首次连接引导、断开/重连提示、车机尺寸适配（1024×600/1920×720）、与主 app 共享的构建脚本与发布流程。

每一步都要在 GL19 实车上验证；模拟器只能证明界面能渲染，不能证明蓝牙/热点/CarPlay。
