# Dicta M4：TTS、最终视觉检查与真实网站展示

执行日期：2026-11-10（Asia/Shanghai）。唯一任务 [T05](https://app.notion.com/p/4135421e690b81ec81e9f41d3c208da5)，依据项目主记录、已认可的 Stitch 五屏与 M0–M3 交付。基线 `796be055ed518912c66bb70c0b27960b085c876b`，全程 main，单执行者，无分支/worktree/rebase/force push。

**工程与技术验证已交付；用户听感确认及本批截图的人工视觉审阅仍待进行。T05 保持 In progress，不预先标为 Done。** 最终提交 SHA 与推送结果以 T05 回写为准，避免报告引用自身提交的循环。

## 实现与针对性视觉检查

使用 Android TextToSpeech，声明 TTS service package visibility；不新增敏感权限或依赖。Home 查询、每日词与 Popup 在现有词头区域提供 UK/US 发音和停止入口，Settings 展示实际引擎、选中 Voice、可用口音及试听。模板仍决定是否声明 speak action，发音文本读取既有 `RenderedEntry.speech` 绑定；没有 headline 的自定义模板也能显示发音操作。预览默认不启动语音，不改原始 Profile assets。

语音按精确 en-GB/en-US 匹配，优先已安装的本地 Voice，再考虑需要网络的 Voice；不将 AU 等其他地区标成 UK/US。无法使用所选口音时明确回退到另一可用口音；两者缺失时提示下载系统语音，不能切换的按钮禁用。初始化、引擎拒绝、错误、超时有终态。QUEUE_FLUSH 替换旧请求，当前 utterance ID 拒绝旧回调覆盖；再次点击正在播放的口音停止。切换 Voice、离开词条/页面、Activity pause 停止，composition 销毁 shutdown；10 秒初始化和20秒短词发音超时上限。口音偏好仅存本机应用偏好，不改系统 TTS 设置。

| 检查范围 | 结论/必要修复 |
|---|---|
| Home / History / Vocabulary | 现有卡片、间距、四入口、中文/英文与 IPA 字形可读，保持已认可布局；检查原图08–10 |
| Popup / 真实网页 | BBC 和 Guardian 上居中卡片、网页上下文、滚动和固定页脚正常；保留「打开」及主题色收藏 |
| Light / Dark / Accent | 实查发现 Main 的白色状态栏图标在 Light 下对比不足；用应用主题同步系统栏图标深浅，未改手机系统设置；修复后原图11与 Activity 测试通过 |
| 新发音入口 | 显式使用当前 primary 强调色，沿用现有按钮形态；没有重新设计页面 |
| 长词 / 长释义 / 200% 字体 | 45字符长词与长中英释义在共享 renderer 中换行且无 TextLayout overflow；Popup 200% 字体下正文可滚动，关闭/打开页脚仍可达 |
| Settings / Profile | TTS 实际引擎/Voice 状态可读；Profile 页布局及原默认配置保留，旧词条快照可打开；BYOK Settings 保持 FLAG_SECURE，不采集凭据页截图 |
| 字体 / 图标 / 背景 | 保留 M3 已认可的系统 serif/sans 字体回退、现有图标及 dim 回退，不引入新字体或重做背景 |

截图由 Codex 检查隐私与可读性后上传；以上不代替用户人工视觉审阅。Profile QA 原图包含用户已有的 M3 命名方案，仅留本地，未纳入公开展示候选。

## TTS 真机实证

设备：OnePlus / ONEPLUS A6013 / fajita、Android15/API35、1080×2340、450dpi。身份在每个 ADB helper 导入时核实，没有操作 OnePlus Ace 6T。

系统 secure `tts_default_synth` 初始为空，但系统发现唯一 TTS 服务 Google TTS，TextToSpeech 实际 defaultEngine 为 **com.google.android.tts**。引擎报告30个 UK/US Voice（包含网络/非网络选项，见 [完整记录](evidence/tts-device.json)）；本次实际选择并验证以下两个本地声音。

| 项目 | UK | US |
|---|---|---|
| Locale | en-GB | en-US |
| 实际 Voice | en-GB-language | en-US-language |
| isLanguageAvailable | 1 / LANG_COUNTRY_AVAILABLE | 1 / LANG_COUNTRY_AVAILABLE |
| 初始化 / synthesizeToFile / speak 返回 | SUCCESS / 0 | SUCCESS / 0 |
| 实际回调 | onStart → onDone | onStart → onDone |
| PCM | 24kHz、16bit、单声道 | 24kHz、16bit、单声道 |
| 时长 | 3.695秒 | 3.374秒 |
| 峰值 / RMS（16bit） | 22039 / 3254.25 | 19852 / 3731.11 |
| 非零采样比例 | 94.08% | 92.68% |

原始 WAV、SHA256、阈值验证见 [audio-analysis.json](evidence/audio-analysis.json)。样例为原创短句 “Serendipity. A little discovery, a brighter day.”，没有抽取新闻音频。

- **引擎合成：通过。** 真实 WAV 文件有效且非静音，两种声音均收到 start/done；speak 真实播放也分别返回0并收到 start/done。
- **扬声器播放链路：通过。** 真机 MediaPlayer 播放生成的 GB.wav，实际 routedDevice 为 TYPE_BUILTIN_SPEAKER / 2，媒体未静音、音量5/25，播放完成回调成功。没有改变系统音量或路由。此为设备数字输出与路由实证，没有使用麦克风进行独立声学采集。
- **停止与替换：通过。** 长样例开始后 stop 收到真实 onStop；快速替换后只保留当前请求，完成状态正确。页面离开、pause 和 shutdown 有真机 UI 测试。
- **失败与回退：通过受控测试。** VoicePolicy 的缺少语音/未安装/错误地区/回退由 JVM 测试覆盖；onError 与旧 ID 的拒绝由原生测试注入回调验证，未伪称 Google 引擎在正常场景发生真实错误。没有卸载引擎或删除语音数据来制造故障。
- **用户听感：待确认。** 合并样例依次 UK、0.5秒间隔、US，共约7.57秒；只需听一次确认自然度与口音。文件链接见下方交付索引。

## 真实网站与截图

Chrome 实际访问正常公开文章，不使用 localhost、显式 PROCESS_TEXT 或网页 DOM 注入代替真实菜单：

- [The Guardian / Borrowdale rainforest](https://www.theguardian.com/environment/article/2024/may/22/lake-district-borrowdale-rainforest-declared-national-nature-reserve)：长按标题 rainforest → 系统更多菜单 → Dicta → 发音/收藏 → 关闭返回；收藏持久化。
- [BBC / fossilised forest](https://www.bbc.com/news/science-environment-68500649)：长按 fossilised → 系统更多菜单 → Dicta → US 发音 → 打开相同快照 → Back 返回 Chrome；另验证直接关闭。

两站返回后仍在相同原文位置，使用前后台组件与完整原图对照。Chrome 偶尔只暴露 WebView/工具栏，不提供正文节点；报告没有把缺失的可访问性树伪装成精确 DOM 坐标证明。Guardian 首次有标题边界记录，后续以真实截图核对。见 [chrome.json](evidence/chrome.json)。

最终原图为完整1080×2340真机 PNG，未裁剪、拼接、替换文章或清空网页。保留网站品牌与正常文章/照片上下文，无账号、Key、通知遮挡或开发者说明。网站正常 consent 仅通过页面控件继续；动态广告保持真实页面行为。素材不暗示 BBC/Guardian 合作或背书。原文和照片没有作为独立素材抽出。

[截图与文件完整索引](evidence/screenshots.json) 包含每张文件大小、SHA256、Drive链接、用途和人工审阅状态。原图上传 [Google Drive /ChatGPT/Dicta](https://drive.google.com/drive/folders/1z5SKVJyuqLFFs87nC2URwMU6mQ82pPKl)，供 README、个人网站和 F-Droid 详情后续挑选，未进行平台发布。

## 测试、修复与安全

最终 **49/49 相关 JVM 测试、9/9 真机测试通过；lint 0错误/13建议**。JVM 仅运行 speech、query、profile 相关套件；真机为 M4SpeechTest×2、M4SpeechUiTest×3、M4AppearanceTest×1、200% Popup×1、前后台快照×1、历史/收藏保护×1。未重复无关凭据替换、M0全套或付费API回归。

- [构建+JVM+lint](evidence/build-verified.txt)、[JVM计数](evidence/jvm-summary.json)、[lint分类](evidence/lint-summary.json)。13条建议为固定依赖更新、原有 manifest/备份建议、KAPT与SharedPreferences KTX建议，无运行错误。
- 原 Profile speech 绑定测试先真实失败：期望 Configured pronunciation.，实际 Display label；改为配置绑定、补无headline入口后通过。失败证据 [profile-speech-red.txt](evidence/profile-speech-red.txt)，最终 M4SpeechUiTest/M4AppearanceTest 日志单列。
- 一次测试源码使用了 Kotlin 不支持的局部 lateinit 引用，修正测试读取方式；系统栏修复初次 lint 要求 LocalActivity，按诊断采用正确 API 后最终构建和 lint 通过。未屏蔽检查。
- [安全报告](evidence/security.json)：两个 APK 解压成员及应用日志不含真实开发密钥，原默认 Profile assets 逐字节一致，生产 APK 无 FakeProvider/安全录入测试类；无悬浮窗、无障碍、联系人、存储读取等新增权限。
- 升级前后 provider.enc 密文 SHA256 相同，未重新录入/替换/清除 BYOK。所有升级使用 install -r，未卸载主应用、清空数据库或手机数据。测试只创建并清理自有 UUID 记录/测试库；真实网站查询自然新增历史与 rainforest 收藏。

复现方法及边界见 [tools/m4/README.md](../../tools/m4/README.md)。收尾仅移除测试 APK、自建 TTS 测试文件和 UI dump，保留主 App、凭据、历史、收藏、每日缓存和全部用户 Profile；本阶段未启动 localhost 服务器或 ADB reverse。

## APK 与剩余人工验收

- 已安装 Debug APK：`0.4.0-m4` / code4，临时包 `dev.dicta.prototype`。
- 构建路径：`app/build/outputs/apk/debug/app-debug.apk`；本地副本：`artifacts/m4/Dicta-M4-debug.apk`。
- 大小：12,561,861 bytes；SHA256：`fd10c9de0f99e56836d062130b46ffea4cec259912f985f679de68a9111f1011`。
- [APK 下载](https://drive.google.com/file/d/1Geqg84NovZlfm9ut0kf6k0T1ui3mfnzZ/view?usp=drivesdk)。
- [一次试听：UK → US](https://drive.google.com/file/d/1S7HekrYAaiPGba-Hpy7V5A235GgiczR1/view?usp=drivesdk)。

只在API35/该设备与当前Google引擎实测，minSdk26为编译下限。正式包名、许可证、Release签名和发布决策保持开放。技术交付不代表用户已认可音色/口音或本批最终截图；这些状态独立记录，未提前标为通过。

官方依据：[TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)、[UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)、[TTS Engine/queries](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine)。
