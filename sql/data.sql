-- 阶段 2 初始数据
-- 执行方式：mysql -h localhost -u dev -p mall < sql/data.sql
-- 账号密码：admin / admin123，user / user123（均为 BCrypt 密文）
-- 可重复执行，重复执行时覆盖为初始状态

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (1, 'admin', '$2a$10$gsrWk4T3hqvDHUMgbaiYG.S5UlRAFPuAbNDPTXL9xLixZvCD0RL..', '管理员', 'ADMIN', 1),
       (2, 'user', '$2a$10$YKC2WcZV7yP8PfTMSGX3OOLQPrhUCXHuFBdEs3.1TW0gr/MvIXqSG', '普通用户', 'USER', 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`),
                        `nickname` = VALUES(`nickname`),
                        `role`     = VALUES(`role`),
                        `status`   = VALUES(`status`);

INSERT INTO `product` (`id`, `name`, `brand`, `category_id`, `description`, `selling_point`, `price`, `stock`, `status`,
                       `image_url`)
VALUES (1, '轻量网面透气跑步鞋', '沃兰', 1001,
        '单只重量约 210 克，鞋面采用工程网眼，长时间穿着不闷脚，适合日常通勤与五公里慢跑。', '网面透气 轻量缓震', 399.00, 120, 1, NULL),
       (2, '夏季透气休闲帆布鞋', '沐行', 1001,
        '经典帆布鞋型，薄款鞋垫搭配透气内里，夏天穿不捂脚，可搭配休闲裤与短裤。', '夏季薄款 透气不闷脚', 199.00, 200, 1, NULL),
       (3, '真皮商务皮鞋', '匠心', 1002,
        '头层牛皮鞋面，橡胶大底，鞋型偏正式，适合商务场合搭配西装。', '头层牛皮 商务正装', 899.00, 50, 1, NULL),
       (4, '防水越野跑鞋', '沃兰', 1001,
        '防水透气膜内衬，深齿橡胶外底，适合雨天与轻度越野路面。', '防水 深齿抓地', 599.00, 80, 0, NULL),
       (5, '可折叠双肩背包', '沐行', 1003,
        '轻量尼龙面料，可折叠收纳成掌心大小，日常通勤与短途出行均可使用。', '可折叠 轻量收纳', 129.00, 300, 1, NULL)
ON DUPLICATE KEY UPDATE `name`         = VALUES(`name`),
                        `brand`        = VALUES(`brand`),
                        `category_id`  = VALUES(`category_id`),
                        `description`  = VALUES(`description`),
                        `selling_point` = VALUES(`selling_point`),
                        `price`        = VALUES(`price`),
                        `stock`        = VALUES(`stock`),
                        `status`       = VALUES(`status`),
                        `image_url`    = VALUES(`image_url`);
