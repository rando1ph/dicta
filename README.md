# Dicta

Android 原生 BYOK AI 词典。已批准五屏设计位于 `design/`；M2 已接入 DeepSeek 真实 BYOK，Home 与 Chrome 划词 Popup 共用默认 Profile、实时查询与 Schema 校验。

- [M2 真实查询、凭据安全与真机验收](docs/m2/REPORT.md)
- [M1 原生工程、真机测试与截图审阅](docs/m1/REPORT.md)
- [M0 报告与验收证据](docs/m0/REPORT.md)
- [五屏字段、交互与状态](docs/m0/VISUAL-CONTRACT.md)
- [默认 Profile 规范与编辑方法](profiles/default/README.md)
- [最小架构与 M1 交接](docs/m0/ARCHITECTURE-M1.md)

```bash
# 原生 Debug APK + JVM tests + lint（隔离 JDK/SDK）
./tools/m1/build.sh
# APK: app/build/outputs/apk/debug/app-debug.apk
```

使用临时包名 `dev.dicta.prototype`。在 Settings 保存 HTTPS Endpoint / Model / API Key，再从首页查词或 Chrome 选词菜单调用 Dicta。默认 DeepSeek / deepseek-flash；Key 用 Android Keystore 加密存储。历史仅在本次进程内保留；完整持久化/收藏/每日词/Profile 编辑器留 M3，TTS 留 M4。M2 截图已上传指定 Drive，待用户人工审阅。

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/m0/requirements.txt
.venv/bin/python tools/m0/verify.py
.venv/bin/python -m unittest discover -s tools/m0 -v
```

仅在需要重新获取证据时执行真实 API 测试（五次 POST，可能收费）：

```bash
.venv/bin/python tools/m0/verify.py --live
python3 tools/m0/check_device.py
python3 tools/m0/check_secrets.py
```

测试密钥只从仓库外 `~/.config/dicta/deepseek_api_key` 读取，目录/文件权限须为 700/600。不能写入配置、日志、Git、Notion 或 APK。只在 `main` 工作；不创建分支/worktree，不使用 rebase/force push。
