"""阶段 2 接口验收脚本：覆盖登录、角色鉴权、商品维护与图片上传。

用法：cd backend; python scripts/checks/api_check.py
前提：应用已在 localhost:8080 启动，sql/schema.sql 与 sql/data.sql 已执行。
说明：用 Python 发起请求是为了避免 Windows 终端 GBK 编码破坏中文请求体。
"""

import json
import subprocess
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://localhost:8080"
ROOT = Path(__file__).resolve().parents[3]


def request(path, method="GET", payload=None, token=None, form=None):
    cmd = ["curl", "-s", "-w", "\n%{http_code}", "-X", method, BASE + path]
    if token:
        cmd += ["-H", f"Authorization: Bearer {token}"]
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        cmd += ["-H", "Content-Type: application/json; charset=UTF-8", "--data-binary", "@-"]
    elif form is not None:
        cmd += form
    out = subprocess.run(cmd, input=data, capture_output=True)
    text = out.stdout.decode("utf-8")
    body, _, code = text.rpartition("\n")
    return int(code), body


def show(label, code, body, expect_code):
    mark = "PASS" if code == expect_code else "FAIL"
    print(f"[{mark}] {label} -> HTTP {code} (期望 {expect_code})")
    print(f"       {body[:220]}")
    return mark == "PASS"


def check(label, condition, detail=""):
    mark = "PASS" if condition else "FAIL"
    print(f"[{mark}] {label}" + (f" -> {detail}" if detail else ""))
    return bool(condition)


def main():
    results = []

    code, body = request("/api/auth/login", "POST", {"username": "admin", "password": "admin123"})
    results.append(show("1 管理员登录", code, body, 200))
    admin_token = json.loads(body)["data"]["token"]

    code, body = request("/api/auth/login", "POST", {"username": "user", "password": "user123"})
    results.append(show("2 普通用户登录", code, body, 200))
    user_token = json.loads(body)["data"]["token"]

    code, body = request("/api/auth/login", "POST", {"username": "admin", "password": "bad"})
    results.append(show("3 错误密码", code, body, 200))
    results.append(show("3b 错误密码错误码", 0 if '"code":1001' in body else 1, body, 0))

    code, body = request("/api/auth/me", token=admin_token)
    results.append(show("4 当前登录用户", code, body, 200))

    code, body = request("/api/auth/me")
    results.append(show("5 未带令牌访问 /api/auth/me", code, body, 401))

    code, body = request("/api/auth/me", token="abc.def.ghi")
    results.append(show("6 伪造令牌", code, body, 401))

    code, body = request("/api/products?page=1&size=10")
    results.append(show("7 用户端商品列表（游客可访问）", code, body, 200))
    listed = json.loads(body)["data"]["list"]
    names = [p["name"] for p in listed]
    print(f"       列表: {names}")
    results.append(check("8 下架商品不在列表中", all(p["status"] == 1 for p in listed)))

    code, body = request("/api/products/4", token=user_token)
    results.append(show("9 下架商品详情", code, body, 200))
    results.append(check("9b 下架商品返回 2001", '"code":2001' in body))

    code, body = request("/api/products/1")
    results.append(show("10 上架商品详情", code, body, 200))

    code, body = request("/api/admin/products?page=1&size=10", token=user_token)
    results.append(show("11 USER 角色访问管理端", code, body, 403))

    code, body = request("/api/admin/products", "POST",
                         {"name": "缓震竞速跑鞋", "brand": "沃兰", "categoryId": 1001,
                          "description": "碳板中底，适合半马与全马竞速。",
                          "sellingPoint": "碳板 竞速缓震", "price": 1099.00, "stock": 30},
                         token=admin_token)
    results.append(show("12 管理员新增商品", code, body, 200))
    new_id = json.loads(body)["data"]["id"]
    results.append(check("12b 新增后默认下架", json.loads(body)["data"]["status"] == 0))

    code, body = request(f"/api/products/{new_id}")
    results.append(show("13 下架新品对用户不可见", code, body, 200))
    results.append(check("13b 返回 2001", '"code":2001' in body))

    code, body = request(f"/api/admin/products/{new_id}/status", "PATCH", {"status": 1}, token=admin_token)
    results.append(show("14 管理员上架商品", code, body, 200))
    results.append(check("14b 状态变为上架", json.loads(body)["data"]["status"] == 1))

    code, body = request(f"/api/products/{new_id}")
    results.append(show("15 上架后用户可见", code, body, 200))

    code, body = request(f"/api/admin/products/{new_id}/status", "PATCH", {"status": 1}, token=admin_token)
    results.append(show("16 重复上架被拒绝", code, body, 200))
    results.append(check("16b 返回状态不合法 2002", '"code":2002' in body))

    code, body = request(f"/api/admin/products/{new_id}", "PUT",
                         {"price": 999.00, "sellingPoint": "碳板 竞速缓震 轻量"}, token=admin_token)
    results.append(show("17 管理员编辑商品", code, body, 200))
    results.append(check("17b 价格已更新", json.loads(body)["data"]["price"] == 999.0))

    code, body = request(f"/api/admin/products/{new_id}", "PUT", {"price": -1}, token=admin_token)
    results.append(show("18 非法价格被拒绝", code, body, 400))

    code, body = request(f"/api/admin/products/{new_id}/status", "PATCH", {"status": 0}, token=admin_token)
    results.append(show("19 管理员下架商品", code, body, 200))

    code, body = request(f"/api/products/{new_id}")
    results.append(show("20 下架后用户不可见", code, body, 200))
    results.append(check("20b 返回 2001", '"code":2001' in body))

    code, body = request("/api/products/999999")
    results.append(show("21 不存在的商品", code, body, 200))

    code, body = request("/api/products?page=0")
    results.append(show("22 非法分页参数", code, body, 400))

    code, body = request("/api/admin/products/images", "POST",
                         token=admin_token,
                         form=["-F", f"file=@{ROOT / 'src/test/resources/sample.png'};type=image/png"])
    results.append(show("23 上传商品图片", code, body, 200))
    if '"code":0' in body:
        url = json.loads(body)["data"]
        out = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", url],
                             capture_output=True)
        print(f"       图片 URL: {url}")
        results.append(check("23b 图片 URL 可公开访问", out.stdout.decode().strip() == "200", url))

    code, body = request("/api/admin/products/images", "POST",
                         token=admin_token,
                         form=["-F", f"file=@{ROOT / 'pom.xml'};type=image/png"])
    results.append(show("24 非图片文件被拒绝", code, body, 200))
    results.append(check("24b 返回上传失败 2003", '"code":2003' in body))

    code, body = request("/api/admin/products/images", "POST", token=user_token,
                         form=["-F", f"file=@{ROOT / 'src/test/resources/sample.png'};type=image/png"])
    results.append(show("25 USER 角色上传图片", code, body, 403))

    passed = sum(1 for r in results if r is True)
    print(f"\n通过 {passed} / {len(results)} 项")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
