# Dicta 1.2.0

**读到生词，随手读懂。** 本次更新改善跨阅读应用的划词弹窗，保留既有 Chrome 体验。

- Readest 使用默认词典或指定 Dicta 时，弹窗保留原书页背景；Dicta 主界面已在后台时也可正常调用。
- 旧弹窗或完整词条留在后台时，再次划词显示最新选择的单词。
- 从弹窗打开完整词条复用同一份结果，无须额外查词；返回或关闭后回到阅读应用，不替换原选中文字。
- 保留 BYOK 设置、查询历史、生词本、每日词、Profile 与 TTS；Dicta Debug 继续独立安装。

## 安装与更新

下载 `Dicta-v1.2.0.apk`，适用于 **Android 8.0 及以上**。包名 `dev.randolf.dicta`，版本 **1.2.0**，versionCode **7**。沿用 v1.0.0、v1.1.0 正式签名证书，直接覆盖安装原正式版即可保留设置与已存词条，请保留原应用数据。仍需配置自己的服务地址、模型和 API Key。

## 验证范围与兼容边界

T09 在 **OnePlus 6T / LineageOS 22.2 / Android 15** 上完成 Readest 与 Chrome 验证，并通过 T10 人工审阅。集成范围为 Android 标准 `ACTION_PROCESS_TEXT` / `text/plain`；其他阅读器、私有词典协议和厂商系统的行为可能不同，ColorOS 17 未实测。私有审阅中的 Mock 释义用于验证交互，不代表词条语义质量。

正式构建、**81 项 Release 单元测试**、**14 项签名脚本回归**及 Release Lint 均通过，Lint 为 **0 错误、13 项既有警告**。本轮在全新隔离 AOSP API36 模拟器中，用公开 v1.1.0 APK 覆盖升级到最终签名 v1.2.0 APK，冷启动、BYOK 密文保留及 Keystore 解密、历史、生词本、自定义 Profile 与每日词数据验证通过。使用非敏感样例数据，无真实供应商请求。T11 未操作用户手机及其数据，也未重复大范围兼容性矩阵。

## 签名与完整性

沿用 **Dicta Release** 身份，RSA 3072，APK v2/v3 签名验证通过。APK **8,518,281 字节**。

证书 SHA-256：

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256：

```text
b159e1fd0d9ebf7c9469e506679cbcb298bd59b1d3046ea3616f6c5aea2ea34b
```

将 `SHA256SUMS` 下载到 APK 所在目录，执行 `sha256sum -c SHA256SUMS` 校验。

[源码](https://github.com/rando1ph/dicta/tree/v1.2.0) · [发行核验记录](https://github.com/rando1ph/dicta/blob/main/docs/releases/v1.2.0/REPORT.md) · [English](https://github.com/rando1ph/dicta/blob/v1.2.0/docs/releases/v1.2.0/Release-Notes.en.md)。原创代码继续采用 Apache-2.0，第三方声明保留。
