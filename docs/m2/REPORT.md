# Dicta M2 真实 BYOK 查询交付

执行日期：2026-11-10，Asia/Shanghai。任务 T03：4135421e690b81469737e5664d6c933f。基线 `51ebf4530c3f70354bec13b846d061d749618dd2`，全程 main；未创建分支或 worktree。沿用 M1 已获认可的 Compose 布局、色彩、共享模板 renderer 和 M0 原始 Profile。

## 实现

- Home 与 Chrome PROCESS_TEXT Popup 共同调用 `DictaRepository → DeepSeekProvider → ProfileEngine`。DeepSeek Chat Completions，默认 `https://api.deepseek.com` / `deepseek-flash`，`json_object`、非思考、2200 tokens；发送规范 Prompt 中的 Schema 和示例，查询以 JSON 数据封装。
- Settings 支持 HTTPS Endpoint、Model、API Key 填写、替换、保存、清除和测试连接。空 Key 输入保留已存密钥；清除配置删除所有凭据配置。测试使用已保存配置生成 serendipity，明确提示少量 API 用量；成功必须同时通过词条 Schema 校验。Settings 使用 FLAG_SECURE，密钥输入遮罩、不进入 saved state，离开页面取消连接测试。
- AES-GCM 使用 Android Keystore 中不可导出的 AES key，配置只以 IV+认证密文写入 noBackupFilesDir 的 AtomicFile；备份/迁移关闭。设备 Key 失效或配置损坏提示重新保存，不静默显示样例。开发 Key 经仅测试 APK 内的临时 RSA-OAEP 公钥加密通道录入，再由 App 加密存储；没有明文 ADB 参数、剪贴板或手机明文文件。
- connect/read/total 分别 10/40/60 秒；响应限 128 KiB，严格 JSON（含重复键拒绝）及 M0 Schema 受控子集校验。不接纳空白、截断、错误格式或不符 Schema 的词条，不保存失败结果。401/403、402、429、5xx、网络、超时有安全用户反馈；不输出响应体、堆栈或密钥。
- 只允许手动重试；取消/关闭传播到 OkHttp Call.cancel。快速查询取消旧请求，generation 拒绝迟到结果。禁用重定向，避免凭据转发。额外阻止 OkHttp 对 503 + Retry-After:0 的内建重发。
- 删除 M1 样例、阶段说明、假错误入口与自动 luminous 查询；无凭据给出自然设置提示。FakeProvider 移到 JVM 测试源码，非必要演示 fixtures 不再打包；保留规范 Prompt 所需的默认 example。原有未上线收藏保留禁用态，TTS 暂不可用。没有扩展 M3/M4。

## 构建与测试方法

```bash
./tools/m1/build.sh  # assembleDebug + JVM tests + lintDebug
./tools/m1/build.sh :app:assembleDebugAndroidTest
# 首先用 tools/m1/device.py 核实唯一 OnePlus A6013 / fajita。
# 使用 --no-streaming -r -t 安装自己的 app/debug 与 androidTest/debug APK。
python3 tools/m2/provision.py  # 如需首次安全配置；只读取仓库外指定 Key
python3 tools/m2/instrument.py # 前台联网测试；保留包和已存配置
.venv/bin/python -m unittest discover -s tools/m0 -v
python3 tools/m2/check_delivery.py
python3 tools/m0/check_secrets.py
```

不要直接依赖 Gradle connectedDebugAndroidTest 保留 App：本机 UTP 会在结束时卸载被测包，丢弃开发测试配置。交付使用直接 am instrument，并在需要实际联网的测试中打开本 App 前台；遵守手机现有后台网络限制，不更改网络/代理、账户或系统设置。

## 验证与调查

最终 **48/48 JVM、16/16 真机仪器、30/30 M0 回归**通过；安全录入测试另一次独立执行通过。lint **0错误/11警告**，为9项固定版本更新提示、原有重复label与备份规则建议，无新增运行错误。完整输出、JUnit XML及计数见 `evidence/`。

| 场景 | 实证结果 |
|---|---|
| Home 实际输入 serendipity | 实时中英释义、音标、例句；无 Fake 标签，截图01 |
| Chrome 实际长按 petrichor → 更多 → Dicta | 实时释义与共享模板渲染成功，截图02/03；没有以显式 Intent 代替真实菜单 |
| Popup 关闭返回 | Chrome仍在同一位置，文本边界前后均 `[75,681][1004,796]`；截图04和 `chrome-checkpoint.json` |
| 再次实际选词后立即 Back | 回到 Chrome，等待后没有迟到 Popup；`device-tests.json` |
| App 强制结束进程后重新打开 | 无需重新输入凭据，Home 实际输入 resilience 得到实时释义，截图05 |
| Settings 编辑/保存/替换/清除/连接测试 | 真机自动化通过，空输入保留原 Key，测试连接经过 Schema 校验，安全标记生效并在离开页面后释放 |
| 无 Key / 错 Key | 不发送无凭据请求；真实 DeepSeek 错 Key 返回 AUTH，Popup 无 Key 显示可理解错误并可关闭，不保存失败记录 |
| 429 / 503 / 断网 / 超时 / 非法 JSON / Schema不符 | 真机受控 TLS 服务器/关闭回环端口覆盖，全部有终态；生产端点使用系统证书信任，不关闭 TLS 校验 |
| 网络取消 / 快速重复 / 迟到响应 | 实际HTTP取消测试和最新请求优先测试通过；手动重试仅发一次，503 Retry-After:0不触发隐式重发 |
| M1 原生回归 | 旋转、重建、前后台、详细释义复用、可编辑源不回写、无效输入/Intent、Back、左右边距关闭、2倍字体及滚动/固定页脚均通过 |

