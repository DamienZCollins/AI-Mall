"""阶段 3 检索验收：直接观察 BM25、kNN 与 RRF 融合三路结果。

用法：python scripts/search_check.py
前提：应用已启动，索引已通过 POST /api/admin/search/reindex 建立。
"""

import json
import subprocess
import sys

BASE = "http://localhost:8080"
ES = "http://localhost:9200"


def search(keyword, size=5):
    out = subprocess.run(
        ["curl", "-s", "-G", f"{BASE}/api/search",
         "--data-urlencode", f"keyword={keyword}",
         "--data-urlencode", f"size={size}"],
        capture_output=True)
    return json.loads(out.stdout.decode("utf-8"))


def es_search(body):
    out = subprocess.run(
        ["curl", "-s", "-X", "POST", f"{ES}/mall_product/_search",
         "-H", "Content-Type: application/json", "--data-binary", "@-"],
        input=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        capture_output=True)
    return json.loads(out.stdout.decode("utf-8"))


def names(response):
    return [h["_source"]["name"] for h in response["hits"]["hits"]]


def bm25(keyword, size=5):
    return es_search({
        "size": size,
        "_source": ["name"],
        "query": {"bool": {
            "must": [{"multi_match": {"query": keyword,
                                      "fields": ["name^3", "brand^2", "sellingPoint", "description"]}}],
            "filter": [{"term": {"status": 1}}]}},
    })


def knn(keyword, size=5):
    vector = embed(keyword)
    return es_search({
        "size": size,
        "_source": ["name"],
        "knn": {"field": "embedding", "query_vector": vector, "k": size * 2, "num_candidates": size * 4,
                "filter": [{"term": {"status": 1}}, {"term": {"hasVector": True}}]},
    })


def embed(text):
    import urllib.request
    with open(".env", encoding="utf-8") as f:
        config = dict(line.strip().split("=", 1) for line in f if "=" in line and not line.startswith("#"))
    req = urllib.request.Request(
        config["EMBEDDING_BASE_URL"].rstrip("/") + "/embeddings",
        data=json.dumps({"model": config["EMBEDDING_MODEL"], "input": text}).encode("utf-8"),
        headers={"Authorization": "Bearer " + config["EMBEDDING_API_KEY"],
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)["data"][0]["embedding"]


def report(keyword):
    print(f"\n{'=' * 70}\n查询：{keyword}\n{'=' * 70}")
    print("\n[RRF 融合 —— 对外接口]")
    result = search(keyword)
    for i, h in enumerate(result["data"]["list"], 1):
        print(f"  {i}. {h['name']}  [{h['brand']}]  融合分={h['score']:.5f}")
    print("\n[仅 BM25 关键词召回]")
    for i, name in enumerate(names(bm25(keyword)), 1):
        print(f"  {i}. {name}")
    print("\n[仅 kNN 语义召回]")
    for i, name in enumerate(names(knn(keyword)), 1):
        print(f"  {i}. {name}")


if __name__ == "__main__":
    queries = sys.argv[1:] or ["夏天穿的透气鞋", "Nike 跑鞋", "下雨天出门穿什么", "冬天保暖"]
    for q in queries:
        report(q)

    print(f"\n{'=' * 70}\n下架商品验证\n{'=' * 70}")
    result = search("防水越野跑鞋")
    hit_names = [h["name"] for h in result["data"]["list"]]
    print(f"  查询「防水越野跑鞋」返回：{hit_names}")
    print(f"  下架商品「防水越野跑鞋」是否出现：{'是（异常）' if '防水越野跑鞋' in hit_names else '否（正确）'}")
