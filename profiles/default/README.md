# 默认 Profile v1

这套可编辑配置从已批准 Popup/Home 推导，是真实调用使用的配置。`profile.json` 为清单，`prompt.md` 为系统提示词，`entry.schema.json` 为词条数据契约，`popup.template.json` 为通用渲染顺序与映射；`template.schema.json` 校验模板 DSL，`example.json` 为少量人工构造的形状示例。

## 数据契约

采用 JSON Schema Draft-07 的受控子集，兼容当前 Kotlin 候选验证器和 Python 实证工具。允许 `type`、`properties`、`required`、`additionalProperties:false`、`items`、`enum`、字符串长度、数组数量、数值上下界与描述元数据。类型限 object/array/string/number/integer/boolean/null，以及 `["string","null"]`。不接受用户 `$ref`、远程引用、递归、正则、组合逻辑或其他未支持关键字，不能静默忽略。内置模板元 Schema 自身可使用本地 `$ref`/条件约束，不等于用户词条 Schema 开放这些功能。

默认对象字段全为 required；未知可空内容用 null，可空列表用 []。字段均有字符串/数组上限，最多四义项。校验只证明结构，不保证释义、音标和词源真实性。`etymology.certainty` 保留为内部数据以兼容旧词条，不在界面显示。词源卡片直接显示摘要和详情，不显示独立标题或折叠按钮。不编造频率、来源链接、音频 URL。模型输出按纯文本显示。

## Prompt 与 Provider

`{{schema_json}}`、`{{example_json}}` 由本地 JSON 序列化结果替换；不做递归模板执行。输入单独序列化为 user 消息：

```json
{"mode":"lookup","query":"ephemeral","exclude":[]}
```

daily 模式同一 Prompt/Schema，query 为空，exclude 为近期已选词列表（M3 应用实现；M0 仅定义协议，未验证每日选词效果）。不自动获取周边上下文。输入去首尾空白，最长 100 Unicode 字符，支持词汇及短语，空输入不请求。

实证默认 `https://api.deepseek.com` + `deepseek-flash` + `json_object`，非思考模式、非流式、最多 2200 输出 tokens。Prompt 包含 JSON 字样和示例。开发脚本故意只向固定 DeepSeek 域名发送外部开发密钥，**不是任意 Provider 配置编辑器**；M2 再实现自定义 HTTPS Endpoint 和 Provider 适配。`credentialRef:null` 表示导出文件无凭据，导入后需关联设备本地密钥。

## Template DSL v1

- `nodes` 数组即展示顺序；`row` 横向自动折行、`column` 纵向、`repeat` 遍历数组并展示 1 起始的义项序号、`text`、`badge`、`chips` 为受控原生组件。没有 WebView/HTML/脚本组件。
- `bind` 使用 JSON Pointer。根节点相对于词条根；repeat 的 children 相对于当前数组元素。row/column 不改变作用域。`/senses/0/…` 只在 minItems 保证该索引存在时允许。所绑定属性必须 required；可隐藏值用 nullable 或 []，不绑定可能缺失的属性。
- `hideIfEmpty` 隐藏 null、空串、空数组；组内没有可见项时原生渲染器应折叠容器。不自动向网络查询缺失字段。
- `style` 仅允许已列举语义 token（headline/ipa/grammar/definition/quote/etymology/body/muted）。主题和 Accent 统一解释 token，不接受任意 CSS。
- `summary` 为 History/Vocabulary/Recent 提取字段；列表标题、摘要和词性无需业务层硬编码。`speech` 为 TTS 朗读字段。
- `containers` 控制 Home/Popup 外壳尺寸。Popup 最大 480dp、左右至少 16dp、圆角 28dp、正文可滚动。按钮区保持可达，横屏/大字体可缩小高度并滚动。
- `actions` 是应用白名单事件，完全来自配置而非模型：speak（系统发音）、save（本地收藏）、details（打开同一已保存快照）、close（退出 Popup）、shuffle（仅每日单词模式）。Home 查询结果必须由宿主隐藏 shuffle；模型不得新增/执行 action。
- 最大 128 个静态节点、最大嵌套 12 层；每数组最多 20 项。导入文件总量建议 256 KiB，词条/响应上限 128 KiB，原生实现需同时限制展开节点总量（建议 500）及渲染耗时。

Python `render()` 输出映射后的纯数据树，用于验证字段、作用域和顺序，不是 Android 视觉渲染器；原生布局、空容器折叠、折行、滚动、词源收起/展开、无障碍与事件派发在 M1 实现。

## 编辑、验证与恢复

现在可直接编辑三份文件后从仓库根运行 `verify.py` 和 unittest。删除模板中的词源 column 即可隐藏词源；调整 nodes 顺序即可改变显示；新增字段必须同时更新 Schema、Prompt 的语义说明、example 和 template 绑定。测试 `test_custom_field_without_code_change` 已证明添加自定义字段不改渲染代码可映射成功。

未来 App：内置版本不可变；编辑先复制为 user profile 草稿，依次做 JSON 语法→Schema 子集→模板元 Schema→绑定存在/类型→示例渲染校验，再原子激活新 revision。失败不覆盖 active revision。预览默认用本地样例，不自动付费调用。恢复默认只是重新选择内置 Profile，保留用户副本和旧记录。导出一个 versioned JSON 包，内嵌 Prompt/Schema/Template/Example 和非敏感模型参数，不导出密钥；导入做同样校验并选择本地凭据，不自动发请求。未知 formatVersion 拒绝并提示升级。

`profile.revision` 随三份配置或模型策略变更递增；历史保存该 revision 的内容哈希及 Schema/Template 快照。升级不能用新模板强行解析旧词条。凭据与 Profile 的关联在设备内独立保存，不进入分享文件。

v1.1.0 展示兼容：共享渲染引擎只将 v1.0.0 默认词源长标题映射为「词源」，并隐藏 `/etymology/certainty` 展示节点；数据库中的原始词条、旧模板与用户 Profile 均不重写，自定义标签、字段及展示顺序继续生效。
