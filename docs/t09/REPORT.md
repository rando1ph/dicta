# T09 · Popup 跨阅读器兼容性研发与真机验证

2026-11-18。T09 修复、定向回归、Readest/Chrome 真机验证与私有截图交付已完成；代码提交 `6127c1f`。视觉和剩余兼容性由用户人工审阅，T10 人工审阅任务及 T11 发行均未启动。

## 目标和边界

保持现有 Popup 视觉、查词、历史、收藏、完整词条及取消返回语义，修复标准 `ACTION_PROCESS_TEXT` 调用在不同任务启动方式下的行为。开发、提交和推送均限 `main`；不创建分支或 worktree，不 rebase 或 force push。本轮不进行正式版本签名、不创建发行 Tag、不发布 GitHub Release，也不覆盖正式安装包。

唯一设备为 ADB 指定的 OnePlus 6T：`ONEPLUS A6013`，`ro.product.device=OnePlus6T`，LineageOS 22.2 / Android 15 / API 35，设备别名为 `fajita`。所有设备操作应显式指定序列号 `e5ee4f28`，并先确认它是唯一 ADB 设备；不操作 OnePlus Ace 6T。本轮结果不能代表 ColorOS 17 或其它设备的兼容性。

## 入场核实与任务记载的差异

| 项目 | 本机实测基线 | 对验收的影响 |
| --- | --- | --- |
| 正式 Dicta | `dev.randolf.dicta`，实际安装版本 **1.0.0 / code 5**，并非任务文字中的 1.1.0 | 保留现有正式 APK 和数据，不能以仓库版本代替设备版本 |
| Readest | `com.bilingify.readest`，0.12.20 / code 12020 | 查词调用源码与该版本对照 |
| Readest 藏书 | *The Martian*，进度约 9%；*Project Hail Mary*，进度约 1%；**未发现原指定的 Artemis** | 本轮用户已明确选择以现有 *Project Hail Mary* 替代；从既有约 1% 位置测试，不翻页，不将替代作品标记为 Artemis 验收 |
| Lookup App | 入场实际选择为 **Default**，并非任务文字中的正式 Dicta | 测试结束已按用户要求恢复为正式 `Dicta`，截图读回确认；明确保留入场与结束选项差异 |

正式 Dicta 基线 APK SHA-256 为
`557c8c3c055ad53cf4d6bdb29f450564f31bc4b3ea000163f240000c7897c1f7`；
Readest 基线 APK SHA-256 为
`0f8278610ba9fc5e9852b5f5a382a6f928e0c1607e957d8c34a8447f1be6ed1a`。
版本、安装时间与哈希记录保存在本地
`artifacts/t09/baseline/protected-packages.json`。结束核验确认两个 APK 的哈希、版本和安装元数据与入场一致；两本书及原有进度仍在，阅读位置与选项恢复结果见下文。未 Root、读取或逐字节比较应用私有数据，不把 APK 一致性表述为全部用户数据的字节证明。

## 问题复现与修复机制

