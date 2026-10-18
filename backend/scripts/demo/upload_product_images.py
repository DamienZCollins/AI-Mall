"""把仓库 素材/ 下的商品图上传到 RustFS，对象键固定为 products/{序号}.jpg。

用法：cd backend; python scripts/demo/upload_product_images.py

对象键固定不变，所以 sql/data.sql 里的 image_url 可以直接写死，重跑本脚本即覆盖。
签名用 curl 的 --aws-sigv4，不引入 boto3 依赖。
"""

import json
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BACKEND = Path(__file__).resolve().parents[2]
ROOT = BACKEND.parent
IMAGE_DIRS = ["01_数码", "02_家居", "03_个护美妆", "04_服饰箱包", "05_食品饮品", "06_生活方式"]
# 与 sql/data.sql 的商品编号一致：素材序号 01~34 与 40，35~39 是场景图不上传为商品
SKIP_SEQUENCES = {35, 36, 37, 38, 39}


def load_env() -> dict:
    config = {}
    with open(BACKEND / ".env", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            config[key.strip()] = value.strip()
    return config


def collect_images() -> list[tuple[int, Path]]:
    """按素材序号收集商品图，序号来自文件名前缀"""
    images: dict[int, Path] = {}
    for name in IMAGE_DIRS:
        directory = ROOT / "素材" / name
        if not directory.is_dir():
            raise SystemExit(f"素材目录不存在：{directory}")
        for path in sorted(directory.glob("*.jpg")):
            sequence = int(path.name[:2])
            if sequence in SKIP_SEQUENCES:
                continue
            images[sequence] = path
    if not images:
        raise SystemExit("素材目录下没有找到 jpg 图片")
    return sorted(images.items())


def put_object(config: dict, sequence: int, path: Path) -> str:
    key = f"products/{sequence:02d}.jpg"
    url = f"{config['RUSTFS_ENDPOINT']}/{config['RUSTFS_BUCKET_PRODUCT']}/{key}"
    command = [
        "curl", "-sS", "-o", "NUL", "-w", "%{http_code}",
        "--aws-sigv4", f"aws:amz:{config['RUSTFS_REGION']}:s3",
        "--user", f"{config['RUSTFS_ACCESS_KEY']}:{config['RUSTFS_SECRET_KEY']}",
        "-X", "PUT", "-H", "Content-Type: image/jpeg",
        "--data-binary", f"@{path}", url,
    ]
    result = subprocess.run(command, capture_output=True)
    code = result.stdout.decode("utf-8", errors="replace").strip()
    if result.returncode != 0 or code != "200":
        raise SystemExit(f"上传失败 {key}：curl 退出码 {result.returncode}，HTTP {code}\n"
                         f"{result.stderr.decode('utf-8', errors='replace')}")
    return key


def verify(config: dict, sequence: int) -> int:
    """公开读取一次，确认可达且内容类型正确"""
    key = f"products/{sequence:02d}.jpg"
    url = f"{config['RUSTFS_PUBLIC_BASE_URL']}/{key}"
    request = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(request, timeout=15) as response:
        content_type = response.headers.get("Content-Type", "")
        if response.status != 200:
            raise SystemExit(f"校验失败 {key}：HTTP {response.status}")
        if not content_type.startswith("image/"):
            raise SystemExit(f"校验失败 {key}：Content-Type 为 {content_type}")
        return len(response.read())


def main() -> None:
    config = load_env()
    images = collect_images()
    print(f"待上传 {len(images)} 张商品图 -> "
          f"{config['RUSTFS_PUBLIC_BASE_URL']}/products/")

    total = 0
    for sequence, path in images:
        put_object(config, sequence, path)
        size = verify(config, sequence)
        total += size
        print(f"  {sequence:02d}  {path.name}  {size // 1024} KB")

    print(f"完成：{len(images)} 张，合计 {total // 1024} KB")


if __name__ == "__main__":
    main()
