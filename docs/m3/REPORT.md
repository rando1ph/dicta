# Dicta M3：历史、生词本、每日词与可编辑 Profile

执行日期：2026-11-10（Asia/Shanghai）。唯一任务 T04：`4135421e690b819eb515f713ebffb928`。基线 `27b57eeb6ce5aeb3e66a27f58cf131cbe41e0be1`；全程 main，无分支/worktree/rebase/force push。沿用已认可的 Compose 五屏风格和原始默认 Profile，Popup 保留「打开」。

## 交付内容

- **历史与收藏**：Room 数据库存储不可变词条快照、独立历史事件、独立收藏及每日词引用。Home/Popup 成功查询先在事务内保存再展示成功；列表连续倒序，支持本地搜索、管理、多选、批量收藏/删除或移出、当前筛选全选、删除确认和空状态。筛选变化清空选择；Home 可确认清空历史。删除历史仅删事件，取消收藏仅删收藏，不误删快照。
- **本地再次查看**：历史、收藏和 Popup「打开」均使用 entryId 读取保存的 Schema/Template/JSON，不重新请求模型或追加历史。相同归一化查询、配置内容、设备端点和有效模型复用已验证快照，并新增本次查询事件；连接测试明确绕过缓存。
- **Popup 收藏**：默认未收藏为当前主题 primary 的描边按钮，已收藏为 primary 填充及「✓ 已收藏」；Home、详情和生词本共享持久状态。Accent 与主题偏好也在设备保留。发音仍留 M4。
- **每日单词**：复用现有 DeepSeek BYOK 和同一 Prompt/Schema/渲染器，使用 `mode=daily`、空 query、近期词 exclude；没有额外单词 API。按本地日期、IANA 时区、Profile 内容哈希、端点和有效模型形成缓存槽。首页首次进入/回前台时检查；切日或配置变化后首次访问生成，无后台定时付费请求。换一换只发一个请求，busy 防止并发；排除当前词及最近20次成功选词，忽略大小写及多余空白。重复、错误、超时不重试，保留旧卡供手动重试。最近词列表和新卡在同一事务更新；重命名 Profile 不改变缓存身份。
- **Profile 编辑器**：只读预置默认；新建、复制、切换、编辑名称、Prompt、JSON Schema、Popup Template、Example 及可选模型覆盖。字段、顺序和预览来自配置，无特定 JSON 词条字段硬编码。300ms 去抖本地预览不调用 API；语法、受控 Draft-07、声明式模板、绑定、样例和大小均校验，保存时再次校验并原子激活不可变 revision，错误不覆盖当前有效版本。编辑失败保留草稿。
- **导入导出与安全恢复**：Android 系统文件选择器或 JSON 文本导入，输出 versioned JSON 包，限256KiB。包只含 Prompt/Schema/Template/Example/名称/模型，不含设备端点、密钥或凭据关联；严格拒绝未知版本/未知字段。始终使用设备现有 BYOK 端点和密钥，不将旧密钥自动发送到导入端点。恢复默认只重新选择预置方案，保留用户副本与旧记录。
- **版本兼容**：Room schema v2 已导出，显式1→2迁移为旧快照补 rendererVersion=1；无 destructiveMigration。未知数据库版本拒绝打开并保留数据。每条词条保存自己的 Schema/Template/Profile revision；展示版本不兼容或数据损坏时保留原 JSON，提供有提示的受限纯文本查看。损坏的激活 Profile 回退到预置默认，不删除用户配置。

快照不会因取消收藏/删除历史自动清理；本阶段以保留数据为先。已保存词条可本地再次查看，不表述为离线词典。

## 构建与验证

```bash
./tools/m1/build.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python3 tools/m3/install.py
python3 tools/m3/instrument.py M3StorageTest M3ProfileDeviceTest M3UiTest LargeFontPopupTest PlatformTest
python3 tools/m3/check_delivery.py
python3 tools/m0/check_secrets.py
```

使用既有隔离 JDK/SDK、固定依赖；Room 2.8.4 与 Kotlin 2.2.21 KAPT。直接运行 am instrument，不使用会卸载被测包的 Gradle UTP。存储测试仅清理自建 UUID 测试库，不清空 Dicta 数据或重置设备。设备是已授权 OnePlus A6013 / fajita、Android15/API35，未使用 OnePlus Ace 6T。

最终 **58/58 JVM、22/22 真机仪器测试通过，lint 0错误/12提示**；提示为固定版本更新、原有 manifest 建议及 KAPT 性能建议，无运行错误。完整构建输出、JVM计数、lint与真机结果见 `evidence/`。未重复执行与本次变更无关的M0付费查询或整套凭据替换测试。

| 真机/实际操作 | 结果 |
|---|---|
| Home 输入 luminescence | 实时生成完整词条并收藏；来源 home 的历史持久保存 |
| Chrome 长按 petrichor → 更多 → Dicta | 实际系统菜单进入共享引擎，展示实时词条，启用收藏；截图05–07 |
| Popup 关闭返回 Chrome | 同一词文本边界前后均 `[75,681][1004,796]`；截图08、`chrome.json` |
| 历史/收藏管理 | 检索、筛选清空选择、当前结果全选、删除确认及独立关系均通过；无结果与空库文案分开 |
| 每日词生成/换一换/缓存 | 实际 BYOK 生成并换词，重复访问复用ID；本地日期/时区/配置槽规则、排重边界及单次HTTP请求有自动化证据 |
| Profile 编辑与导入导出 | Prompt、Schema、Template、Example均在真机编辑；无效配置阻止保存；原生 CreateDocument 导出到本次自建文件后由 OpenDocument 重新导入，校验成功并激活 |
| 自定义模板实际生成 | 导入的 Compact 模板实际生成 quiescent，每日卡按配置仅显示 `Preview quiescent`；截图11，示例预览为vernal（截图10） |
| 强制结束/重新启动 | 历史10条、收藏4条、每日缓存3槽、用户Profile revision 6条及激活ID保持一致；每日词条ID/计数不变；`cold-restart.json` |
| Profile切换后重开旧词条 | luminescence仍按自身完整模板展示，未套用Compact模板，数据库计数及历史事件不变；截图12 |
| 兼容性与回退 | Room重开、构造v1→v2迁移、未知数据库版本拒绝重置、损坏Profile回默认、未知renderer保留原JSON通过 |
| Popup回归 | 两种Accent像素检查、收藏状态同步、2倍字体、固定页脚/滚动、外部点按、Back、重建/旋转、前后台、取消、可编辑源不回写通过 |

