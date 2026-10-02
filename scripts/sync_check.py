"""阶段 3 端到端验收：验证商品变更后索引同步，以及阶段 2 功能未回归。

用法：python scripts/sync_check.py
"""

import json
import subprocess
import sys
import time

BASE = "http://localhost:8080"


def request(path, method="GET", payload=None, token=None):
    cmd = ["curl", "-s", "-w", "\n%{http_code}", "-X", method, BASE + path]
    if token:
        cmd += ["-H", f"Authorization: Bearer {token}"]
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        cmd += ["-H", "Content-Type: application/json; charset=UTF-8", "--data-binary", "@-"]
    out = subprocess.run(cmd, input=data, capture_output=True)
    text = out.stdout.decode("utf-8")
    body, _, code = text.rpartition("\n")
    return int(code), body


def search_names(keyword, size=8):
    out = subprocess.run(
        ["curl", "-s", "-G", f"{BASE}/api/search",
         "--data-urlencode", f"keyword={keyword}",
         "--data-urlencode", f"size={size}"],
        capture_output=True)
    return [h["name"] for h in json.loads(out.stdout.decode("utf-8"))["data"]["list"]]


def search_status(keyword, page=1):
    out = subprocess.run(
        ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-G", f"{BASE}/api/search",
         "--data-urlencode", f"keyword={keyword}", "--data-urlencode", f"page={page}"],
        capture_output=True)
    return int(out.stdout.decode("utf-8").strip())


def show(label, condition, detail=""):
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" -> {detail}" if detail else ""))
    return bool(condition)


def main():
    results = []
    _, body = request("/api/auth/login", "POST", {"username": "admin", "password": "admin123"})
    admin = json.loads(body)["data"]["token"]

    print("== 商品变更同步索引 ==")
    probe = "极地防寒雪地靴"
    code, body = request("/api/admin/products", "POST",
                         {"name": probe, "brand": "暖冬", "categoryId": 1002,
                          "description": "零下三十度极寒环境专用，加厚羊毛内里。",
                          "sellingPoint": "极寒保暖", "price": 888.00, "stock": 10}, token=admin)
    product_id = json.loads(body)["data"]["id"]
    results.append(show("新增商品成功", code == 200, f"id={product_id}"))

    names = search_names("极地防寒雪地靴")
    results.append(show("新增商品默认下架，搜不到", probe not in names))

    code, body = request(f"/api/admin/products/{product_id}/status", "PATCH", {"status": 1}, token=admin)
    results.append(show("上架成功", code == 200))
    names = search_names("极地防寒雪地靴")
    results.append(show("上架后立即可搜到", probe in names, str(names[:3])))

    code, body = request(f"/api/admin/products/{product_id}", "PUT",
                         {"sellingPoint": "极寒保暖 防滑耐磨"}, token=admin)
    results.append(show("编辑商品成功", code == 200))

    code, body = request(f"/api/admin/products/{product_id}/status", "PATCH", {"status": 0}, token=admin)
    results.append(show("下架成功", code == 200))
    names = search_names("极地防寒雪地靴")
    results.append(show("下架后搜不到", probe not in names))

    print("\n== 阶段 2 功能回归 ==")
    code, body = request("/api/products?page=1&size=5")
    results.append(show("用户端商品列表", code == 200 and json.loads(body)["code"] == 0))

    code, body = request("/api/products/1")
    results.append(show("商品详情", code == 200 and json.loads(body)["code"] == 0))

    code, body = request("/api/auth/me", token=admin)
    results.append(show("当前登录用户", code == 200))

    code, body = request("/api/auth/me")
    results.append(show("未带令牌返回 401", code == 401))

    code, body = request("/api/admin/products?page=1&size=5")
    results.append(show("未带令牌访问管理端返回 401", code == 401))

    code, body = request("/api/search")
    results.append(show("搜索缺少 keyword 返回 400", code == 400))

    results.append(show("搜索非法页码返回 400", search_status("测试", page=0) == 400))

    print("\n== 清理 ==")
    subprocess.run(["docker", "exec", "mall-mysql", "mysql", "-uroot", "-p1234", "-e",
                    f"USE mall; DELETE FROM product WHERE id = {product_id};"], capture_output=True)
    print(f"已删除测试商品 id={product_id}")

    passed = sum(1 for r in results if r)
    print(f"\n通过 {passed} / {len(results)} 项")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
