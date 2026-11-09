# 最小原生架构与 M1 交接

## 已定工程边界

先用单 Gradle `:app` 模块、Kotlin/Compose Material 3、ViewModel + StateFlow + 协程、手动依赖注入。包内按职责分层，暂不拆多模块或加入插件系统：

| 边界 | 责任/接口 |
|---|---|
| `platform` | MainActivity、ProcessTextActivity、Android TTS、Keystore |
| `query` | LookupUseCase；归一化输入、请求身份/取消、校验、成功后保存；不依赖 Compose |
| `provider` | Provider.generate(request) → 原始响应；能力表区分 json_schema/json_object/text、模型参数；DeepSeek 首个适配器 |
| `profile` | 版本化 Prompt/Schema/Template、受控子集校验、草稿/激活、导入导出、内容哈希 |
| `render` | JsonElement + Template → 受控 Compose 组件；同一逻辑供 Home/Popup 使用；不按 word/senses 写专用业务 DTO |
| `data` | Room：EntrySnapshot/HistoryEvent/Bookmark/DailySelection/ProfileRevision；DataStore：小型偏好 |
| `ui` | Home/History/Vocabulary/Settings、必要的配置编辑页；状态与事件单向流动 |

初始工程基线建议 minSdk 26（覆盖本机 API35，简化 Java time/文件 API），不是已验证全版本支持承诺。M1 应额外跑 API26 模拟器或调整支持声明。compileSdk/targetSdk、AGP、Kotlin、Compose BOM/JDK 组合必须在 M1 建工程时按当日稳定官方版本及构建结果锁定 version catalog；M0 不凭文档宣称编译过。当前主机仅发现 JDK25、adb，未发现常用路径 Android SDK 或 Gradle；M1 第一步安装项目所需工具链，不需修改全局默认 Java。正式包名、开源许可证和发布时间仍未确定，可先用 `dev.dicta.prototype` 做 debug 原型，发布前再决定正式 applicationId。

候选依赖：OkHttp（取消/超时）、kotlinx.serialization JsonElement、OptimumCode json-schema-validator（Draft7 Kotlin 实现）、Room、DataStore。Python jsonschema 是 M0 参考验证器，不打包到 APK。Kotlin 验证器文档确认支持所用关键词；**M0 未编译它**，M1 必须将本次正/负样例移植测试，核对重复键、null、数组上下限、未知字段、子集拒绝与绑定行为。不要将一般 JSON 反序列化误当 Schema 校验。