通过 `check_delivery.py` 确认设备原有 `provider.enc` 密文字节SHA256与升级前完全一致；没有替换、重新录入或清除BYOK。App/test APK、应用日志及仓库工作树/历史的真实密钥精确扫描通过，原始默认Profile assets逐字节一致。App APK不含FakeProvider或测试录入类，无新增敏感权限。

## 问题定位与修复

Android ICU 正则比桌面 JVM 严格：Prompt 占位符表达式的右花括号未转义，桌面测试通过而真机新请求在发送前失败。真机堆栈定位至 `ProfileDraft.systemPrompt` 后，转义两侧字面花括号，并增加 Android 原生单次替换测试；仍保留插入内容中的字面占位符，避免递归展开。此前已保存的缓存和凭据未受影响。初次失败证据见 `evidence/initial-android-regex-failure.txt`，修复后的最终测试另列。

收尾将管理测试改为唯一 UUID 的专用词条，删除只针对明确的测试事件/词条，既有收藏时不反向切换；两项针对性真机复测通过，前后历史、收藏、每日缓存、Profile与凭据完全一致（`test-data-protection.json`）。

## APK、截图与阶段边界

最终 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，本地交付副本为 `artifacts/m3/Dicta-M3-debug.apk`，版本 `0.3.0-m3` / code3，临时包名 `dev.dicta.prototype`。

截图上传到 [Google Drive /ChatGPT/Dicta](https://drive.google.com/drive/folders/1z5SKVJyuqLFFs87nC2URwMU6mQ82pPKl)，仅供用户人工视觉审阅；**未宣称视觉已通过**。BYOK Settings 使用安全窗口，不采集凭据页面。M4 的 TTS UK/US、最终主题/字体/图标和跨宿主全面视觉验收保持未开始。本次仅API35真机实证，minSdk26为编译下限。

APK：12,503,419 bytes；SHA256 `cb2ace70148ea2adce9523907950f5022d829f6deb72854c150d480046a60ebe`。临时 tcp:8766 reverse、受控本地服务器、测试 APK、本次导出文件及 UI dump 已清理；保留主 App、加密凭据和用户数据。

Git 最终 SHA 与推送状态写回 T04，避免报告自引用提交循环。

官方依据：[Room 迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)、[Room 版本记录](https://developer.android.com/jetpack/androidx/releases/room)、[OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument)、[ActivityResultContracts 源码](https://android.googlesource.com/platform/frameworks/support/+/91a12edbc83943787f4e71fd3b9a74e3cbed8022/activity/activity/src/main/java/androidx/activity/result/contract/ActivityResultContracts.kt)。

## 截图索引（11张，均待用户审阅）

- [M3-01-home-daily.png](https://drive.google.com/file/d/1Cy8H1A11tk9dVMlrP4MLdHTPy-lTgj2V/view)
- [M3-03-history.png](https://drive.google.com/file/d/1qzsIaMXndlRe9honAZooXjH6_872VORs/view?usp=drivesdk)
- [M3-04-vocabulary.png](https://drive.google.com/file/d/1fNNcC2S-OF66B6KQvnJ173b9KDFlf4AH/view?usp=drivesdk)
- [M3-05-chrome-selection-menu.png](https://drive.google.com/file/d/1NFX0JDkbVRIKQQeXo2jR93DjnGinOApg/view?usp=drivesdk)
- [M3-06-popup-unsaved.png](https://drive.google.com/file/d/1vylFPHjpHphYLlaPbsjPBbdK3i2nkfcg/view?usp=drivesdk)
- [M3-07-popup-saved.png](https://drive.google.com/file/d/1ZaBjXVaQnbR9XBfYezl-Otzpwdj0_fT2/view?usp=drivesdk)
- [M3-08-return-to-chrome.png](https://drive.google.com/file/d/1_SZOgihAEABDvLvBBgpqqQ5GY0zraxvc/view?usp=drivesdk)
- [M3-09-profile-template-preview.png](https://drive.google.com/file/d/1Pizk4t2txQ5MPqLs-QgCK6kYaejIOCt3/view?usp=drivesdk)
- [M3-10-profile-preview-export.png](https://drive.google.com/file/d/1FqNhpbuRCHrLBT8LV6q10iPMSXwTdnZ3/view?usp=drivesdk)
- [M3-11-daily-profile-after-restart.png](https://drive.google.com/file/d/1mxPvailFwcvN0UVf_XYGYv2qVX7i2CEJ/view?usp=drivesdk)
- [M3-12-old-entry-after-profile-switch.png](https://drive.google.com/file/d/1tC6HtLuGHvcZZuzvrEPxxILm24WBhmze/view?usp=drivesdk)

原图在本地 `artifacts/m3/`；大小、SHA256和Drive链接见 `evidence/screenshots.json`。
