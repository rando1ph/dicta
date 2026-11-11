# T07 · Dicta v1.1.0 candidate

工程实现和模拟器验证已交付，**人工截图及候选版本验收待用户审阅**。Notion T07 保持 **In progress**。本阶段只执行 T07；没有正式签名、Git tag、GitHub Release 或 T08 操作。

- [Notion T07](https://app.notion.com/p/4155421e690b814b8277f96327ad3bcf) / [项目主记录](https://app.notion.com/p/4135421e690b81a08ca0ea72b911557a)
- [Google Drive 原始 PNG 目录](https://drive.google.com/drive/folders/1gFzRZcU8--qtCNtvcei3kMU8SbpCampT) / [逐张截图索引](SCREENSHOTS.md) / [Drive 内的 Markdown 索引](https://drive.google.com/file/d/1xPKqDg2ePu2hIoDLb2wjs2iWxiDpGbxx/view?usp=drivesdk)
- Git 基线：main / `e7a1611013af7cdc06cd7d80c9fb80746966d688`。所有实现、测试和本报告直接在 main 完成；提交 SHA 见本报告所属提交及 T07 回写。

## 实施与验收覆盖

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

## 接口支持边界

| 协议 | Chat Completions 请求 |
| --- | --- |
| DeepSeek | json_object、thinking disabled、max_tokens；保留原默认模型及旧配置自动识别。 |
| OpenAI | json_object、max_completion_tokens；官方根地址自动补 v1，不发送 DeepSeek thinking。 |
| Compatible | HTTPS + Bearer + OpenAI Chat Completions / choices 响应形状；不发送供应商专属 thinking。代理可手动选择 OpenAI 请求协议。 |

不支持 JSON mode 的接口可显式选择「仅提示词 JSON」，两种方式始终执行客户端 Schema 校验。HTTP 400 / 422 提示调整模型、协议或输出方式，由用户手动重试。模型列表不保证每个 ID 都支持文本查词；本阶段未开发原生 Anthropic、Azure 特有鉴权或 Responses API 适配。

依据：[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[OpenAI models](https://developers.openai.com/api/reference/resources/models/methods/list)、[DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[DeepSeek models](https://api-docs.deepseek.com/api/list-models/)、[Android 状态保存](https://developer.android.com/develop/ui/compose/state-saving)、[Android 返回处理](https://developer.android.com/guide/navigation/custom-back)。Android 文档通过 Google 官方 CLI 检索和读取。

## 验证

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

## 候选 APK 与待审事项

`artifacts/t07/Dicta-v1.1.0-candidate-debug.apk`，Application ID `dev.randolf.dicta`，versionName `1.1.0-candidate` / code 6。大小、SHA-256 及证书见 [APK 元数据](evidence/apk.json)。这是 Android Debug 签名包；**不能覆盖安装正式签名的 v1.0.0，请勿为测试而卸载正式版或清数据**。

模拟器升级基线为空 Room 库且未配置 Key；测试结束后原有 4 个持久化文件逐一 SHA-256 一致，移除本任务临时 Key / 外观配置，并恢复原屏幕、字体与导航模式。候选包冷启动正常，数据库表计数与原始基线一致。另用有数据的 Room、旧 AES-GCM 凭据、Profile / Prompt / Schema / Template 快照测试验证兼容。正式证书下的真机保留数据覆盖安装仍须在用户批准的后续签名阶段验证。本轮没有把 minSdk26 当作已跨版本实测。

AOSP 未安装 TTS 引擎，UK / US 控件正确禁用并提示设置；没有宣称本轮已重做真机扬声器或自然度验收。DeepSeek / OpenAI / Compatible 的协议已用 TLS Mock 验证，本轮没有真实账户连通或模型质量验收。

请用户审阅 20 张截图、导航返回、模型选择与 Profile 覆盖说明、Key 保存/更换/清除、响应式降级，以及 Debug 候选实现。人工验收和启动 T08 均等待用户明确批准。