参考：[Room](https://developer.android.com/training/data-storage/room)、[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)、[Kotlin JSON Schema validator](https://github.com/OptimumCode/json-schema-validator)。开源 [freeDictionaryApp](https://github.com/yamin8000/freeDictionaryApp) 仅研究其 Compose 词典交互/职责分层，GPL-3.0，未 Fork、未复制源码或素材；不继承其外部词典 API 架构。

## PROCESS_TEXT → Dialog

依据 [Intent 官方契约](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)，PROCESS_TEXT 与两个 extra 在 API23 提供；选中文本是 CharSequence。输入只包含所选文本，不保证周边语境。Manifest 草案如下，**尚未编译运行**：

```xml
<activity android:name=".platform.ProcessTextActivity"
    android:exported="true" android:label="Dicta"
    android:theme="@style/Theme.Dicta.TransparentHost">
    <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

导出入口验证 action/MIME、读取 `getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()`，默认只读，读取 `EXTRA_PROCESS_TEXT_READONLY` 但即使宿主可编辑也不回写或替换原文。空/超长/非预期 Intent 安全结束或提示，不访问外部 URI。使用正常 activity 导航，不启动常驻服务，不申请 overlay/Accessibility 权限。

透明宿主 Activity 只承载一个 Compose `Dialog`，避免背景盖住 Chrome；宿主不另加 dim，Dialog 负责遮罩。`DialogProperties(usePlatformDefaultWidth=false)`，居中卡片宽 `min(480dp, availableWidth-32dp)`、高度扣除 insets，内容滚动、底部 action 可达。所有 dismiss 路径取消该请求并 finish 宿主；onNewIntent/旋转/后台恢复需防止重复请求和重复历史。用 ViewModel 保留有效状态，进程被杀后恢复 entryId 或显示“请重试”，不自动重发付费请求。

[Compose Dialog 官方指南](https://developer.android.com/develop/ui/compose/components/dialog) 支持自定义 Dialog 内部卡片与尺寸。它本身不是跨 App 悬浮服务；跨 App 来源由导出 Activity 接入。上面的组合为文档支持的工程方案，Chrome 实际菜单、透明层与返回栈仍须 M1 实机证明。

[AOSP window blur](https://source.android.com/docs/core/display/window-blurs) 在 API31+ 提供，依赖设备和运行状态；检查 crossWindowBlurEnabled 并监听变化。Compose 内容 blur 不能模糊其他应用窗口。不支持时加 dim、提高卡片不透明度，不尝试截图宿主或变更设备 blur 设置。

## Provider、配置和安全

能力按 provider+endpoint+model 记录，不因“OpenAI compatible”就假定所有参数兼容。首个已实证路径为 DeepSeek Chat Completions JSON mode；未来确认支持原生 json_schema 的供应商可优先该模式，但仍客户端校验；拒绝/截断/空响应先于 Schema 判断。DeepSeek beta strict tool calls 为独立协议，未实测，M0 不混入默认。

网络使用 HTTPS，不自动跟随携带认证的跨域重定向；用户明确配置 endpoint 后再关联凭据，导入 Profile 不自动将已有密钥发送到新域名。connect 10s/read 40s/总计60s、响应上限128KiB、零自动重试。取消必须传播到底层 HTTP call。日志只记安全类别、耗时、token 数，不能记密钥、headers、完整 body 或异常响应原文。有限重试若后续增加，需显式策略，不能循环修复。

Android [Keystore](https://developer.android.com/privacy-and-security/keystore) 保存不可导出的 AES-GCM 密钥，应用私有文件只保存 nonce+ciphertext+tag，nonce 每次随机；凭据按 endpoint identity 分开关联。明文只在使用时短暂驻留内存，不进入 StateFlow 持久快照、savedState、剪贴板、日志、备份或导出。凭据文件排除 Auto Backup；Keystore 失效时要求重新输入，不悄悄回退明文。密钥编辑/显示界面启用截图保护，离开即遮罩。首次查询缺 BYOK 时进入设置，不把开发电脑的密钥编译进 APK。

## 持久化与一致性

| 实体 | 建议字段/约束 |
|---|---|
| EntrySnapshot | UUID、normalizedQuery、原查询、validatedJson、profileRevisionId、schemaJson、templateJson、rendererVersion、provider/model、createdAt；不可变 |
| HistoryEvent | UUID、entryId FK、source(home/popup)、queriedAt；按 queriedAt DESC,id DESC；打开旧记录不产生 API 请求 |
| Bookmark | entryId 唯一 FK、savedAt；重复收藏幂等；M0 不设复习算法 |
| ProfileRevision | profileId+revision、Prompt/Schema/Template/Example、非敏感模型配置、内容哈希、builtin/user、createdAt；不可变激活版本 |
| DailySelection | localDate+profileRevisionId、zoneId、entryId FK、refreshCounter；每日卡原子替换，失败保留旧卡 |
| Preferences | activeProfileRevision、主题、Accent、TTS 开关/口音/引擎、非敏感 provider 配置；DataStore |

查询成功：校验 → Room 事务保存 EntrySnapshot+HistoryEvent → UI success。缓存命中需 query+profile 内容哈希+provider/model 一致，显式刷新才重查。历史/收藏重开直接读 snapshot，不调用模型。查询历史删除仅移除 HistoryEvent；取消收藏仅移除 Bookmark；只有没有 history/bookmark/daily 引用的快照才允许清理，使用事务与 FK RESTRICT 防误删。为排序/外键/归一化搜索建立索引，初始规模用本地 LIKE 查询足够。

旧记录按自身快照 Schema/Template 展示，不套新 active Profile。未知 rendererVersion 提示不兼容，提供受限纯文本数据查看及恢复默认选择，不抹除原 JSON。Room schemaVersion 与 Profile revision 分开；升级写显式 Migration 和持久化测试，不允许 destructiveMigration。

每日词：本地日期切换后首次访问才生成；相同日期/profile 复用缓存。换一换一次一个请求、按钮 busy，exclude 当前及最近20个每日词；若模型返回重复词保留旧卡并提示重试，不无限循环。成功事务替换 DailySelection，旧词若被收藏/历史引用仍保留。时区变化按当前 localDate/zoneId 重新判定；不做后台定时付费调用。M0 定义策略，M3 实现并实测。

## M1 单会话执行包

前置：重读项目正典/T01、本报告，确认仍是 main、记录用户改动；不创建分支或 worktree。

1. 安装隔离 Android SDK/JDK/Gradle Wrapper，锁稳定版本、建立单 :app debug 工程，最初不嵌入 API 密钥。将默认 Profile 作为只读 assets；本地 fake Provider 使用本次 JSON 样例，明显标记“演示数据”。
2. 实现统一 JsonElement/Template Compose renderer（本次 DSL）、Home 搜索入口、History 浏览、Vocabulary 入口、Settings 外壳与主题 token。验证改模板顺序/字段可见性无需改 Kotlin。M1 可用最小 Room 快照支撑跨进程重开；完整批量管理、配置编辑器留 M3。
3. 实现导出的 PROCESS_TEXT Activity + 透明宿主/居中 Dialog。接入 fake Provider 的成功、加载、错误、超时、取消场景，关闭返回原宿主。Popup 详细释义复用同一 entryId，不能产生第二次查询。
4. `check_device.py` 再次确认唯一设备为 fajita；安装自己的 debug 包。先用显式 `adb -s <已核实序列号> shell am start -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT ephemeral --ez android.intent.extra.PROCESS_TEXT_READONLY true -n <debug包名>/<ProcessTextActivity完整名>` 诊断入口；再在 Chrome 打开专用非敏感测试页、长按英文、展开菜单、选择 Dicta。这两个测试必须分开记录，显式启动成功不代表菜单成功。
5. 验证 Chrome 保持背景可见、居中/滚动、Back/关闭/外部点按返回原位置、重复选择/不同词/空词、旋转、大字体、进出后台、不覆盖可编辑原文。记录 API26 与当前 API35 差异；若 Chromium 页面/宿主不提供 PROCESS_TEXT，记录兼容范围，不转向 AccessibilityService。
6. 运行单元/仪器测试、assembleDebug、lint；记录版本/命令/设备证据。抓取首个原生五屏外壳与 Chrome Popup 的必要截图，上传用户指定 Google Drive `/ChatGPT/Dicta`，关联截图及差异清单供用户人工审阅。未经用户审阅不标记视觉验收完成。
7. 按 main-only 提交推送，写回交接。M1 Done 需工程可复现构建、两条入口共享 renderer、Chrome 菜单真实调用/返回成功并有证据；视觉最终通过在 M4。

M1 不提前做多 Provider、生产 BYOK、完整 Profile 编辑器、完整历史/收藏管理、每日词自动生成和复杂 TTS 体验；这些依照项目 M2–M4 实施。M1 使用真实 API 若确有必要，仍从设备本地安全录入凭据，不复用 APK 内置 key。

## 明确待证实

- Native 工具链构建、Kotlin Schema 一致性与 Compose 真机布局/返回栈（M1）。
- Chrome 实际选词菜单行为、各阅读器兼容、blur 可用性（M1/M4）。M0 只证明 ADB 和 Chrome VIEW 路由可用。
- TTS 引擎/UK-US voice 的真实可用性和回退、字体资源许可与字形（M4；不从 IPA 推断系统声音）。
- 三个样例不能代表词源和语言内容质量；模型自评不是来源核验，需持续抽查并保持不确定性显示。
- 正式应用 ID/许可证/发布时间与其他 Provider 的支持范围尚未决定，不影响 M1 debug 原型。