Readest 0.12.20 的 Android 原生桥创建 `ACTION_PROCESS_TEXT` / `text/plain` Intent，并添加 `FLAG_ACTIVITY_NEW_TASK`；指定 Lookup App 时可显式选择接收 Activity。[Readest 对应源码](https://github.com/readest/readest/blob/28aab23e039a23511bc478c23bf5278daa43cc68/apps/readest-app/src-tauri/plugins/tauri-plugin-native-bridge/android/src/main/java/NativeBridgePlugin.kt#L1940-L2000) 是调用路径依据。Android 的任务 affinity 规则与实现另见 [Tasks and back stack](https://developer.android.com/guide/components/activities/tasks-and-back-stack#Affinity) 和 [Android 15 ActivityStarter](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/wm/ActivityStarter.java)。

原实现使用透明 `ProcessTextActivity` 承载现有 Compose Dialog，但与主界面共享默认 affinity。设备上的定向反例证明：当 Dicta 主界面已存在，`NEW_TASK` Popup 被放入相同任务，断言失败；修复前截图也显示 Popup 背后是 Dicta 主界面，而非外部阅读器。该反例为受控调用，修复后的 Readest 和 Chrome 另行执行实际选词验证，二者证据分别记录。

只设置空 affinity 的中间方案通过 10 项中的 8 项，仍有两项失败：后台 Popup 再次收到仅带 `NEW_TASK` 的新查询，以及后台完整词条之后再次查词。由这些反例结合 AOSP 实现可推断，空 affinity 不等于每次必建新实例；匹配 receiver 根组件的任务仍可能被复用，标准启动模式也不保证新选择进入已有接收实例。把完整词条放到另一个 `MainActivity` 还会改变该外部会话顶部的接收组件。

最终代码采用以下组合，已通过下述回归和真实宿主验证：

- `ProcessTextActivity` 使用空 `taskAffinity`，隔离 `NEW_TASK` 外部会话与 Dicta 主界面任务；普通不带 `NEW_TASK` 的调用仍加入调用方任务。
- 使用 `singleTop`，使复用到顶部接收实例的调用进入已有 `onNewIntent`；更新选词、只读标志和 Intent，取消旧请求并显示最新查询。
- Popup 的「打开」在**同一个 receiver** 内切换到现有 `MainScreen` 完整词条，保留同一 ViewModel 和快照，不再为该跳转创建另一个 `MainActivity`。新的外部查询会切回 Popup；重建保存完整词条状态；返回结束外部会话。
- 保留原透明主题、Compose Popup 和业务组件，不新增悬浮窗权限或调用协议。关闭继续返回 `RESULT_CANCELED`，不向可编辑来源写入替换文本。异常 Extra、空输入及不支持的 action/MIME 继续安全处理。

独立 Debug 使用 `dev.randolf.dicta.debug`、版本后缀 `-debug` 和 `Dicta Debug` 标签，启动器及 `PROCESS_TEXT` 目标均可区分。Release 的 `dev.randolf.dicta`、版本默认值和签名配置没有随隔离配置改变；本轮仅构建和安装 Debug 与其独立测试包。

## 自动化结果及复现

| 检查 | 当前已观察结果 | 证据与限制 |
| --- | --- | --- |
| 修复前定向反例 | 1 项执行、1 项失败；Popup 与已有主界面 task ID 相同 | 本地 `artifacts/t09/baseline/instrumentation.txt`；用于确认测试能检出问题 |
| 仅空 affinity 中间方案 | 10 项执行，8 项通过、2 项失败 | 本地 `artifacts/t09/instrumentation-affinity-only.txt`；不是最终修复结果 |
| JVM 单元测试 | **81 项通过，0 failures** | 最终构建流程确认；不重复扩展为无关全量真机测试 |
| Debug lint | **0 errors、13 warnings** | 最终检查通过，保留 13 项警告披露 |
| 最终 Debug / AndroidTest 构建及真机回归 | **13/13 通过**，89.366 秒 | 本地 `artifacts/t09/verification-final.txt`；11 项 T09 加 2 项 T07 Popup 定向回归 |
| 真实 UI Mock review 会话 | **1/1 通过**，701.864 秒，6 次设备内 TLS Mock 请求 | 本地 `artifacts/t09/review-session.txt`；实际宿主操作见下文 |

复现命令为 `bash tools/t09/test.sh e5ee4f28`。运行器检查唯一授权手机与 `main`，依次执行 Debug / AndroidTest 构建、JVM 和 lint，核实两个 APK 的隔离包名后才安装，并要求 instrumentation 输出非零测试数量的 JUnit `OK` 摘要，不能只依据 shell 返回码。详见 [T09 运行说明](../../tools/t09/README.md)。

11 项 T09 契约覆盖隐式目标解析、普通调用与取消结果、已有主界面的 `NEW_TASK`、连续 `singleTop`、后台仅 `NEW_TASK` 的新词、后台完整词条后的新查询、重建及覆盖恢复、完整词条共用快照、异常 Extra 和非法 action/MIME，并实际切换测试 Activity 的横屏/竖屏验证快照与状态恢复。两项 T07 回归验证 Popup 内容、完整词条、重建、关闭和不替换来源文本。方向变更限测试 Activity，并恢复竖屏。

测试使用设备端 TLS Mock Provider，并阻止外部 DNS，不需要生产 API Key 或付费模型调用。实际阅读器/Chrome 路径由真实选词进入，未以显式 ADB Intent 代替。截图中的词条将所选词替换到默认 ephemeral 样例，音标、释义和例句均为 fixture 文本，只证明实际 UI、任务、返回和快照链路，不作为这些词的真实释义或模型质量证明。

## 实际宿主验收矩阵

| 场景 | 实测结果 | 证据与边界 |
| --- | --- | --- |
| Readest 指定 Dicta Debug | **通过**：用户授权的《Project Hail Mary》，Chapter 1，第 9/761 页，真实查询 `pronunciation`，保留书页背景 | 从既有约 1% 位置测试，全程未翻页；截图 06–08 |
| Readest Default 路径 | **通过**：真实选择 `feminine` → 系统选择 Dicta Debug → Popup → Back 返回原书页 | 进入的是系统 chooser，未改为永久默认目标；截图 04–05 |
| 收藏和完整词条 | **通过**：`pronunciation` 收藏后打开完整词条，仍为同一 `ProcessTextActivity`，累计请求保持 2 次 | 复用同一快照，未因打开而追加请求；会话内 Vocabulary 显示 `pronunciation` |
| 后台完整词条再查询 | **通过**：切回 Readest 留下完整词条，再真实查询 `irritated`，进入新词 Popup | 修复中间方案未覆盖好的实际重入路径；截图 09 |
| 后台 Popup 再查询 | **通过**：再次切回 Readest 留下 Popup，真实查询 `computer`，显示新词 Popup | 无需先关闭旧外部会话；书页与进度不变 |
| Chrome 实际选词及详情返回 | **通过**：example.org 公开页长按 `documentation` → 更多 → Dicta Debug → Popup → 完整词条 → Back | 网页背景可见；详情打开前后累计请求均为 5 次；返回同 URL 和位置；截图 11–13 |
| Chrome 第二词及关闭 | **通过**：真实选择 `monitoring` → Popup → X 关闭 | 返回原网页；6 次 Mock 请求对应六个实际选词 |
| 已存在 Dicta 主界面 | **通过**：Debug 主界面在上述真实宿主测试期间一直存在 | Readest 外部 Popup 独立任务；Chrome 普通调用仍加入 Chrome 任务 |
| History / Vocabulary UI | **通过**：会话内 History 显示 `monitoring`、`documentation`、`computer`、`irritated`；Vocabulary 显示 `pronunciation` | 测试数据属于隔离 Debug Mock 会话，不写入正式包 |
| 正式包及两本书保留 | **通过结束核验**：正式 Dicta 与 Readest APK 哈希、版本、安装元数据均与入场相同；仍有两本书 | 《Project Hail Mary》1% /《The Martian》9%；最近阅读排序正常变化；页码和正文位置保留；截图 10、15 |
| Lookup App 恢复 | **已恢复正式 Dicta**，截图读回确认 | 遵照用户要求；入场值实际为 Default；截图 14 |

任务诊断与真实 UI 相符：Readest 本体任务为 712，Default / 指定调用的 Popup 任务分别为 827 / 828，独立于已有 Debug 主界面任务 825；Readest 指定 Intent flags 为 `0x10800000`，含 `NEW_TASK`。Chrome Popup 留在 Chrome 任务 732，flags 为 `0x800000`，不含 `NEW_TASK`。这些 task ID 是本次设备会话观察值，不是固定实现配置。

## 恢复和清理

Mock review 会话优雅结束并输出 `OK (1 test)`。Debug 的 `provider.enc`、review ready/stop 文件均确认不存在；测试 APK `dev.randolf.dicta.debug.test` 已卸载，隔离 Debug App `dev.randolf.dicta.debug` 保留，处于未配置 Provider 状态，并在清理后 force-stop。正式 Dicta 和 Readest 未卸载、未清数据，书籍、笔记和书签未改写。

人工 UI 测试期间临时将屏幕超时从 60000 毫秒调至 600000 毫秒，结束已恢复为 60000 毫秒。最终设备读回为 `mScreenState=OFF`、`mWakefulness=Dozing`，keyguard 显示，屏幕已关闭。Chrome 本轮使用的 example.org 公开页标签保留，未删除其它标签。

## 构建产物、截图、提交及交接

- 最终 Debug APK：`artifacts/t09/final/Dicta-T09-debug.apk`，`dev.randolf.dicta.debug`，`1.1.0-debug` / code 6，12,492,994 字节，SHA-256 `89ee740a81430d25563b995827eb8c49484c787551d9e19586dc0e446bf73076`。Release 包名和版本默认值保持原样。
- 测试 APK 本地构建位置：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`；13 项定向测试通过，设备端测试 APK 已移除。
- **15 张原始 PNG** 已上传 [Google Drive / ChatGPT / Dicta](https://drive.google.com/drive/folders/1z5SKVJyuqLFFs87nC2URwMU6mQ82pPKl)，均为私有审阅。逐张核对父目录、MIME、字节数和 SHA-256，并经认证下载与本地原图逐字节比较通过；未把截图作为公开营销图或嵌入公开 Git。
- 仓库证据：[截图索引](evidence/INDEX.md) · [截图清单与 SHA-256](evidence/screenshots.json)。Drive 同步文件：[索引](https://drive.google.com/file/d/1RDDaj7S3gNC83kUxnHWJAbzJ3iXqln_2/view?usp=drivesdk) · [机器可读清单](https://drive.google.com/file/d/19JhLeACrEaNsyswdQqQkZxd3f3Zr8FPV/view?usp=drivesdk)。
- `main` 代码提交：[6127c1fcdd25119b1b6aa5142b7a3494b69e4862](https://github.com/rando1ph/dicta/commit/6127c1fcdd25119b1b6aa5142b7a3494b69e4862)。本报告与脱敏证据归档在随后的 main 文档提交；最终远端 HEAD 读回记录在 Notion T09 交付更新。
- T09 工程与证据交付完成后等待用户人工审阅；未启动 T10/T11，没有正式签名、发行 Tag 或 GitHub Release。

## 剩余兼容性限制

当前范围是标准 `ACTION_PROCESS_TEXT` / `text/plain` 及其合理任务调用形态，不承诺任意第三方私有协议、`ACTION_SEND` 或自定义 URI 的兼容。主力手机的 ColorOS 17 没有测试；未授权设备、其它 Android/API 版本和其它阅读器没有覆盖。原指定的《Artemis》因书库不存在而未覆盖，本轮真实 Readest 验收使用用户明确授权的《Project Hail Mary》。Mock Provider 不代表真实词条质量或联网 Provider 稳定性；完整词条系统栏等视觉细节和其它 OEM 行为仍交用户人工审阅。

`artifacts/t09` 下的设备原始材料保存在本机且不随 Git 提交；公开或云端证据仅使用逐张检查过的脱敏截图、摘要和索引，不上传密钥、账号、通知或不相关个人内容。
