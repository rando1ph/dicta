# T09 真机截图索引

15 张原始 PNG，1080 × 2340，保存在指定 Google Drive 私有目录。全部已按文件名、MIME、父目录、大小核对，并经认证下载逐字节比对通过。书籍由用户明确授权改用《Project Hail Mary》；Chapter 1，第 9/761 页，测试未翻页。

**Mock 说明：** 实际 Android 选词、宿主背景、返回、连续词和完整词条均为真机操作；词条内容将所选词替换到默认 ephemeral 样例，音标/释义/例句均为测试夹具文本，不代表这些词的真实释义或 Provider 质量。截图仅供私有人工审阅，未公开嵌入 GitHub。

| 文件 | 场景 | Mock 词条 | 原图 |
| --- | --- | --- | --- |
| T09-01-before-new-task-over-dicta.png | 修复前受控NEW_TASK调用，Popup误显示Dicta主界面背景；无API Key降级 | 否 | [Drive](https://drive.google.com/file/d/1b8j1SX517ePQR-MhmHRoKYcBMqFVsGa1/view?usp=drivesdk) |
| T09-02-library-before.png | 入场书库两本，没有Artemis；随后用户授权Project Hail Mary | 否 | [Drive](https://drive.google.com/file/d/1wP_Of9wRkjnh0kGa8ounT-DugmgzfkC7/view?usp=drivesdk) |
| T09-03-readest-position-before.png | Readest Project Hail Mary 起始 Chapter 1，第9/761页 | 否 | [Drive](https://drive.google.com/file/d/1H-cVFejUmwRDjSp-DD5c4yShPGzfgz64/view?usp=drivesdk) |
| T09-04-readest-default-chooser.png | Readest Default真实选词→系统选择Dicta Debug | 否 | [Drive](https://drive.google.com/file/d/15rBMXdTTu2vpAdURw_OEKT4FqpajMDcy/view?usp=drivesdk) |
| T09-05-readest-default-popup.png | Default feminine Popup保留书页背景 | 是 | [Drive](https://drive.google.com/file/d/1nHqujyKRHKyJfbFYoEs3Ays7PwqJ2WXS/view?usp=drivesdk) |
| T09-06-readest-debug-selected.png | Readest Lookup App明确选择Dicta Debug | 否 | [Drive](https://drive.google.com/file/d/12u0TiJq39sBepI1i2fuYslr-B_0h4h64/view?usp=drivesdk) |
| T09-07-readest-explicit-popup.png | 指定调用pronunciation Popup，已收藏 | 是 | [Drive](https://drive.google.com/file/d/1X4Bati2_5VATTjtYnRcW3n-VIR_3zWrJ/view?usp=drivesdk) |
| T09-08-readest-full-entry.png | 完整词条同receiver、无额外请求；系统栏视觉 | 是 | [Drive](https://drive.google.com/file/d/1WmlMpc4Hr6UenivklKu1tOZBEp6jXtTb/view?usp=drivesdk) |
| T09-09-readest-reentry-popup.png | 后台完整词条→Readest再次查询irritated | 是 | [Drive](https://drive.google.com/file/d/1nrUInD0kUypPs9UmE8SuSgZagZqM2fxS/view?usp=drivesdk) |
| T09-10-readest-return.png | 关闭后同一Chapter 1，第9/761页 | 否 | [Drive](https://drive.google.com/file/d/1msLdqw-TQdFwRIuqnTpsB99P3klyVTAl/view?usp=drivesdk) |
| T09-11-chrome-selection-menu.png | Chrome真实选词系统菜单并存正式/Debug | 否 | [Drive](https://drive.google.com/file/d/1RCXbx0doi4kEGb7VDAAAJ8_1K2Bw7z3T/view?usp=drivesdk) |
| T09-12-chrome-popup.png | Chrome documentation Popup，原网页可见 | 是 | [Drive](https://drive.google.com/file/d/1oCAnTaQGdioVkh2MjdZ5Z3r-9Pooq9vH/view?usp=drivesdk) |
| T09-13-chrome-return.png | Chrome完整词条Back返回原网页位置 | 否 | [Drive](https://drive.google.com/file/d/1VNIyzpOI-c69kEmXTBV7wN2DX6TRwYja/view?usp=drivesdk) |
| T09-14-readest-restored-dicta.png | 收尾Lookup App已恢复正式Dicta | 否 | [Drive](https://drive.google.com/file/d/1oX4SDdDHiPLLfGZnTncuH54jzTU_ZuKh/view?usp=drivesdk) |
| T09-15-library-after.png | 书库仍两本，Project Hail Mary1% / The Martian9% | 否 | [Drive](https://drive.google.com/file/d/1xsSckYaYZr0Olmmz1LkZCkbwHlSALXs8/view?usp=drivesdk) |

文件字节数、SHA-256 和截图尺寸见 [screenshots.json](screenshots.json)。T09 完成功能交付，视觉与兼容性人工审阅留给 T10；本轮未启动 T10/T11。
