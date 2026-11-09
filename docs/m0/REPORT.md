# Dicta M0 验证报告

执行日期：2026-11-10（Asia/Shanghai；证据 UTC 为 2026-11-09 晚间）。唯一执行任务 [DICTA-T01](https://app.notion.com/p/4135421e690b81c58cfdc05c54b96177)，范围依据 [项目正典](https://app.notion.com/p/4135421e690b81a08ca0ea72b911557a)。当前会话单执行者完成，不创建子任务、分支或 worktree。起始 main 提交 `4fefdfc`，工作树干净；原始设计资源未修改。

## 结论与验收对应

M0 的规格、实际 API/ADB 验证和可执行默认配置已交付；可以开始 M1 原生基础。没有 Android APK 或原生 Popup，未声称完成 Chrome 划词端到端或人工视觉验收。

| T01 验收 | 成果与证据 |
|---|---|
| 五屏字段/交互/状态与来源 | [VISUAL-CONTRACT.md](VISUAL-CONTRACT.md)，AI/本地/系统字段明确分开；五份 HTML、三 PNG、品牌图及 DESIGN.md 已检查 |
| Prompt/Schema/Template 一致，可真实映射 | [默认 Profile](../../profiles/default/README.md)；3/3 实际输出通过 Draft7 校验及通用模板映射；新增自定义字段测试无需改渲染代码 |
| DeepSeek 能力/容错有实证 | [deepseek-live.json](evidence/deepseek-live.json)、[30项测试输出](evidence/offline-tests.txt)，见下表 |
| Android 官方依据/设备兼容状态/M1 方法 | [ARCHITECTURE-M1.md](ARCHITECTURE-M1.md)；[adb.json](evidence/adb.json)；只读身份/包/显示查询 |
| 最小架构/持久化/M1 明确 | 单模块职责分层，Room+DataStore+Keystore，Profile revision 与旧快照隔离；M1 执行及验收步骤明确 |
| 凭据保护 | 仓库外文件 600、目录700；仅内存认证请求，脚本不回显密钥/响应错误体，不写入配置/APK/Notion；提交前执行字节匹配扫描 |
| 可复现、main 提交推送 | README 命令；最终提交 SHA/远程同步/工作树状态写回 T01（避免报告嵌入自身提交哈希的循环） |

## 实际 API 结果

2026-11-10 当前官方 [Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/) 与实时 `GET /models` 均显示 `deepseek-flash`、`deepseek-v4-pro`；默认使用 flash。设计稿的 `deepseek-chat (V3)` 只是旧占位文案，不能作为本次已验证型号。

共一次 GET 模型列表及五次 POST，无自动重试、无并发刷量。三次生成合计 4539 tokens（包含输入及缓存 tokens，非收费金额）；不以三个样本统计生产 SLA。

| 测试 | 结果 | 用时 | tokens |
|---|---|---:|---:|
| luminous | HTTP200，stop，Schema 和模板通过 | 3.364s | 1626 |
| ephemeral | HTTP200，stop，Schema 和模板通过 | 2.965s | 1592 |
| qzxvplmnonce（人为构造未知串） | HTTP200，stop，明确不识别、IPA/词源 null、空例句 | 1.136s | 1321 |
| 最小 `response_format:json_schema` 探针 | HTTP400 | — | 不记录错误响应体 |
| 故意无效认证 | HTTP401 | — | 不使用真实 key 值作为测试输出 |

[JSON Output 官方说明](https://api-docs.deepseek.com/guides/json_mode/) 要求 `json_object`、Prompt 包含 JSON 指令/示例，并警告空白内容和截断。它提供 JSON 语法输出模式，不能等同服务端严格执行任意 Schema；本次 json_schema 返回400与当前文档一致。稳定默认：JSON mode → finish_reason/空串检查 → 严格 JSON 解析 → 客户端 Schema → 模板绑定 → 持久化。不能直接将模型 JSON 交给 UI。

[DeepSeek strict tool calls](https://api-docs.deepseek.com/guides/tool_calls/) 是 beta endpoint 上的函数参数约束，支持有限 Schema；不是 Chat Completions 的 json_schema response_format。本次未调用 beta，不声称验证过。其他供应商的 native Structured Outputs 仅作为后续能力分支，尚无实测支持声明。

保存的 [examples](examples/) 是本次实际模型结果；对应 `.render.json` 是按模板生成的数据树，不是原生屏幕截图。完整响应中的 request ID、headers、认证、reasoning 内容不保存，只保留必要词条与白名单测量数据。

## 语言与词源质量抽查

`luminous` 的拉丁语 luminosus/lumen 主干与 [Merriam-Webster](https://www.merriam-webster.com/dictionary/luminous) 相符，但模型省略中古英语/法语途径；“与 light 远源关联”未在本次抽查中独立核实。`ephemeral` 的希腊语 ephēmeros 主干与 [Merriam-Webster](https://www.merriam-webster.com/dictionary/ephemeral) 相符；模型说“原指只活一天的生物”过度简化，该参考还记载早期用于短时发热。故结构通过不等于全部事实正确，保留原样例作为证据，不当作语言质量金标准。

未知串测试没有虚构词源，说明 null/unknown 退路在该样本有效，不能推广为永不幻觉。默认 Prompt 要求不确定时说明或返回 unknown，模板明确“AI 生成，未经外部检索核验”；用户可隐藏词源。未来质量抽查包括多义词、罕见词、短语、专有名词与不确定词源。例句标为原创示例，不继承设计稿“经典例句”暗示真实文学引文。

## 异常与配置验证

`tools/m0/verify.py` 是限量 API/契约 spike；`test_verify.py` 30 项离线测试覆盖：空白/null、截断、非法 JSON、重复键、NaN、缺字段/类型/额外字段/空义项、大小上限、无效 envelope、坏绑定/类型/可选路径/越界索引、未知组件/action、远程引用/不支持 union、自定义字段、Prompt 封装、HTTP400/401/402/403/429/500/503/302、断网、取消传播、真实 loopback 延迟服务器导致的总超时。

除真实401/400外，错误状态使用模拟 transport；超时使用本机真实 TCP 服务，未故意拖慢或压测供应商；取消通过受控异步调用证明传播。不是 Android HTTP/TTS 实测。默认不自动重试，用户可主动重试；保存失败、进程死亡、Android 生命周期等在 M1/M2 加入原生测试。

自查发现绑定检查只验证“路径在 Schema 中存在”，却未要求 required/minItems；这会让合法但缺少字段的数据在渲染时失败。先新增3个测试复现失败（可选字段、可选数组索引、未支持的类型 union），再从 Schema 入口/绑定解析修正，完整30项通过。没有把失败掩盖成默认值。

## 设备与 Android 可行性

只连接一个已授权设备；manufacturer OnePlus、model ONEPLUS A6013、`ro.lineage.device=fajita`，与一加 Ace 6T 区分。`ro.product.device` 实际为 OnePlus6T，不能只依赖它等于 fajita。Android15/API35、1080×2340、450dpi；Chrome157.0.8103.46 已安装，HTTPS VIEW Intent 能解析到 Chrome IntentDispatcher。

设备证据由 `check_device.py` 自动保留；脚本先检查唯一设备和型号/codename，再读 Chrome/显示信息，不写序列号。未启动浏览器、安装 APK、触碰个人页面/数据、改系统或网络设置。M0 已确认测试路径的前提条件，**尚未验证 Chrome PROCESS_TEXT 菜单、Dialog 背景透明度/返回栈、TTS 声音与 blur 能力**。这些精确列入 M1/M4，不阻碍 T01 的 M0 验收。

[Android PROCESS_TEXT](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT) + [Compose Dialog](https://developer.android.com/develop/ui/compose/components/dialog) 的路线有官方依据；透明 Activity 宿主负责跨应用入口、Dialog 负责展示。系统不给周边段落，不能承诺自动语境查词。API31+ 的 [window blur](https://source.android.com/docs/core/display/window-blurs) 仍依赖设备/省电状态，应可靠回退 dim。

## 可复现步骤与范围

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/m0/requirements.txt
.venv/bin/python tools/m0/verify.py
.venv/bin/python -m unittest discover -s tools/m0 -v
python3 tools/m0/check_device.py
# 可选，消耗少量真实 API tokens，并覆盖当前证据文件：
.venv/bin/python tools/m0/verify.py --live
```

测试用 Python3、httpx0.28.1、jsonschema4.26.0；没有生成 Android APK。Profile 能直接跑默认查词及生成映射树；原生渲染器、Profile 编辑 UI、Room 实现仍按 M1–M3 分期。

主要后续风险：词源事实质量、Kotlin 验证器与 Python 一致性、工具链安装/版本组合、Chrome 与其他宿主的菜单兼容、深浅主题/Accent/字体与 TTS 真机体验。无需要用户立即处理的 M0 阻碍。建议直接按 M1 执行包构建首个原生原型并在 fajita 上完成 Chrome 菜单调用与返回验证；原生截图上传指定 Drive 等待人工审阅，不将本报告替代视觉验收。

## 最终复核

遵照唯一执行者要求，由本会话自行复核全部交付，未委派其他执行者。30 项测试通过，默认及3份真实样例重新校验通过，所有文档本地链接可解析，设计文件 SHA-256 未变化，`git diff --check` 无错误。[敏感信息扫描记录](evidence/secret-scan.txt) 覆盖工作文件、ZIP 成员与既有历史 blobs；扫描只报告数量和结果，不输出密钥。原始 Stitch 中的虚构示例密钥没有复制到 M0 配置或文档。最终 Git SHA 与推送核验以 T01 回写为准。
