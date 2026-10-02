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

# 3. 查看状态
docker compose ps
```

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
└── .env.example                # 环境变量模板
```

## 文档

本地文档位于 `docs/`，不纳入版本库。