三种实际输入均不在 M1 的两个已知词 fixtures 中。原始设计、M0交付与默认 Profile 保持不变。

调查修复记录：

1. 初次使用 OkHttp 旧版 MediaType.get 在 Kotlin 中被拒绝，改为官方扩展 API；构建通过。
2. 首轮测试无前台 Activity，被手机现有 APP_BACKGROUND 策略限制网络；shell 连接正常而测试 DNS/socket 被阻止。前台实际 Home 查询成功后，修正测试前提，不改手机策略。回环 TLS 测试使用显式 127.0.0.1 和专用测试证书，生产客户端仍使用系统信任链。
3. 增补 503+Retry-After:0 测试先失败（11项中1失败），定位 OkHttp 内建 follow-up，禁止该隐式重发后全部通过。另补响应体读取超时分类测试。
4. 测试工具等待公钥文件写完后再解析，避免部分读取；关闭 Activity 和 Compose 安全标记检查等待实际生命周期/重组完成；断网测试改用已关闭回环端口，避免复用连接影响模拟。

## 截图与人工审阅

截图上传至指定 Google Drive /ChatGPT/Dicta；视觉状态：**待用户审阅**。自动检查和功能测试不代表人工视觉通过。Settings 为安全窗口，不采集含凭据输入的截图。

## 范围边界

仅在授权 OnePlus 6T / A6013 / fajita、Android15/API35 实测；minSdk26 是编译下限。保留 M1 已认可的字体回退与 dim 效果。完整历史持久化/收藏/每日词/Profile 编辑器留 M3，TTS 和最终视觉精修/验收留 M4。模型词源保持现有不确定性提示，不增加独立核查系统。临时包名、许可证、正式发布时间保持开放。

## APK 与安全交付

已安装最终 debug APK，版本 `0.2.0-m2`，临时包 `dev.dicta.prototype`。

- 构建输出：`app/build/outputs/apk/debug/app-debug.apk`
- 可安装交付副本：`artifacts/m2/Dicta-M2-debug.apk`（本地忽略，不提交二进制）
- 大小：12,009,326 bytes
- SHA256：`f89ba674bcc1c6a3ffcc8f8e354a9b40cd59321ddaae46f7f2c267031b1825e5`
- 扫描 App/test APK 解压成员、仓库文件/历史及本 App 日志，真实开发 Key 字节均不存在；设备持久文件只含密文。App APK 没有 FakeProvider 或测试录入接收器。INTERNET 已声明，无悬浮窗、无障碍、个人数据读取权限。
- 已移除本阶段临时 tcp:8766 reverse、受控服务器与 UI dump；测试 APK 在验收后移除，保留可用 App 及其加密配置。不清除手机个人数据、不修改系统或网络设置。

## 截图链接（全部待用户审阅）

- [M2-01-home-live.png](https://drive.google.com/file/d/1kNfygG5sjo1cR_Py7d6Gkv8EEH0YyAX8/view)
- [M2-02-chrome-selection-menu.png](https://drive.google.com/file/d/1EYgfgnps24TzhR5yDsrHboxEPN4r8j40/view)
- [M2-03-chrome-popup-live.png](https://drive.google.com/file/d/1BMphg-1RJmQpn4i9zixgm-DXMdX3eo01/view)
- [M2-04-return-to-chrome.png](https://drive.google.com/file/d/1znHKN0rhqUXbqbtDFIpHGHIU--dJdh_t/view)
- [M2-05-home-after-restart.png](https://drive.google.com/file/d/1fgkyozd1UVS1EG374zfQQ34D5NS5Rk_K/view)

原图本地路径 `artifacts/m2/`；每张大小、SHA256与Drive链接见 `evidence/screenshots.json`。

## 收尾与剩余事项

工程和真机功能验收完成，人工视觉审阅仍待用户进行。除上述M3/M4范围及仅API35实测的兼容边界外，无本阶段功能阻碍。手机现有后台网络限制保持原状，测试按实际前台查词场景运行。Chrome返回后偶尔短暂只提供 Web View 的无子节点可访问性树，视觉内容/位置正常；自动化改用已观察的页面和前台状态，不据此修改产品或系统设置。

Git 仅 main，最终提交 SHA、推送与工作树状态以 T03 回写为准，避免文档引用自身提交的循环。

官方依据：[DeepSeek JSON Output](https://api-docs.deepseek.com/guides/json_mode/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[OkHttp 4.12 client源码](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt)。

