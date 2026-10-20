# Mall

面向 Java 后端求职的模块化单体商城项目，核心亮点：

1. AI 混合搜索：Elasticsearch BM25 + 向量语义检索 + RRF 融合排序
2. 高并发秒杀：Redis Lua 预扣库存 + RocketMQ 异步下单

## 快速开始

后端位于 `backend/`，以下命令均在该目录执行：

```bash
cd backend

# 1. 准备环境变量
copy .env.example .env

# 2. 一键启动：构建应用、启动中间件、建表、初始化 RocketMQ 主题并启动应用
powershell -ExecutionPolicy Bypass -File start.ps1

# 可选：只启动中间件
powershell -ExecutionPolicy Bypass -File start.ps1 -NoApp

# 可选：删除并重建 mall 数据库后启动
powershell -ExecutionPolicy Bypass -File start.ps1 -ResetDb
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
| GET | `/api/seckill/activities` | 公开 | 分页查询秒杀活动 |
| GET | `/api/seckill/activities/{id}` | 公开 | 秒杀活动详情 |
| POST | `/api/seckill/{activityId}` | 登录 | 抢购下单，Redis 预扣后立即返回 |
| GET | `/api/seckill/{activityId}/result` | 登录 | 轮询抢购结果 |
| GET | `/api/seckill/orders/{orderNo}` | 登录 | 查询秒杀订单 |
| POST | `/api/seckill/orders/{orderNo}/pay` | 登录 | 模拟支付回调 |
| POST | `/api/admin/products` | ADMIN | 新增商品 |
| PUT | `/api/admin/products/{id}` | ADMIN | 编辑商品 |
| PATCH | `/api/admin/products/{id}/status` | ADMIN | 上架 / 下架 |
| GET | `/api/admin/products` | ADMIN | 分页查询商品（含下架） |
| POST | `/api/admin/products/images` | ADMIN | 上传商品图片 |
| POST | `/api/admin/search/reindex` | ADMIN | 全量重建商品索引 |
| POST | `/api/admin/seckill/activities` | ADMIN | 创建秒杀活动 |
| PUT | `/api/admin/seckill/activities/{id}` | ADMIN | 编辑秒杀活动 |
| GET | `/api/admin/seckill/activities` | ADMIN | 分页查询全部秒杀活动 |

带权限的接口需携带 `Authorization: Bearer <token>`。接口文档：`/swagger-ui/index.html`。

## 秒杀

活动库存预热到 Redis，抢购请求由 Lua 脚本在 Redis 内一次性完成时间校验、用户去重与库存扣减，
成功后投递 RocketMQ 异步创建订单，接口立即返回而不等待数据库事务。
订单落库后投递延迟消息，到期检查仍未支付则关单并回补库存。
Redis 与数据库两侧都会扣减与回补，数据库约束作为最后一道防线：防重复靠唯一索引
`(user_id, activity_id)`，防超卖靠条件更新 `WHERE stock > 0`，回补靠条件更新的影响行数避免重复执行。
消息投递失败时立即回补 Redis 预扣，不会出现「库存扣了但没有订单」的悬空状态。


## 混合搜索

商品写入 Elasticsearch 时把「名称 + 品牌 + 卖点 + 描述」向量化存入 `dense_vector` 字段；
检索时同时发起 BM25 关键词召回与 kNN 语义召回，两路结果按 RRF（`Σ 1/(k+rank)`，k=60）融合。
品牌型号这类精确查询靠 BM25 稳定命中，自然语言描述靠 kNN 召回语义相关商品。

Elasticsearch 使用自建镜像 `backend/docker/elasticsearch/Dockerfile`，额外安装了 analysis-ik 分词插件，
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
├── backend/                    # 后端开发入口（Spring Boot 与运行依赖）
│   ├── src/
│   ├── pom.xml
│   ├── docker/                 # RocketMQ 配置、含 ik 分词的 ES 镜像
│   ├── docker-compose.yml      # 本地中间件编排
│   ├── sql/                    # 建表与初始化数据
│   ├── .env.example
│   └── start.ps1
├── frontend/                   # 前端开发入口（Vue 3 + Vite）
│   ├── src/
│   ├── public/images/products/ # 商品图（素材副本）
│   └── package.json
├── docs/                       # 全部本地文档，不纳入版本库
│   ├── 接口文档.md
│   ├── backend/                # 后端总体方案、开发路径、阶段拆分
│   └── frontend/               # 前端总体方案、开发路径、阶段拆分与页面截图
├── 素材/                       # 商品图原始素材（40 张）
├── README.md
└── AGENTS.md
```

`backend/` 与 `frontend/` 是相互独立的开发入口。后端源码不跨目录引用前端，前端通过 HTTP 接口访问后端。
文档不再分散在各端目录内，统一以根 `docs/` 为唯一入口，按 `backend/`、`frontend/` 区分归属。

## 仓库边界

本仓库只保留功能开发与运行所需的代码、建表数据与方案文档，不含任何测试、压测与验收产物，
也不提供可执行的测试入口。前端仅保留 `dev`、`build`、`preview`、`typecheck`、`lint`、`format` 六个脚本。

## RocketMQ 主题

`start.ps1` 启动时会自动创建两个主题（见脚本「初始化 RocketMQ 主题」一节），正常流程无需手工干预。
需要单独重建时执行：

```bash
docker exec mall-rocketmq-broker sh -c \
  'cd /home/rocketmq/rocketmq-5.3.2 && sh bin/mqadmin updateTopic -b 127.0.0.1:10911 -t mall-seckill-order -r 4 -w 4 -a "+message.type=NORMAL"'

docker exec mall-rocketmq-broker sh -c \
  'cd /home/rocketmq/rocketmq-5.3.2 && sh bin/mqadmin updateTopic -b 127.0.0.1:10911 -t mall-seckill-timeout -r 4 -w 4 -a "+message.type=DELAY"'

docker compose restart rocketmq-proxy
```

RocketMQ 5.x 的主题要显式声明能接受的消息类型，普通消息与延迟消息不能放同一主题，
因此拆成 `mall-seckill-order`(NORMAL) 与 `mall-seckill-timeout`(DELAY)。
代理会缓存主题路由，新建主题后需要重启代理。

## 文档

本地文档位于根 `docs/`，不纳入版本库：

| 路径 | 内容 |
| --- | --- |
| `docs/接口文档.md` | 接口、业务码与错误约定 |
| `docs/backend/` | 后端总体方案、开发路径、阶段 0–6 任务拆分 |
| `docs/frontend/` | 前端总体方案、开发路径、阶段 0–6 拆分、页面截图与素材接入说明 |
