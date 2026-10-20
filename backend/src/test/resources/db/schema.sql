-- 阶段 2 表结构
-- 执行方式：cd backend; mysql -h localhost -u dev -p mall < sql/schema.sql

CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(32)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(72)  NOT NULL COMMENT 'BCrypt 密文',
    `nickname`    VARCHAR(32)           DEFAULT NULL COMMENT '昵称',
    `role`        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

CREATE TABLE IF NOT EXISTS `product`
(
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(128)   NOT NULL COMMENT '商品名称',
    `brand`       VARCHAR(64)             DEFAULT NULL COMMENT '品牌',
    `category_id` BIGINT         NOT NULL DEFAULT 0 COMMENT '分类 ID',
    `description` VARCHAR(2000)           DEFAULT NULL COMMENT '商品描述',
    `selling_point` VARCHAR(255)          DEFAULT NULL COMMENT '卖点',
    `price`       DECIMAL(10, 2) NOT NULL COMMENT '售价',
    `stock`       INT            NOT NULL DEFAULT 0 COMMENT '库存',
    `status`      TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：1 上架，0 下架',
    `image_url`   VARCHAR(512)            DEFAULT NULL COMMENT '商品图片 URL',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品';

CREATE TABLE IF NOT EXISTS `seckill_activity`
(
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`       BIGINT         NOT NULL COMMENT '绑定商品 ID',
    `title`            VARCHAR(128)   NOT NULL COMMENT '活动标题',
    `seckill_price`    DECIMAL(10, 2) NOT NULL COMMENT '秒杀价',
    `total_stock`      INT            NOT NULL COMMENT '活动总量，用于校验回补上限',
    `per_user_limit`   INT            NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time`       DATETIME       NOT NULL COMMENT '开始时间',
    `end_time`         DATETIME       NOT NULL COMMENT '结束时间',
    `pay_timeout_minutes` INT         NOT NULL DEFAULT 15 COMMENT '待支付超时分钟数',
    `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '配置状态：1 启用，0 停用',
    `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_end_time` (`end_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀活动';

CREATE TABLE IF NOT EXISTS `seckill_stock`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id` BIGINT   NOT NULL COMMENT '活动 ID',
    `stock`       INT      NOT NULL COMMENT '剩余库存',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_id` (`activity_id`),
    CONSTRAINT `ck_seckill_stock_non_negative` CHECK (`stock` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀库存';

CREATE TABLE IF NOT EXISTS `seckill_order`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(64)    NOT NULL COMMENT '订单号',
    `user_id`       BIGINT         NOT NULL COMMENT '下单用户',
    `activity_id`   BIGINT         NOT NULL COMMENT '活动 ID',
    `product_id`    BIGINT         NOT NULL COMMENT '商品 ID',
    `product_name`  VARCHAR(128)   NOT NULL COMMENT '下单时的商品名称',
    `seckill_price` DECIMAL(10, 2) NOT NULL COMMENT '成交价',
    `status`        TINYINT        NOT NULL COMMENT '1 待支付，2 已支付，3 已取消，4 超时取消',
    `expire_time`   DATETIME       NOT NULL COMMENT '支付截止时间',
    `pay_time`      DATETIME                DEFAULT NULL COMMENT '支付时间',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`),
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀订单';
