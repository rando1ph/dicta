# T07 · Dicta v1.1.0 candidate

## 2026-11-11 收尾修订 · 待最终审阅

四项最新用户决策已实现、验证并交付截图。Notion T07 保持 **In progress**，等待用户最终审阅。仅在 main 修改和提交；T08 未启动，没有正式签名、tag 或公开 Release。

- [Notion T07](https://app.notion.com/p/4155421e690b814b8277f96327ad3bcf)
- [原 Google Drive 审阅目录](https://drive.google.com/drive/folders/1gFzRZcU8--qtCNtvcei3kMU8SbpCampT) · [最新 Drive 截图索引](https://drive.google.com/file/d/1_NZzl7M8n1biVqh4SIfHCxOPUtSRiBtK/view?usp=drivesdk) · [仓库逐图索引](SCREENSHOTS.md)
- 本轮基线：main / `8ab0c28d6af56dd4f26fa1b9a79a9bcf576e2f5a`；提交 SHA 见本报告所属提交及 Notion 回写。

### 四项结果

| 要求 | 当前行为 |
| --- | --- |
| 每日单词 | 首次进入 Search 自动检查日期、时区、Profile、服务地址和生效模型对应的缓存，未命中时自动生成。保留手动「换一换」、失败「重试」与旧卡片。互斥控制并发；同一配置的失败自动尝试不会因返回、重组或前后台切换反复调用。缓存读回、跨日、时区和配置变化均有设备测试。每日词不加入查询历史。 |
| API Key 边界 | Key 归属已保存的 HTTPS 主机及端口。同服务编辑 Model、协议、输出模式或 API 资源路径可保留 Key；切换服务必须明确输入新 Key，空输入拒绝保存且保留原配置。UI 清空跨服务草稿 Key、取消旧 Key 的遮蔽已保存状态；模型列表和测试操作在未保存的新连接下不可用。实际 TLS 请求验证新主机的 models/completions 只收到新 Key。旧加密存储格式不变。 |
| 词源 | 默认标题改为「词源」，移除默认 certainty 展示；Prompt 继续禁止编造，词源摘要/详情只承载派生内容。共享 ProfileEngine 对 v1.0.0 默认长标题做展示映射，并隐藏 `/etymology/certainty` 节点，覆盖每日词、Search/Detail、Popup、History/Vocabulary。旧 raw JSON、Schema、Template、Profile 和已存词条不重写；自定义标题、其它字段与顺序保留。 |
| 发布流程 | T07 模拟器回归和截图 → 用户审阅、明确批准 → T08 在模拟器/测试环境以原正式证书验证保留数据覆盖升级，再签名发行 → 用户发行后自行在 Ace 6T 体验。发布前没有实体手机验收关卡。 |

### 本轮验证

| 检查 | 结果与证据 |
| --- | --- |
| JVM | **81/81**，包含新增服务凭据边界与默认/旧模板词源兼容测试。 |
| 常规模拟器 | **30/30**，411dp / 100% 字体；包含八项收尾修订用例、既有导航、Provider、Room、Profile、Popup、TTS 回退与布局回归。 |
| 窄屏大字体 | **4/4**，320dp / 200% 字体；覆盖页面/按钮、Popup 旧快照和跨服务 Key 提示。 |
| Profile 契约 | **30/30** Python 回归，默认 Prompt/Schema/Template 仍满足既有契约。 |
| 构建与 lint | Debug App、测试 APK 构建成功；lint **0 错误 / 13 项既有提示**。 |

[测试统计](evidence/tests.json) · [构建、lint、常规回归日志](evidence/revision/regression-normal.txt) · [窄屏回归](evidence/revision/regression-large.txt) · [Profile 契约](evidence/revision/profile-contract.txt)。

先用用例复现每日词不自动生成、跨主机空 Key 被保留、旧模板仍显示免责声明，再分别修复：[设备修复前证据](evidence/revision/before-fix-device.txt)、[词源修复前证据](evidence/revision/before-fix-etymology.txt)。测试夹具修正了 HTTP/2 authority 记录、失败提示后按钮滚动定位和审阅命令文件的原子交接；一次审阅辅助会话因读到未完成命令中断，未作为产品通过证据，其后的审阅会话正常结束。构建辅助脚本将 kapt 编译与 lint 顺序执行，避免 lint 读取正在重建的测试存根。

仅操作 **codex_api36_aosp / emulator-5554，Android 16 / API36**。所有模型调用均为本地 TLS Mock，真实付费 API 调用 **0 次**；测试传输明确禁止外部 DNS。AOSP 无 TTS 引擎，本轮只验证已有禁用/回退，没有重做真实语音质量或真实账户模型质量验收。官方 Android CLI 用于文档查阅、原始截图、部署启动与布局读取。

### 截图与数据兼容

新增 **8 张原始 PNG（21–28）**已逐张视觉检查并追加到原 Drive 目录，核对文件夹、PNG 类型和大小，保留本地 SHA-256。覆盖自动每日词、展开词源及换词按钮、实际 v1.0.0 历史模板、新服务空 Key 提示、新查询详情、Popup、320dp/200% 和 Light/Accent 对照。收藏旧快照也经独立 UI 回归，避免重复上传相同画面。Popup 补图来自明确的 PROCESS_TEXT 测试 Intent；首轮外部浏览器交互记录属于历史证据。**01–20 为首轮候选历史，不代表最新版；21–28 待用户审阅。**

Room 仍为 v2，Application ID、Keystore alias 与凭据文件格式不变。v1.0.0 Template 夹具直接取自发布 tag；设备测试逐字段确认历史/收藏快照原数据不变。模拟器原始四个文件在测试结束后逐字节恢复，未卸载主 App 或清除用户数据；冷启动后六张用户表仍与原始空基线一致。已恢复屏幕、字体设置，移除测试 APK，保留最新候选 App。[数据/设备证据](evidence/device.json) · [安全扫描](evidence/security.json)。

### 最新候选与待审

本地 APK：`artifacts/t07-revision/Dicta-v1.1.0-candidate-debug.apk`，`dev.randolf.dicta`，`1.1.0-candidate` / code 6，12,646,013 字节。SHA-256：`265ac7934f796ea968843458d1920eabab3744d4ce1c0e8a851745910f61fd15`。已验证 Debug 签名，安装包哈希一致。[APK 元数据](evidence/apk.json)。

这是 Debug 测试包，不能覆盖正式签名的 v1.0.0；不要为此卸载正式版或清数据。正式证书下的保留数据升级验证留待获批后的 T08 在模拟器/测试环境执行。用户最终审阅截图、四项修订和候选实现之前，不启动 T08。

## 首轮候选历史 · 8ab0c28

以下保留原交付记录；其中「仅手动生成每日词」、旧词源提示和发布前真机相关表述均已由上方收尾修订及最新用户决策覆盖。

工程实现和模拟器验证已交付，**人工截图及候选版本验收待用户审阅**。Notion T07 保持 **In progress**。本阶段只执行 T07；没有正式签名、Git tag、GitHub Release 或 T08 操作。

- [Notion T07](https://app.notion.com/p/4155421e690b814b8277f96327ad3bcf) / [项目主记录](https://app.notion.com/p/4135421e690b81a08ca0ea72b911557a)
- [Google Drive 原始 PNG 目录](https://drive.google.com/drive/folders/1gFzRZcU8--qtCNtvcei3kMU8SbpCampT) / [逐张截图索引](SCREENSHOTS.md) / [Drive 内的 Markdown 索引](https://drive.google.com/file/d/1xPKqDg2ePu2hIoDLb2wjs2iWxiDpGbxx/view?usp=drivesdk)
- Git 基线：main / `e7a1611013af7cdc06cd7d80c9fb80746966d688`。所有实现、测试和本报告直接在 main 完成；提交 SHA 见本报告所属提交及 T07 回写。

### 实施与验收覆盖

| T07 范围 | 实际交付与证据 |
| --- | --- |
| A · 独立 Word Detail | Search 主动查询、首页最近查询、History、Vocabulary 均在来源页之上进入详情；隐藏底部导航。顶部返回和系统返回恢复原 tab、输入、筛选及列表滚动。加载、错误、取消、重试留在详情；不会把结果插入首页。 |
| A · 快照及 Popup | 保留共享 Schema / Template 渲染器、容器圆角、TTS 与收藏；已存词条按 ID 读取自身快照，不新增模型请求。Popup「打开」进入独立完整详情；返回结束该详情 Activity 并回到外部浏览器。 |
| B · 通用 BYOK | AI Provider / 模型服务卡片；DeepSeek、OpenAI、Compatible 预设，HTTPS Base URL / 完整 API 路径规范化。按协议发送 token 参数；DeepSeek 专属 thinking 不会发给其它协议。 |
| B · 模型发现与失败后备 | 保存可用连接凭据后 GET models；可搜索选择、手动刷新及 Model ID 输入。认证、无端点、网络、响应错误有明确反馈。列表成功与实际查词校验分开；不根据失败自动重发收费请求。 |
| B · Key 与优先级 | 移除 Settings FLAG_SECURE；固定八点遮蔽 + 已保存，存储明文不回填 Compose 或 saved state。保留原 Key 编辑其它字段，更换后清空输入；更多菜单中的清除须确认。Profile 非空 model 优先于全局；界面与实际请求共用优先级规则，空全局 ID 不会拦截有效 Profile model。 |
| C · 响应式 | 保存为独立全宽主按钮、测试为次按钮；每日词操作 FlowRow。窄屏大字体导航降级为图标并保留完整无障碍名称；按实际窗口宽度判断。修复多行词源展开标题被按钮圆角裁切。 |
| D · 数据与测试 | Application ID 不变，Room v2、默认 Profile assets、Keystore alias、旧加密配置格式和历史快照兼容。74 项 JVM、22 项常规模拟器测试、3 项 320dp / 200% 字体测试通过。 |
| E · 交付 | 20 张实际运行原始 PNG 已上传，逐张视觉检查并核对 Drive 文件夹、PNG 类型及字节大小；索引含场景、设备/API、屏宽、字体、主题、链接、SHA-256 与待审状态。 |

每日词继续保留和读取缓存；首次获取及换词由用户主动触发。进入首页、返回详情、保存配置、获取模型列表不会自动生成收费词条。「测试查词」明确提示可能产生用量，使用已保存配置和当前 Profile，成功后不加入历史。

### 接口支持边界

| 协议 | Chat Completions 请求 |
| --- | --- |
| DeepSeek | json_object、thinking disabled、max_tokens；保留原默认模型及旧配置自动识别。 |
| OpenAI | json_object、max_completion_tokens；官方根地址自动补 v1，不发送 DeepSeek thinking。 |
| Compatible | HTTPS + Bearer + OpenAI Chat Completions / choices 响应形状；不发送供应商专属 thinking。代理可手动选择 OpenAI 请求协议。 |

不支持 JSON mode 的接口可显式选择「仅提示词 JSON」，两种方式始终执行客户端 Schema 校验。HTTP 400 / 422 提示调整模型、协议或输出方式，由用户手动重试。模型列表不保证每个 ID 都支持文本查词；本阶段未开发原生 Anthropic、Azure 特有鉴权或 Responses API 适配。

依据：[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[OpenAI models](https://developers.openai.com/api/reference/resources/models/methods/list)、[DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[DeepSeek models](https://api-docs.deepseek.com/api/list-models/)、[Android 状态保存](https://developer.android.com/develop/ui/compose/state-saving)、[Android 返回处理](https://developer.android.com/guide/navigation/custom-back)。Android 文档通过 Google 官方 CLI 检索和读取。

### 验证

- [JVM、lint 与模拟器测试统计](evidence/tests.json) / [构建记录](evidence/build.txt) / [常规测试](evidence/instrumentation-normal.txt) / [窄屏大字体测试](evidence/instrumentation-large.txt)。Lint 0 错误、13 项既有建议；没有为本任务升级整套依赖。
- 仅 `codex_api36_aosp / emulator-5554`，Android 16 / API36；1080×2400 / 420dpi / 411dp / 100% 字体，以及 840×1920 / 320dp / 200%。实际执行官方 CLI 启动、布局获取、原始截图，ADB 点击、输入、长按、滚动和边缘返回手势。没有操作实体手机。
- 外部宿主为 AOSP WebView Browser Tester 133 与受控阅读页。实际长按 ephemeral → 系统 Dicta 菜单 → Popup → 打开完整详情 → 边缘返回，以及 Popup 关闭返回均通过。正文边界进入前、详情返回后、关闭后均为 `[63,777][1021,1192]`。[浏览器证据](evidence/browser.json)。
- 模型请求均用本地 TLS Mock，真实付费模型调用 **0 次**。Mock transport、临时证书及 review fixture 仅在测试 APK；候选 App 不包含 FakeProvider / MockWebServer / 录入夹具。
- 先复现并修复「空全局 ID 拦截有效 Profile model」；查词测试及每日词请求均验证实际发送 Profile model。[修复前失败证据](evidence/profile-model-before-fix.txt)。早期夹具 DNS 主线程调用、文本选择器歧义、ActivityScenario 生命周期清理和截图时钟问题已修正；一次重叠 instrumentation 造成的中断轮次作废，最终测试按顺序执行。
- 代码、APK、日志和新增交付文件经过密钥扫描；原图仅为测试数据与遮蔽字段。安全、设备及数据保留结果见 [security.json](evidence/security.json) / [device.json](evidence/device.json)。

复现常规 Mock 验收：

```sh
bash tools/t07/test.sh emulator-5554
```

窄屏场景先显式设置该模拟器的 `wm size 840x1920` 和 `font_scale 2.0`，仅运行 T07ResponsiveTest、T07PopupTest，再恢复先前设置。截图重现通过显式 opt-in 的 T07ReviewSession；所有读取、点击及截图仍使用实际 App 窗口。`tools/t07/review.py` 提供界面操作，`tools/t07/index.py` 核对原图与已上传文件并生成索引。

### 候选 APK 与待审事项

`artifacts/t07/Dicta-v1.1.0-candidate-debug.apk`，Application ID `dev.randolf.dicta`，versionName `1.1.0-candidate` / code 6。大小、SHA-256 及证书见 [APK 元数据](evidence/apk.json)。这是 Android Debug 签名包；**不能覆盖安装正式签名的 v1.0.0，请勿为测试而卸载正式版或清数据**。

模拟器升级基线为空 Room 库且未配置 Key；测试结束后原有 4 个持久化文件逐一 SHA-256 一致，移除本任务临时 Key / 外观配置，并恢复原屏幕、字体与导航模式。候选包冷启动正常，数据库表计数与原始基线一致。另用有数据的 Room、旧 AES-GCM 凭据、Profile / Prompt / Schema / Template 快照测试验证兼容。正式证书下的真机保留数据覆盖安装仍须在用户批准的后续签名阶段验证。本轮没有把 minSdk26 当作已跨版本实测。

AOSP 未安装 TTS 引擎，UK / US 控件正确禁用并提示设置；没有宣称本轮已重做真机扬声器或自然度验收。DeepSeek / OpenAI / Compatible 的协议已用 TLS Mock 验证，本轮没有真实账户连通或模型质量验收。

请用户审阅 20 张截图、导航返回、模型选择与 Profile 覆盖说明、Key 保存/更换/清除、响应式降级，以及 Debug 候选实现。人工验收和启动 T08 均等待用户明确批准。
