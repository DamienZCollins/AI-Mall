-- 阶段 2 表结构
-- 执行方式：mysql -h localhost -u dev -p mall < sql/schema.sql

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
