# Dicta 1.0.0

**读到生词，随手读懂。** Dicta 首个 Android 公开正式版本现已发布，使用你自己的 AI 服务与 API Key，让查词自然融入阅读。

- 在支持的应用中选中单词，通过 Dicta 原生弹窗查看释义、听发音、收藏或打开完整词条。
- 首页主动查词，发现每日单词；已保存词条可本地重看，无须重复请求 AI。
- 历史与生词本独立管理，支持搜索、选择与删除。
- 使用 Android TTS 播放英美发音，可用口音取决于设备安装的语音引擎。
- 复制、编辑、校验、预览、切换及导入导出 Profiles，自定义词条内容和展示方式。
- 支持深色、浅色主题及强调色选择。

## 安装与配置

下载本 Release 的 `Dicta-v1.0.0.apk`，在 Android 8.0 及以上设备安装。打开 Settings，填写自己的 HTTPS 服务地址、模型和 API Key。DeepSeek 已实测，其他端点兼容性因服务而异。AI 用量费用由服务供应商收取，应用不附赠密钥或额度。

当前界面以简体中文为主，默认 Profile 提供中英双语词条。本次真机验证使用运行 Android 15 的 OnePlus 6T，未声称已在其他 Android 版本真机验证。

正式包名为 **dev.randolf.dicta**，版本 **1.0.0**，versionCode **5**。旧 `dev.dicta.prototype` 测试包可独立共存，其用户数据与 BYOK 配置保留；正式应用使用自己的初始设置。

## 签名、校验与源码

沿用既有 **Dicta Release** 独立签名身份（RSA 3072，APK v2/v3 签名）。

证书 SHA-256：

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256（`Dicta-v1.0.0.apk`）：

```text
557c8c3c055ad53cf4d6bdb29f450564f31bc4b3ea000163f240000c7897c1f7
```

同时提供 `SHA256SUMS` 校验文件。将它与 APK 下载到同一目录，执行 `sha256sum -c SHA256SUMS` 即可校验。

源码：[v1.0.0](https://github.com/rando1ph/dicta/tree/v1.0.0)。原创代码采用 Apache-2.0，第三方声明与许可文本已包含在 APK 和仓库中。[中文 README](https://github.com/rando1ph/dicta/blob/v1.0.0/README.zh-CN.md) 与 [English README](https://github.com/rando1ph/dicta/blob/v1.0.0/README.md) 提供已审阅真机截图及使用说明。
