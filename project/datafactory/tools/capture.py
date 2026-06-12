"""截图识别工具 — 从剪贴板/文件/Temp目录读取图片，调用千问API识别
用法:
  python capture.py                   自动找图片(剪贴板→Temp→QQ截图)
  python capture.py <file>            指定文件
  python capture.py --clipboard       从剪贴板读取
  python capture.py --latest          最新QQ截图
"""

import sys, os, json, base64, io, time
from pathlib import Path
import requests

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 千问 (DashScope) API
cfg = {
    "api_base_url": "https://dashscope.aliyuncs.com/compatible-mode",
    "api_key": "sk-06da086491f34da28915249cf3c66410",
    "model": "qwen-vl-max"
}

OUTPUT = Path.home() / "screenshot.png"
QQ_DIR = Path("D:/Tencent/1106935659/nt_qq/nt_data/Pic")
MAX_PX = 1280


def from_clipboard():
    try:
        from PIL import ImageGrab
        img = ImageGrab.grabclipboard()
        if img:
            img.save(OUTPUT)
            return str(OUTPUT)
    except:
        pass
    return None


def from_temp():
    tmp = Path(os.environ.get('TEMP', 'C:/Windows/Temp'))
    candidates = []
    now = time.time()
    for p in tmp.rglob('*'):
        if p.suffix.lower() in ('.png', '.jpg', '.jpeg', '.bmp'):
            if now - p.stat().st_mtime < 120:
                candidates.append((p.stat().st_mtime, p))
    if candidates:
        candidates.sort(reverse=True)
        return str(candidates[0][1])
    return None


def from_qq():
    candidates = []
    if QQ_DIR.exists():
        for root, _, files in os.walk(QQ_DIR):
            if 'Thumb' in root.lower():
                continue
            for f in files:
                if f.lower().endswith(('.png', '.jpg', '.jpeg', '.bmp')):
                    p = Path(root) / f
                    candidates.append((p.stat().st_mtime, p))
    if candidates:
        candidates.sort(reverse=True)
        return str(candidates[0][1])
    return None


def recognize(image_path):
    from PIL import Image
    path = Path(image_path)
    if not path.exists():
        print(f"File not found: {image_path}")
        return

    img = Image.open(path)
    if max(img.size) > MAX_PX:
        r = MAX_PX / max(img.size)
        img = img.resize((int(img.size[0]*r), int(img.size[1]*r)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=85)
    b64 = base64.b64encode(buf.getvalue()).decode()
    img.save(OUTPUT)

    resp = requests.post(
        f"{cfg['api_base_url']}/v1/chat/completions",
        headers={"Authorization": f"Bearer {cfg['api_key']}"},
        json={
            "model": cfg["model"],
            "messages": [{
                "role": "user",
                "content": [
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                    {"type": "text", "text": "描述图片内容，包括所有按钮、文字、选项、状态信息"}
                ]
            }]
        },
        timeout=60
    )
    if resp.status_code == 200:
        text = resp.json()["choices"][0]["message"]["content"]
        print(f"\n识别: {image_path}")
        print("=" * 60)
        print(text)
        print("=" * 60)
    else:
        print(f"API Error: {resp.status_code} {resp.text}")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if arg == "--clipboard":
            path = from_clipboard()
        elif arg == "--latest":
            path = from_qq()
        else:
            path = arg
    else:
        path = from_clipboard() or from_temp() or from_qq()

    if path:
        recognize(path)
    else:
        print("No image found. Usage: python capture.py [file|--clipboard|--latest]")
