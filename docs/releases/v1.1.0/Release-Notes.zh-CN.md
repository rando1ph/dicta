# Dicta 1.1.0

**读到生词，随手读懂。** 本次更新改善词条导航、模型服务配置与日常阅读体验。

- 从搜索、最近查询、历史或生词本进入独立词条详情，返回时恢复原页面、输入、筛选和滚动位置。划词弹窗打开完整详情后，仍可沿原路径返回阅读应用。
- 支持 DeepSeek、OpenAI 和兼容 HTTPS Chat Completions 的服务，可获取并搜索模型列表，也可手动输入 Model ID。支持 JSON mode 或仅提示词 JSON，均执行客户端 Schema 校验；Profile 中非空的模型配置优先于全局模型。
- API Key 继续由 Android Keystore 加密保存。切换服务主机或端口需要新 Key；同服务修改模型或 API 路径时可保留密钥。测试查词使用已保存连接和当前 Profile，且不加入历史。
- 没有匹配缓存时自动获取每日词，同日优先复用缓存，保留「换一换」和失败重试。新生成使用已配置的供应商，可能产生用量费用。
- 改善窄屏、大字体、服务设置与弹窗操作布局。
- 词源正文直接展示，移除单独占行的卡片标题和展开/收起控件。默认 certainty 字段与旧默认免责声明标签在展示时隐藏，保留已存词条数据和 Profile 编辑能力。

## 安装与更新

下载 `Dicta-v1.1.0.apk`，在 **Android 8.0 及以上**设备安装。包名 **dev.randolf.dicta**，版本 **1.1.0**，versionCode **6**。沿用 v1.0.0 正式签名证书，可作为更新安装；保留原应用即可保留其数据与设置。Debug 候选包使用不同证书。

在设置中填写自己的 HTTPS 服务地址、模型和 API Key。兼容性取决于端点与模型，模型列表返回成功不代表每个模型都支持查词。本版本未适配原生 Anthropic、Azure 特有鉴权或 Responses API，不附赠密钥或 AI 额度。界面仍以简体中文为主，默认 Profile 提供中英双语词条；TTS 口音取决于设备安装的 Android 语音引擎。

## 签名、校验与验证范围

沿用既有 **Dicta Release** 签名身份（RSA 3072，APK v2/v3）。

证书 SHA-256：

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256（`Dicta-v1.1.0.apk`，8,518,281 字节）：

```text
24417b983e384683fcaf4c1e38eb2424ca8de8a3b72179b5963c808709440258
```

将 `SHA256SUMS` 下载到 APK 所在目录，执行 `sha256sum -c SHA256SUMS` 校验。

T07 已完成本版本功能测试与用户最终审阅。T08 仅检查正式构建、包名和版本、原证书、APK 签名与对齐、ZIP 完整性、SHA-256 及公开下载资产；发行时没有重复模拟器回归、覆盖安装或真机测试。T07 使用本地 TLS Mock 验证服务协议，本次未新增真实供应商或语音质量验收。

源码：[v1.1.0](https://github.com/rando1ph/dicta/tree/v1.1.0)。[发行核验记录](https://github.com/rando1ph/dicta/blob/main/docs/releases/v1.1.0/REPORT.md) · [中文 README](https://github.com/rando1ph/dicta/blob/v1.1.0/README.zh-CN.md) · [English README](https://github.com/rando1ph/dicta/blob/v1.1.0/README.md)。原创代码采用 Apache-2.0，第三方声明与许可文本继续包含在 APK 和仓库中。
