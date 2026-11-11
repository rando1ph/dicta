# T07 · v1.1.0 截图审阅索引

[打开 Google Drive 原始截图目录](https://drive.google.com/drive/folders/1gFzRZcU8--qtCNtvcei3kMU8SbpCampT)

全部为实际运行的原始 PNG，由 Google 官方 Android CLI 捕获，逐张视觉检查；未裁剪、重编码或拼接。设备为 codex_api36_aosp / **emulator-5554**，Android 16 / API36、420dpi。所有模型请求和模型列表均来自本地 TLS Mock，使用固定词条夹具与测试凭据，没有真实 API Key 或付费查询。

01–20 为首轮候选历史，不能代表收尾修订后的行为；21 起为最新修订截图。13–16 以及标记 320dp / 200% 的修订图为 840×1920，其余为 1080×2400 / 411dp / 字体 100%。AOSP 没有安装 TTS 引擎，截图中的发音禁用与回退提示为真实状态。17–20 使用 AOSP WebView Browser Tester 和仓库内的受控阅读页，未将其表述为 Chrome 或真实新闻网站。

**人工视觉与候选版本验收均待用户审阅。** 每张图的大小、SHA-256 和 Drive 元数据核验见 [机器索引](https://github.com/rando1ph/dicta/blob/main/docs/t07/evidence/screenshots.json)。

## 最新收尾修订 · 2026-11-11

| 文件 | 场景 | 屏宽 / 字体 / 主题 | 审阅 |
| --- | --- | --- | --- |
| [T07-21-home-auto-dark.png](https://drive.google.com/file/d/1gliYh6GwoZTWlvqLPjvdLWeQlGOAl7JH/view?usp=drivesdk) | 首次进入 Search 自动生成当天每日词；默认词源收起 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-22-daily-etymology.png](https://drive.google.com/file/d/1Jn5CjbcZUju32kaYkbZCS6EaWxjNvuJF/view?usp=drivesdk) | 每日词展开词源，仅保留摘要/详情；收藏与换一换同排 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-23-history-v100-etymology.png](https://drive.google.com/file/d/1j7gm4y6-5FqBK8SuT5cU1plA3J1pZrh2/view?usp=drivesdk) | History 打开实际 v1.0.0 Template 快照；词源无旧免责声明或自评 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-24-new-provider-key-required.png](https://drive.google.com/file/d/1LgZI6M1vETD_QHx2-nTxZJ8j8LiljRBI/view?usp=drivesdk) | 切换 OpenAI 后 API Key 空白，新服务要求新密钥；原配置尚未覆盖 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-25-detail-etymology.png](https://drive.google.com/file/d/1jyWjpyyrXu0wnogWPe1PcQV0jze_04Mu/view?usp=drivesdk) | Search 新查询详情；默认词源仅显示摘要和详情 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-26-popup-etymology.png](https://drive.google.com/file/d/1nFCrcNpuhltiYhx4z8LKtHakA06U0Ocb/view?usp=drivesdk) | PROCESS_TEXT 测试 Intent → Popup，词源展开与固定页脚 | 411dp · 100% · Dark / Accent 1 | 待审 |
| [T07-27-narrow-large-etymology.png](https://drive.google.com/file/d/1J34aKyLocTI28-Ox12HJuLxcp7HP2a56/view?usp=drivesdk) | 320dp / 200% 字体：词源展开换行与每日词按钮可达 | 320dp · 200% · Dark / Accent 1 | 待审 |
| [T07-28-light-etymology.png](https://drive.google.com/file/d/1ccMylPQUWHQ79ZDshThkXtOG8mDXyvrL/view?usp=drivesdk) | Light / Accent 3：每日词词源展开与同排操作 | 411dp · 100% · Light / Accent 3 | 待审 |

## 首轮候选历史

| 文件 | 场景 | 屏宽 / 字体 / 主题 | 审阅 |
| --- | --- | --- | --- |
| [T07-01-home-dark.png](https://drive.google.com/file/d/1w9lcMjQ9OEJsV_469k2_h2YMeJmCJP2G/view?usp=drivesdk) | Search 首页、每日词缓存 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-02-daily-actions.png](https://drive.google.com/file/d/1T6IWnMyDdtUUM3Xyyx9Z__Rv7ws8evPh/view?usp=drivesdk) | 每日词收藏 / 换一换同排 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-03-search-detail.png](https://drive.google.com/file/d/1TwOTB20nHfKHfJkTf_MH73prgvmtcF4C/view?usp=drivesdk) | Search 主动查询 → 独立详情 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-04-history-filter.png](https://drive.google.com/file/d/1QNQWy25vXkMCWZXw8F99jY0tj4f3C7WH/view?usp=drivesdk) | History 搜索筛选 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-05-history-detail.png](https://drive.google.com/file/d/1xTO3eYJimf0TvYPURIwjpA49C0Fu67Nb/view?usp=drivesdk) | History → 本地快照详情 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-06-history-gesture-return.png](https://drive.google.com/file/d/1hr8hrgiS2IBEKV73WtQ_hR83aa3FUAuv/view?usp=drivesdk) | 详情边缘返回手势 → History，保留筛选 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-07-vocabulary.png](https://drive.google.com/file/d/1Gbg7J5jNMVesRSWOEytG4AeaIFdQOCwQ/view?usp=drivesdk) | Vocabulary 筛选与收藏 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-08-vocabulary-detail.png](https://drive.google.com/file/d/1HD7AvBMRqdUYztAACCTccxFIU3WT4Z7P/view?usp=drivesdk) | Vocabulary → 本地快照详情 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-09-provider-key-saved.png](https://drive.google.com/file/d/1I0DuAhl1-7GslN_U38l4X61D1p2GWjVl/view?usp=drivesdk) | 模型服务、已保存 Key 固定遮蔽 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-10-model-discovery-picker.png](https://drive.google.com/file/d/1SMs3wsUebPdhYA5xikLMh1Js8aw8Mg-5/view?usp=drivesdk) | 自动模型发现、可搜索选择器 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-11-provider-actions.png](https://drive.google.com/file/d/1H-BeBNZhY2kuuTzXIW0n3eC0GGvRMmzQ/view?usp=drivesdk) | 生效模型、保存主操作、测试用量提示 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-12-home-light-accent.png](https://drive.google.com/file/d/1gNi5V4hLG0lXct9lsAReIxg5AL3pf2cQ/view?usp=drivesdk) | Light 与 Accent 3 对照 | 411dp · 100% · Light / Accent 3 | 首轮历史 |
| [T07-13-narrow-large-home.png](https://drive.google.com/file/d/1s3nLZ7bhUl_Tv1r8xf033UhfxyuHp5Vr/view?usp=drivesdk) | 320dp / 200% 字体，首页与导航降级 | 320dp · 200% · Dark / Accent 1 | 首轮历史 |
| [T07-14-narrow-large-actions.png](https://drive.google.com/file/d/1EZLYKitJ4lFKYmTq27bSxz-f-Jd_TDrr/view?usp=drivesdk) | 320dp / 200% 字体，每日词操作与词源换行 | 320dp · 200% · Dark / Accent 1 | 首轮历史 |
| [T07-15-narrow-large-settings.png](https://drive.google.com/file/d/1W4ApWCBbS2kahU4qhV0D6pbas-80L3DV/view?usp=drivesdk) | 320dp / 200% 字体，保存 / 测试可达 | 320dp · 200% · Dark / Accent 1 | 首轮历史 |
| [T07-16-narrow-large-detail.png](https://drive.google.com/file/d/1t31fGQwylPY0ZzOqQ11XrCc9tDNoj_ZN/view?usp=drivesdk) | 320dp / 200% 字体，详情返回与正文滚动 | 320dp · 200% · Dark / Accent 1 | 首轮历史 |
| [T07-17-browser-selection.png](https://drive.google.com/file/d/1oS1uxqnNG_4_BnCfMU-umz5-GvXhaBYY/view?usp=drivesdk) | 外部浏览器真实长按 → 系统 Dicta 菜单 | 411dp · 100% · Browser / light page | 首轮历史 |
| [T07-18-browser-popup.png](https://drive.google.com/file/d/1qt-NVA5o5YFdAmieOOmuIMdk3RU2FclT/view?usp=drivesdk) | 系统菜单 → 原生 Popup，固定页脚 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-19-popup-full-detail.png](https://drive.google.com/file/d/1z7C42JcoAu070H_trQ-Q7MNhlckm9H9y/view?usp=drivesdk) | Popup 打开 → 完整详情，隐藏底部导航 | 411dp · 100% · Dark / Accent 1 | 首轮历史 |
| [T07-20-return-to-browser.png](https://drive.google.com/file/d/1WVE8DeHI1ieP3VmXiW4fE80CnrT5VRPM/view?usp=drivesdk) | 完整详情边缘返回 → 浏览器同一阅读位置 | 411dp · 100% · Browser / light page | 首轮历史 |
