# Mall

面向 Java 后端求职的模块化单体商城项目，核心亮点：

1. AI 混合搜索：Elasticsearch BM25 + 向量语义检索 + RRF 融合排序
2. 高并发秒杀：Redis Lua 预扣库存 + RocketMQ 异步下单

## 快速开始

```bash
# 1. 准备环境变量
copy .env.example .env

# 2. 启动本地中间件
docker compose up -d

# 3. 建表与初始化数据
docker exec -i mall-mysql mysql -uroot -p1234 --default-character-set=utf8mb4 mall < sql/schema.sql
docker exec -i mall-mysql mysql -uroot -p1234 --default-character-set=utf8mb4 mall < sql/data.sql

# 4. 查看状态
docker compose ps

# 5. 启动应用（凭据从 .env 读取）
set -a && . ./.env && set +a
mvn spring-boot:run
```

初始账号：`admin / admin123`（管理员）、`user / user123`（普通用户）。

## 接口一览

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 公开 | 登录并获取 JWT |
| GET | `/api/auth/me` | 登录 | 当前登录用户 |
| GET | `/api/products` | 公开 | 分页查询上架商品 |
| GET | `/api/products/{id}` | 公开 | 上架商品详情 |
| GET | `/api/search` | 公开 | 关键词 + 语义混合搜索 |
| POST | `/api/admin/products` | ADMIN | 新增商品 |
| PUT | `/api/admin/products/{id}` | ADMIN | 编辑商品 |
| PATCH | `/api/admin/products/{id}/status` | ADMIN | 上架 / 下架 |
| GET | `/api/admin/products` | ADMIN | 分页查询商品（含下架） |
| POST | `/api/admin/products/images` | ADMIN | 上传商品图片 |
| POST | `/api/admin/search/reindex` | ADMIN | 全量重建商品索引 |

带权限的接口需携带 `Authorization: Bearer <token>`。接口文档：`/swagger-ui/index.html`。

## 混合搜索

商品写入 Elasticsearch 时把「名称 + 品牌 + 卖点 + 描述」向量化存入 `dense_vector` 字段；
检索时同时发起 BM25 关键词召回与 kNN 语义召回，两路结果按 RRF（`Σ 1/(k+rank)`，k=60）融合。
品牌型号这类精确查询靠 BM25 稳定命中，自然语言描述靠 kNN 召回语义相关商品。

Elasticsearch 使用自建镜像 `docker/elasticsearch/Dockerfile`，额外安装了 analysis-ik 分词插件，
否则中文会被切成单字。首次使用需先 `docker compose build elasticsearch`。


## 开发环境

| 依赖 | 版本 |
| --- | --- |
| Java | 21 |
| Docker Desktop | 支持 Compose v2 |
| MySQL | 8.4 |
| Redis | 7.2 |
| RocketMQ | 5.3.2 |
| Elasticsearch | 8.17.6 |
| RustFS | latest |

## 目录结构

```text
.
├── config/                     # 配置模板
├── docker/rocketmq/            # RocketMQ broker 配置
├── docker-compose.yml          # 本地中间件编排
├── scripts/                    # 辅助脚本
├── sql/                        # 建表与初始化数据脚本
└── .env.example                # 环境变量模板
```

## 文档

本地文档位于 `docs/`，不纳入版本库。
