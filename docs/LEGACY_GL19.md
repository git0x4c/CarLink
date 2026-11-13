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

### 当前能力

- 无线 CarPlay（蓝牙引导 + 车机热点 + iAP2/MFi + AirPlay），USB 有线兜底。
- 自动连接：开机/打开即连、热点未就绪时自动开启并等待、断线恢复时补开热点（见 `vendor/diplay-legacy/docs/geely-android43/GL19-AUTO-CONNECT.md`）。
- 视频硬解、音频（含 API18–20 软件 Opus）、触控回传、媒体按键、诊断报告导出。

### 尚未完成

- **界面与品牌**：现在复用的是 DiPlay 的 View 界面与 `com.shihab.diplay` 包名，还没有 CarLink 的品牌壳。
- **CarLink 自有能力**：多手机切换、Android Auto、浮动切换器、应用内更新都只在主 app（API 25+）里，Legacy 不含。
- **实车验证**：Legacy 包在 GL19 上的安装与连接需要上车确认；仓库这里只能证明构建与产物属性。

### 许可

`vendor/diplay-legacy` 为 DiPlay（GPL-3.0）的衍生副本，`vendor/jmdns` 为 Apache-2.0。CarLink 以 AGPL-3.0 分发，AGPL-3.0 §13 允许与 GPL-3.0 代码组合；分发 Legacy 包时需同时提供对应源码。
