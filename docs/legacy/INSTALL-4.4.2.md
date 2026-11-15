# 在 Android 4.4.2 车机上安装 CarLink Legacy

## 这是什么

CarLink 主 app（`com.carlink.app`）用 Compose 与 core-ktx 1.19，运行时下限是 Android 7.1（API 25），
在 Android 4.4.2 上装不上也跑不了。Legacy 是仓库里单独的一条产品线（`vendor/diplay-legacy`）：

| 项目 | 值 |
|---|---|
| 应用名 | CarLink Legacy |
| 包名 | `com.carlink.legacy.a`（release） |
| 版本 | 0.1.6（versionCode 9），设置里显示的 versionName 是 `0.1.6-legacy` |
| 系统要求 | Android 4.3+（minSdk 18；4.4.2 是 API 19） |
| CPU | armeabi-v7a（32 位 ARM） |
| 能力 | 无线 CarPlay（蓝牙引导 + 车机热点 / 本地直连），**不含** Android Auto |
| 产物 | `dist/legacy/CarLink-Legacy-0.1.6-ARMv7.apk` + 同名 `.sha256` |

它与主 app 包名不同，可以安装在同一台车机上互不影响。

## 1. 校验文件

```sh
# macOS / Linux
shasum -a 256 CarLink-Legacy-0.1.6-ARMv7.apk
# 应输出：7779e54733aa3396f0ccd483c009188b4209139b6390b2f1a2baf0df368b3b0b

# Windows
certutil -hashfile CarLink-Legacy-0.1.6-ARMv7.apk SHA256
```

不一致就不要装：多半是拷贝过程损坏或文件被替换。

## 2. 拷到 U 盘

FAT32 的 U 盘，APK 放根目录（车机文件管理器对中文/深层目录的兼容性差）。
插到车机的 USB 口，等它挂载完成。

## 3. 安装

1. 打开车机的**文件管理器**（GKUI 一般叫"文件管理"或"ES 文件浏览器"）。
2. 找到 `CarLink-Legacy-0.1.6-ARMv7.apk`，点它。
3. 如果提示"禁止安装未知来源应用"：去车机设置里允许当前文件管理器安装应用
   （GKUI 在 设置 → 安全 → 未知来源；不同固件路径不同），再回来点一次。
4. 安装完成后，应用列表里出现 **CarLink Legacy**。

安装过程不需要 root，不修改系统分区，也不影响原车蓝牙/语音/设置。

## 4. 首次使用

打开 CarLink Legacy，按首页弹出的三步引导：

1. 在 iPhone 上打开蓝牙，与**车机蓝牙**配对（不是在 iPhone 上直接连热点）。
2. 点「选择 iPhone」，选中刚配对的那台。
3. 点「打开车机热点」，热点起来后点「读取车机热点」保存；或者到 设置 → 连接 里把
   热点方式选成「本地直连」（Wi-Fi Direct，不接管手机流量）。

之后：

- 只想临时用：点「连接 CarPlay」。
- 想上车自动连：把首页三个开关都打开（打开 CarLink 时连接 / 自动开启车机热点 / 车机启动后打开）。
- 连上后 CarPlay 画面里按返回或设置，回到的是 CarLink 自己的页面，不是 DiPlay 的设置页。

## 5. 升级与降级

- **覆盖安装**：签名相同的包可以直接盖装，设置与配对信息保留。当前包用本机的 Android Debug
  证书签名（SHA-256 `7b50f412…074d5e`），后续升级包必须用同一把钥匙签，否则装不上。
- **换签名**：必须先卸载旧包，再装新包（设置会丢，重新走三步引导）。
- **版本号**：`versionCode` 不能降级；要装更旧的包先卸载。

## 6. 装完核对

设置 → 关于 里应显示：

```
CarLink Legacy
0.1.6-legacy
com.carlink.legacy.a
Android 4.4+
```

首页状态卡应有一行「自检：…」，与实际相符（就绪 / 自动连接已关闭 / 还没有保存车机热点信息 /
还没有选择 iPhone / 蓝牙未开启 / 车机热点未开启 / 认证信息不可用）。

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 解析包时出现问题 | 文件损坏、U 盘挂载不完整、存储空间不足 | 重新拷贝并核对 SHA-256；清出空间后重试 |
| 应用未安装 | 签名冲突（装过别的签名版本）、空间不足、固件限制 | 先卸载旧包再装；清空间；换一个文件管理器 |
| 装完应用列表没有图标 | launcher 缓存没刷新 | 重启车机 |
| 打开就闪退 | 固件/机型差异 | 设置 → 诊断 → 导出诊断报告（U 盘 `DiPlay-diagnostics/`），把 `CarLinkLegacy-report-*.txt` 带回 |
| 首页自检"车机热点未开启" | 热点没开 | 手动开热点，或打开「自动开启车机热点」 |
| 连接失败 | iPhone 没在车机蓝牙里配对 / 热点信息没保存 | 先配对、再选手机、再存热点；按 [实车清单](CAR-TEST.md) 逐步排查 |

## 8. 边界

- 包内含 CarPlay 认证资产（`assets/offline-mfi/`），只用于你自己的车机，不要外传或上传。
- Legacy 只覆盖 CarPlay；Android Auto、多手机切换、浮动切换器、应用内更新都在主 app（Android 7.1+）里。
- 4.4.2 上开热点可能让 Wi-Fi 客户端断开，这是原厂 AP/STA 互斥，不是应用缺陷。
- 本包没有在 4.4.2 真机上跑过（开发机是 arm64，没有 API19 镜像）。安装与连接请按
  [实车验证清单](CAR-TEST.md) 走一遍，失败就导出诊断报告。
