# Dicta

Android 原生 BYOK AI 词典。已批准五屏设计位于 `design/`；M3 已完成历史持久化、生词本、每日词与 Profile 编辑；Home 与 Chrome 划词 Popup 共用 DeepSeek BYOK、结构校验和配置驱动渲染。

- [M3 历史、收藏、每日词、Profile 与真机验收](docs/m3/REPORT.md)
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

使用临时包名 `dev.dicta.prototype`。在 Settings 保存 HTTPS Endpoint / Model / API Key，再从首页查词或 Chrome 选词菜单调用 Dicta。默认 DeepSeek / deepseek-flash；Key 用 Android Keystore 加密存储。历史、收藏、每日词缓存与用户 Profile 均持久保存。Settings → 管理 Profile 可复制、编辑、校验、预览、导入导出及恢复默认。旧词条使用自身配置快照，本地重开不调用 AI；每日换词使用相同 BYOK。原凭据配置保留。TTS 与最终视觉验收留 M4；M3 截图已上传指定 Drive，待用户人工审阅。

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
