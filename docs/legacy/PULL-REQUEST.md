# PR：CarLink Legacy（Android 4.4/5.x）第一步

分支：`spike/legacy-api19`（基于 `main` 的浅克隆，9 个提交，613 文件 / ~79.8k 行，其中绝大多数是 vendor 进来的引擎与测试）。

## 为什么不是改主 app

CarLink 主 app 的运行时下限由实现方式决定，不是配置：

| 组成 | 事实 |
|---|---|
| `:app` | minSdk 25，101 个 Kotlin 文件里 51 个用 Compose |
| `:carlink-core` | minSdk 25，core-ktx 1.19 / media3 / work / libsu 都要求 API 23+ |
| `vendor/diplay` | 0.2.22，shared minSdk 24，common 里还有 Compose |

GL19（2019 帝豪 GL / Android 4.4.2 / API 19）装不上，也跑不了 Compose。所以 Legacy 是一个**独立产品模块**，与主 app 不共享 UI 运行时。

## 改了什么

```
vendor/diplay-legacy/{shared,common,mobile,jmdns}   来自 DiPlay GL19 fork（GPL-3.0）
  common/.../legacy/LegacyHomeActivity.kt            首页：状态、自检、连接/断开/选手机/热点/自动开关
  common/.../legacy/LegacySettingsActivity.kt        设置：画面、音频、诊断、关于
  common/.../legacy/LegacyDiagnosticExport.kt        诊断报告（U 盘优先）
  common/.../legacy/AutoConnectPause.kt              手动断开暂停 / 停留首页有限重试
  common/.../AutoConnectPolicy|Diagnostics|SelfCheck 自动连接的判定与记录（与 DiPlay fork 同一套）
settings.gradle.kts                                  :legacy-shared / :legacy-common / :legacy-mobile / :legacy-jmdns
docs/LEGACY_GL19.md                                  构建、结构、三步计划、边界
docs/legacy/CAR-TEST.md                              实车验证清单与判定表
scripts/legacy.sh                                    build / release / test 三个入口
```

引擎侧只做了一处转发改动（`CarPlayHostActivity.showDiPlayHome`）：返回与"设置"回到 CarLink 的屏幕，只有 Wi-Fi Direct 恢复页仍用引擎流程。

## 构建

```sh
scripts/legacy.sh test      # 单测（shared + common）
scripts/legacy.sh debug     # 无认证的调试包
DIPLAY_AUTH_ASSETS_DIR=/path/to/assets scripts/legacy.sh release   # 可连接 iPhone
```

认证资产（`offline-mfi/identity.pk8`、`certificate.p7b`）不提交，构建时不注入就只有"工具包"。

## 验证证据

- `:legacy-shared` + `:legacy-common` 全量单测通过；新增首页/设置页 Robolectric 冒烟、诊断导出、暂停重试、策略测试。
- lint `NewApi = 0`（`legacy-common`、`legacy-mobile`，minSdk 18）。
- 产物 `com.carlink.legacy.a` / `CarLink Legacy` / `0.1.5`：minSdk 18、仅 `armeabi-v7a`、`v1+v2` 签名、认证资产在包内、launcher 指向 Legacy 首页。
- **没有**在 4.4.2 真机或 Dalvik 模拟器上启动过（本机无 API19 镜像，`avdmanager` 建 AVD 失败）。这是本 PR 唯一实质缺口。

## 风险

- `vendor/diplay-legacy` 与 `vendor/diplay` 是同一引擎的两个版本（0.2.10+API18 改造 vs 0.2.22），两份并存但只在各自的 APK 里，不会同时进一个 classpath。
- Legacy 不跟随 `main` 的引擎更新；同步需要手工移植（与 fork 维护成本一致）。
- 与仓库"一个 app、不暴露引擎界面"的规则仍有张力：Legacy 复用了引擎的投影面（`CarPlayHostActivity`），其余引擎界面已不可达。
- AGPL-3.0 + GPL-3.0 组合分发时需同时提供源码。

## 回滚

- 主 app 完全未改：删掉 `vendor/diplay-legacy/` 与 `settings.gradle.kts` 里四行 include 即回到 `main` 行为。
- 应用侧：关掉"自动开启车机热点"回到手动热点；关掉"打开 CarLink 时连接"则完全手动。

## 后续

1. 实车验证（按 `docs/legacy/CAR-TEST.md`），拿回 `CarLinkLegacy-report-*.txt`。
2. 按报告修：认证、固件热点权限、配对时序或音频路由。
3. 长期：Legacy 的 UI 逐步向 CarLink 的 Home v2 靠拢；引擎更新按 fork 的方式同步。
