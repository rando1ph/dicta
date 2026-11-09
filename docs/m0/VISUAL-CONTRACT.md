# 五屏视觉与内容契约

依据：项目/DICTA-T01 全文、`design/stitch/stitch_dicta_android_dictionary/` 五份 HTML、三份 PNG、`nocturne_lexicon/DESIGN.md` 与品牌图。ZIP 内只有 Popup/Home/Settings 三张 PNG，History/Vocabulary 的定稿是 HTML；不是缺失待补的 M0 设计任务。已逐个检查内容、样式和脚本，并验证 ZIP 与解压文件逐字节一致，哈希见 `evidence/design-sha256.json`。HTML 中点击模拟、假连接延迟、假密钥、固定计数并非实现或实测。

## 字段归属

| 显示内容 | 默认数据路径 | 来源与规则 |
|---|---|---|
| 词头 | `/word` | AI；输入原词另由本地保留；不假设固定是形容词 |
| UK/US 音标 | `/pronunciation/uk`, `/pronunciation/us` | AI；允许 null，未提供时隐藏，IPA 不等于 TTS 口音 |
| 义项词性 | `/senses/*/partOfSpeech` | AI；顶部/列表采用首义项，正文逐义项显示 |
| 中英释义 | `/senses/*/definitionZh`, `definitionEn` | AI；1–4 义项，序号由本地按顺序产生 |
| 例句与译文 | `/senses/*/examples/*/en`, `zh` | AI 原创，不冒充书籍引文；可以为空 |
| 搭配 | `/senses/*/collocations` | AI 字符串列表；容纳 Popup 第二义项的短语 |
| 同义词 chips | `/synonyms` | AI；第一版仅展示，不自动链式请求 |
| 词源摘要、详情、确定性 | `/etymology/summary`, `detail`, `certainty` | AI；未知为 null，UI 标记未经外部检索核验，可通过模板隐藏 |
| 收藏状态、历史时间、计数、选中集合 | Entry/History/Bookmark 元数据 | 本地；禁止让模型生成 |
| 每日词标签、日期、最近词条 | DailySelection/History | 本地日期与缓存策略；不把 WOTD 标签或装饰柱形图解释成词频统计 |
| 发音按钮、可用口音、播放状态 | TTS 能力和偏好 | Android；不是 AI 音频，也不是语音输入 |
| 加载、错误、取消、重试、详细释义 | 请求状态/宿主 action | 本地控制；详细释义打开原快照，不另做深度解析 |
| Endpoint/Model/Profile/主题/Accent/TTS 引擎 | 配置与系统 | 本地；密钥另用 Keystore 保护，Ready 必须来自真实检查 |

## 屏幕与交互

| 屏幕 | 容器/组件 | 必要行为与状态 |
|---|---|---|
| Popup | 居中圆角浮卡、可见原应用背景、词头/词性、发音、音标、义项/例句/搭配、同义词、词源、收藏/详细释义、关闭 | PROCESS_TEXT 输入；等待时显示当前查询及取消；关闭/Back/外部点击取消网络并 finish；成功才保存，收藏按钮状态同步；详细释义不重复请求；长词条在卡内滚动，操作区可达 |
| Home | 品牌/副标题、搜索框、每日单词卡、最近查词、底部四入口 | IME Search 提交；输入查询结果与每日词是不同模式，共用 Entry renderer；每日词可收藏/TTS/换一词；Recent 打开快照，管理跳 History，清空需确认且不误删收藏；首次、加载、失败保留旧卡、无历史状态 |
| History | 连续倒序列表、搜索、Manage、多选浮动操作栏 | 本地搜索词头与摘要；默认浏览，管理后逐条选择/取消、批量收藏和删除；零选中禁用操作，删除确认；不按日期分组、不导出；相对时间为本地格式化，不固化 48 条或默认勾选前三条 |
| Vocabulary | 收藏列表、搜索、计数、Manage、多选/全选、移出 | 默认浏览旧词条；管理中选择、取消、全选当前筛选结果、批量移出；移出只取消收藏，不删历史；空库与搜索无结果分开；文案改为“已保存词条可再次查看” |
| Settings | BYOK 配置卡、Profile 入口、主题段控件、Accent 圆点、TTS 设置 | HTTPS Endpoint/密钥/模型可编辑；测试连接明确费用与结果；密钥默认遮罩，必要查看时保护截图；Profile 校验/预览/导入导出/恢复默认；System/Light/Dark；口音 UK/US 与 IPA 文案分开；TTS 不可用明确降级 |

