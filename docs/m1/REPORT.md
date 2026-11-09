# Dicta M1 原生基础交付

执行日期：2026-11-10，Asia/Shanghai。唯一任务 [DICTA-T02](https://app.notion.com/p/4135421e690b813da6f1ede4edda02e7)。基线 `cdf582b1bec644ff06c09282ab70d4449de53003`，仅使用 main；沿用 M0 默认 Profile、实际生成的三份非敏感样例及已批准五屏定稿。

## 范围

单 `:app` Kotlin/Compose 原型，临时包名 `dev.dicta.prototype`。Home、History、Vocabulary、Settings 导航，共享受控模板词条渲染器，透明 PROCESS_TEXT Activity + 居中 Compose Dialog。Fake Provider 只加载 luminous、ephemeral、qzxvplmnonce；任意其他词明确提示无演示样例。`:slow`、`:error`、`:timeout` 为显式演示场景。APK 无 INTERNET 权限，不读取或携带开发 API Key。

历史只保留本次进程内最多100条不可变快照；打开历史/Popup详细释义复用快照。完整持久化、收藏管理、每日词生成、配置编辑与正式 TTS 分别留待 M3/M4，真实 BYOK 留待 M2。界面有明确原型说明。

## 构建与运行

版本锁定于 `gradle/libs.versions.toml`：AGP 8.13.2、Gradle 8.13、Kotlin/Compose compiler 2.2.21、Compose BOM 2025.10.01、Activity 1.11.0、Lifecycle 2.9.4、kotlinx.serialization 1.8.1、coroutines 1.10.2。JDK21，Java target17；compile/target SDK36，build tools35.0.0，minSdk26。

使用已验证可取得的稳定组合，未采用动态版本。依据：[AGP8.13兼容说明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Compose BOM](https://developer.android.com/develop/ui/compose/bom)、[Compose compiler配置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。本机隔离安装 SDK36，复用已有 JDK21；不变更全局 Java、代理或系统设置。

```bash
# 本机直接运行；其他机器先安装 JDK17/21、Android SDK platform36/build-tools35.0.0
# 必要时显式设置 DICTA_JAVA_HOME、DICTA_ANDROID_HOME
./tools/m1/build.sh
# APK: app/build/outputs/apk/debug/app-debug.apk
# 仪器测试（仅接入经过身份核实的测试机）
./tools/m1/build.sh :app:connectedDebugAndroidTest
```

Wrapper 固定官方发行包 SHA-256。assets 在构建时直接从 `profiles/default/` 与 `docs/m0/examples/` 同步，无人工副本；修改 Schema/Template 并运行测试即可验证字段/顺序变化。M1 未开放 App 内配置编辑/导入，属于 M3。

## 真实 Chrome 路径复现

先检查 `adb devices -l` 与 OnePlus/A6013/fajita 身份，再安装自己的 debug APK。不得清除设备或 Chrome 数据。

```bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/m1
# 另一个终端；<serial> 仅取刚核实的唯一设备，不记录进交付
adb -s <serial> reverse tcp:8765 tcp:8765
adb -s <serial> shell am start -a android.intent.action.VIEW \
  -d http://127.0.0.1:8765/host.html -p com.android.chrome
```

在受控页长按 ephemeral → 更多文字选择操作 → Dicta；验证浮卡和关闭/系统返回/外部点按。`tools/m1/device.py` 每次操作前验证唯一设备身份，`smoke.py` 提供真实 UI 定位及证据辅助；显式 Intent 诊断与 Chrome 菜单验证分开记录。测试完删除本次 `tcp:8765` reverse，不动其他映射。

## 验证结果

工程构建、37项 JVM 测试和10项真机仪器测试通过。实际命令与结果见 `evidence/build-final.txt`（clean重建）、`build-gutter-fix.txt`（最终交互修复）、`instrumentation.txt`、三份绿色测试XML。lint为0错误/8警告，包含6项版本升级提示、重复label和legacy backup规则建议；版本显式锁定，allowBackup=false，Android12+排除规则已写明。只对实际运行的设备与版本作兼容声明：OnePlus 6T API35；minSdk26 为编译下限，API26 尚未实测，不能宣称完整 API26–36 兼容。

## 视觉审阅边界

原始真机截图存放 [Google Drive /ChatGPT/Dicta](https://drive.google.com/drive/folders/1z5SKVJyuqLFFs87nC2URwMU6mQ82pPKl)，[截图索引](evidence/screenshots.json)记录文件哈希和链接。共8张原始PNG（7张最终场景和1张受控阅读页），已上传并在本地逐张检查敏感信息。视觉状态：**待用户人工审阅**。开发自检和截图上传不等于用户验收。

M1 可见差异：采用系统 serif/sans 字体替代 Newsreader/Roboto Flex；Chrome 背景使用 dim，不实现跨窗口 blur；原型说明、演示样例标签及未实现操作提示使布局比定稿更密；历史为进程内列表，Vocabulary为空状态，设置无可输入密钥。正式字体许可/字形、TTS、全套主题/Accent精修及人工视觉验收留待M4。

最终真机操作脚本另有 **17/17通过**：显式Intent诊断、Chrome真实长按→更多→Dicta→关闭返回、系统Back、侧边空白关闭、不同样例、无样例输入、空/过长输入、失败/超时、加载取消后无迟到弹窗、可编辑来源不回写、Home实际输入和三页导航。Chrome返回前后ephemeral文本边界均为 `[75,681][1004,796]`，原页面位置保持。详细证据：[device-tests.json](evidence/device-tests.json)、[device-smoke.txt](evidence/device-smoke.txt)。M0原有30项测试亦通过，全部37份M0/设计/Profile源文件未改变。测试临时ADB reverse及UI dump已移除。

最终APK：`app/build/outputs/apk/debug/app-debug.apk`；交付副本 `artifacts/m1/Dicta-M1-debug.apk`（本地忽略，不提交二进制）。11,435,844 bytes，SHA256 `a15b8e32ae5814e90af77ab1da9a8ef53f791d3f8d19026a521bfbbd6607868d`。已安装在授权OnePlus6T上。

## 截图入口（全部待人工审阅）

| 场景 | 原始真机截图 |
|---|---|
| M1-00-controlled-reader.png | [Drive](https://drive.google.com/file/d/1CnExYB887frS7ttjL0XSg8XorwVYtzfB/view) |
| M1-final-01-chrome-selection-menu.png | [Drive](https://drive.google.com/file/d/172ooCSM4_URM_nyURwzP-ZIGFH-Ehfbu/view?usp=drivesdk) |
| M1-final-02-chrome-popup.png | [Drive](https://drive.google.com/file/d/1610AmAqVJQSboRBfz4OUQj0i9qkPRjgy/view?usp=drivesdk) |
| M1-final-03-return-to-chrome.png | [Drive](https://drive.google.com/file/d/1ri7FdiQrJyOLtLTnhXB-p_Pk9JHN5chB/view?usp=drivesdk) |
| M1-final-04-home-query.png | [Drive](https://drive.google.com/file/d/1hNu_7MDV5VHIUrnLfeO6CB912WZKS4q2/view?usp=drivesdk) |
| M1-final-05-history.png | [Drive](https://drive.google.com/file/d/1aKEtXB0XsG-GQuiic1uXm8PKni9GNFeK/view?usp=drivesdk) |
| M1-final-06-vocabulary.png | [Drive](https://drive.google.com/file/d/17fjoIR2AXbJD-UNXCisi8k86Ts-ZL2xZ/view?usp=drivesdk) |
| M1-final-07-settings.png | [Drive](https://drive.google.com/file/d/11MFMaMxDL7N4C04QHCzYEKIzZoOQoUfQ/view?usp=drivesdk) |

## 调查与修正

- 首次 Kotlin 校验回归暴露 nullable enum、数字1/1.0枚举语义与Unicode长度问题，先保留失败结果再修正；自定义字段测试亦纠正了自身顺序断言。最终25项Profile测试+12项查询/Provider测试通过。不是通用完整Draft7实现，仅接受M0声明的受控子集。
- 首次安装被Play Protect的可选上传询问阻塞；通过实际UI选择“不发送”，随后安装成功；未改全局安全设置。后续使用 `adb install --no-streaming -r -t` 安装自己的debug/test APK。
- 真机发现原浮卡接近全高，改成安全窗口可用高度78%，保留上下Chrome上下文、卡内滚动与固定操作区。
- 真实侧边点击发现Dialog透明padding仍在窗口内部，原生outside-window事件不会触发；增加透明边距/圆角点击检测，卡片内事件不拦截。左右边距关闭及卡内不关闭有新增真机回归。
- Espresso默认pressBack将退出最后Activity判为异常，改用pressBackUnconditionally，再断言Activity已销毁/RESULT_CANCELED；10项仪器测试全部通过。

## 安全与交接

APK无INTERNET/悬浮窗/无障碍/个人数据读取权限，不访问开发密钥。`tools/m1/check_apk.py`扫描解压APK成员并比对9份规范assets，确认真实密钥字节不存在、Profile/样例与M0完全一致；Git文件与历史另用M0扫描器检查。未修改原始设计与M0交付，未修改手机系统字体/旋转/代理、账户或Chrome历史。仪器测试只在本App内请求方向与局部2倍字体。

下一阶段可以按M2接入BYOK网络核心；不因M1使用Fake Provider而误认为真实供应商功能完成。正式包名、开源许可证与发布日期仍待决定。Git最终SHA以T02回写为准，避免报告嵌入自身提交哈希的循环。
