#!/usr/bin/env python3
"""Verify untouched PNGs against Drive metadata and write the review index."""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
manifest_path = root / "docs/t07/evidence/screenshots.json"
manifest = json.loads(manifest_path.read_text())
remote = {item["file"]: item for item in json.loads((root / "artifacts/t07/drive-readback.json").read_text())}
scenarios = [
    "Search 首页、每日词缓存", "每日词收藏 / 换一换同排", "Search 主动查询 → 独立详情",
    "History 搜索筛选", "History → 本地快照详情", "详情边缘返回手势 → History，保留筛选",
    "Vocabulary 筛选与收藏", "Vocabulary → 本地快照详情", "模型服务、已保存 Key 固定遮蔽",
    "自动模型发现、可搜索选择器", "生效模型、保存主操作、测试用量提示", "Light 与 Accent 3 对照",
    "320dp / 200% 字体，首页与导航降级", "320dp / 200% 字体，每日词操作与词源换行",
    "320dp / 200% 字体，保存 / 测试可达", "320dp / 200% 字体，详情返回与正文滚动",
    "外部浏览器真实长按 → 系统 Dicta 菜单", "系统菜单 → 原生 Popup，固定页脚",
    "Popup 打开 → 完整详情，隐藏底部导航", "完整详情边缘返回 → 浏览器同一阅读位置",
]
rows = []
for item in manifest["images"]:
    number = int(item["file"].split("-")[1])
    path = root / "artifacts/t07/screenshots" / item["file"]
    data = path.read_bytes()
    with Image.open(path) as png:
        assert png.format == "PNG"
        width, height = png.size
        png.verify()
    assert int(remote[item["file"]]["size"]) == len(data), item["file"]
    assert manifest["folder"].endswith(remote[item["file"]]["parent_ids"][0]), item["file"]
    large = 13 <= number <= 16
    assert (width, height) == ((840, 1920) if large else (1080, 2400))
    theme = "Light / Accent 3" if number == 12 else "Browser / light page" if number in (17, 20) else "Dark / Accent 1"
    mode = f"{'320dp · 200%' if large else '411dp · 100%'} · {theme}"
    item.update(scenario=scenarios[number - 1], api=36, width_px=width, height_px=height,
                width_dp=320 if large else 411, density_dpi=420, font_scale=2.0 if large else 1.0,
                theme=theme, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                drive_metadata_verified=True, review="待用户审阅")
    rows.append(f"| [{item['file']}]({item['url']}) | {item['scenario']} | {mode} | 待审 |")
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
text = """# T07 · v1.1.0 截图审阅索引

[打开 Google Drive 原始截图目录](https://drive.google.com/drive/folders/1gFzRZcU8--qtCNtvcei3kMU8SbpCampT)

全部为实际运行的原始 PNG，由 Google 官方 Android CLI 捕获，逐张视觉检查；未裁剪、重编码或拼接。设备为 codex_api36_aosp / **emulator-5554**，Android 16 / API36、420dpi。所有模型请求和模型列表均来自本地 TLS Mock，使用固定词条夹具与测试凭据，没有真实 API Key 或付费查询。

13–16 为 840×1920 / 320dp / 系统字体 200%；其余为 1080×2400 / 411dp / 字体 100%。AOSP 没有安装 TTS 引擎，截图中的发音禁用与回退提示为真实状态。17–20 使用 AOSP WebView Browser Tester 和仓库内的受控阅读页，未将其表述为 Chrome 或真实新闻网站。

**人工视觉与候选版本验收均待用户审阅。** 每张图的大小、SHA-256 和 Drive 元数据核验见 [机器索引](https://github.com/rando1ph/dicta/blob/main/docs/t07/evidence/screenshots.json)。

| 文件 | 场景 | 屏宽 / 字体 / 主题 | 审阅 |
| --- | --- | --- | --- |
""" + "\n".join(rows) + "\n"
(root / "docs/t07/SCREENSHOTS.md").write_text(text)
print(f"Verified and indexed {len(rows)} original PNG files.")