History/Vocabulary 切换搜索条件后清空选中集合，避免操作不可见项目。无匹配时不显示“没有保存词条”的首次使用文案。收藏按 entry 快照关联而不是只靠词头，后续更新同词释义不静默覆盖已收藏内容。

## 统一状态机

Idle → Loading(requestId, query) → Success(valid entry) / Error(code)。取消进入 Cancelled 并停止底层 HTTP；Popup 取消后直接关闭。新请求取消旧请求，只接纳最新 requestId 的返回。成功持久化后才展示收藏可用状态；存储失败明确提示未保存。Error 有手动 Retry 或配置入口，绝不无限转圈；旧卡标注旧结果，不混入新查询。

| 失败类 | 用户反馈 | 策略 |
|---|---|---|
| 空/过长输入 | 请输入英文词或短语，最多 100 字符 | 本地拦截，不请求 |
| HTTP 401/403、402 | 检查密钥/访问权限、余额 | 不自动重试 |
| HTTP 400 | 请求参数或 Provider 能力不匹配 | 不暗中切换到无校验模式 |
| HTTP 429、5xx、断网 | 稍后重试/检查网络 | 默认零自动重试，避免重复付费；429 可显示 Retry-After |
| 连接/读取/总超时 | 查询超时，可重试 | 10s/40s/60s 边界；取消底层调用 |
| 空响应/截断/非法 JSON/Schema 错误 | 返回内容不完整或格式不符 | 不保存失败词条；不显示半截 JSON；允许手动重试 |
| 模板错误 | 配置无法展示，请修正或恢复默认 | 保留 active revision；旧快照用旧模板；损坏记录可用安全纯文本 JSON 查看 |
| TTS 缺失、口音不可用 | 提示未安装/使用可用英语声音 | 不保证 UK/US 都可用，不调用收费语音 API |

## 视觉实现边界

沿用暗色 `#141218`、高层容器、紫色交互、琥珀色词源/收藏、蓝色词性；以 DESIGN.md 前置 token 和已批准 PNG 为主，解决其正文与 HTML 内部分歧时不改整体风格。词头/例句 Newsreader，UI/IPA Roboto Flex；History HTML 使用 Inter 是导出差异，原生统一 UI 字体。字号从 px 语义转换为 sp，间距 4dp 网格、卡片圆角 28dp、内边距约 24dp；点击热区至少 48dp。系统中文/IPA fallback 需真机检查。字体文件及授权尚未入库，M1 可先系统 serif/sans fallback，视觉验收前补齐许可与字形验证。

默认模板保持设计的内容顺序；Home 可以以独立例句井/词源井装饰，Popup 更紧凑。顶部词性重复由容器适配决定，不能硬编码默认 JSON 字段。Light 和 Accent 切换沿用 Material 语义色，M4 真机人工审阅才算最终视觉验收。

`DESIGN.md` 的 anchored popup、拖动把手、底部快速搜索与频率指标属于较早设计系统建议；最终 PNG/项目明确的居中 Dialog 与五屏内容优先，不新增这些功能。背景书页是宿主示意，不读取周边文本、不复制进 Dicta。模糊为可选增强，不支持时用 dim+不透明卡片；不以截图宿主实现模糊。

排除：离线词库、独立深度解析、剪贴板快捷入口、OCR、语音输入、意象图片、词源树、悬浮球、AccessibilityService、Root/Shizuku。原 HTML 中未出现的缓存清除模拟脚本不据此扩展成正式设置功能。
