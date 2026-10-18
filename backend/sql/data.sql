-- 阶段 2、3 初始数据
-- 执行方式：cd backend; mysql -h localhost -u dev -p mall < sql/data.sql
-- 账号密码：admin / admin123，user / user123（均为 BCrypt 密文）
-- 可重复执行：商品先清空再写入，保证索引重建的输入稳定

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (1, 'admin', '$2a$10$gsrWk4T3hqvDHUMgbaiYG.S5UlRAFPuAbNDPTXL9xLixZvCD0RL..', '管理员', 'ADMIN', 1),
       (2, 'user', '$2a$10$YKC2WcZV7yP8PfTMSGX3OOLQPrhUCXHuFBdEs3.1TW0gr/MvIXqSG', '普通用户', 'USER', 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`),
                        `nickname` = VALUES(`nickname`),
                        `role`     = VALUES(`role`),
                        `status`   = VALUES(`status`);

DELETE FROM `product`;
ALTER TABLE `product` AUTO_INCREMENT = 1;

-- 商品与素材图一一对应：编号即素材序号（01~34、40），图片由 scripts/demo/upload_product_images.py
-- 以 products/{序号}.jpg 为对象键写入 RustFS，image_url 前缀取 .env 的 RUSTFS_PUBLIC_BASE_URL。
INSERT INTO `product` (`id`, `name`, `brand`, `category_id`, `description`, `selling_point`, `price`, `stock`, `status`,
                       `image_url`)
VALUES
-- 01 数码
(1, '声屿 头戴式蓝牙耳机', '声屿', 1001,
 '40mm 动圈单元，主动降噪最高 38 dB，耳罩为记忆棉包覆蛋白皮，连续佩戴两小时不夹头。',
 '主动降噪 记忆棉耳罩', 899.00, 120, 1, 'http://localhost:9000/mall-product/products/01.jpg'),
(2, '云栖 轻量头戴耳机', '云栖', 1001,
 '整机约 190 克，头梁为轻质铝合金，支持折叠收纳，适合通勤与远程会议。',
 '轻量 190g 可折叠', 599.00, 86, 1, 'http://localhost:9000/mall-product/products/02.jpg'),
(3, '知音 真无线降噪耳机', '知音', 1001,
 '单次续航 8 小时，配合充电盒共 32 小时，支持通透模式与入耳检测。',
 '入耳检测 长续航', 699.00, 240, 1, 'http://localhost:9000/mall-product/products/03.jpg'),
(4, '声屿 木质桌面音箱', '声屿', 1001,
 '木质侧板搭配 3 英寸全频单元，低频下潜至 55 Hz，适合书桌与小空间。',
 '木质侧板 全频单元', 799.00, 64, 1, 'http://localhost:9000/mall-product/products/04.jpg'),
(5, '键序 紧凑无线机械键盘', '键序', 1001,
 '68 键紧凑布局，热插拔轴座，支持三模连接，键帽为 PBT 二色成型。',
 '热插拔 PBT 键帽', 469.00, 150, 1, 'http://localhost:9000/mall-product/products/05.jpg'),
(6, '掌间 人体工学无线鼠标', '掌间', 1001,
 '右手贴合弧度，静音微动，一节电池可用约 18 个月。',
 '静音微动 长续航', 259.00, 320, 1, 'http://localhost:9000/mall-product/products/06.jpg'),
(7, '时纪 智能运动腕表', '时纪', 1001,
 '1.4 英寸圆形表盘，支持心率与血氧监测，内置二十余种运动模式，防水 5 ATM。',
 '心率血氧 5ATM 防水', 1299.00, 78, 1, 'http://localhost:9000/mall-product/products/07.jpg'),

-- 02 家居
(8, '栖迟 原木高脚凳', '栖迟', 1002,
 '白蜡木实木框架，座面为整块木料打磨，适合吧台与岛台搭配。',
 '白蜡木 实木整料', 480.00, 56, 1, 'http://localhost:9000/mall-product/products/08.jpg'),
(9, '栖迟 米白软包单椅', '栖迟', 1002,
 '高密度海绵填充，坐深 52 cm，面料为可拆洗棉麻混纺。',
 '可拆洗 高密度海绵', 1280.00, 34, 1, 'http://localhost:9000/mall-product/products/09.jpg'),
(10, '青岚 墨绿绒面双人沙发', '青岚', 1002,
 '绒面面料搭配实木细腿，坐感偏软，适合客厅与阅读角。',
 '绒面 实木细腿', 3890.00, 18, 1, 'http://localhost:9000/mall-product/products/10.jpg'),
(11, '青岚 焦糖皮革三人沙发', '青岚', 1002,
 '头层牛皮包覆，高回弹海绵坐垫，三人位总宽 220 cm。',
 '头层牛皮 三人位', 5680.00, 12, 1, 'http://localhost:9000/mall-product/products/11.jpg'),
(12, '序白 柿红布艺双人沙发', '序白', 1002,
 '柿红色布艺，坐垫可拆洗，双人位总宽 168 cm，适合小户型客厅。',
 '可拆洗 小户型适配', 2680.00, 26, 1, 'http://localhost:9000/mall-product/products/12.jpg'),
(13, '序白 水洗棉条纹靠枕', '序白', 1002,
 '水洗棉套面，内芯为三维中空棉，回弹后不结块，尺寸 45 × 45 cm。',
 '水洗棉 可机洗', 89.00, 420, 1, 'http://localhost:9000/mall-product/products/13.jpg'),
(14, '久拾 金属可调阅读灯', '久拾', 1002,
 '铝合金灯臂三处可调，灯头可 180 度旋转，光源显色指数 Ra95。',
 'Ra95 高显色 多角度', 359.00, 96, 1, 'http://localhost:9000/mall-product/products/14.jpg'),
(15, '久拾 手工陶瓷杯组', '久拾', 1002,
 '手工拉坯成型，表面为哑光釉，四只一组，单只容量约 260 ml。',
 '手工拉坯 哑光釉', 168.00, 180, 1, 'http://localhost:9000/mall-product/products/15.jpg'),

-- 03 个护美妆
(16, '木序 琥珀精油', '木序', 1003,
 '琥珀色避光玻璃瓶，单方精油，可搭配香薰机或基础油稀释使用。',
 '避光瓶 单方精油', 159.00, 210, 1, 'http://localhost:9000/mall-product/products/16.jpg'),
(17, '木序 暖木香氛蜡烛', '木序', 1003,
 '大豆蜡基底，棉质烛芯，前调为雪松与橙皮，燃烧时长约 45 小时。',
 '大豆蜡 45 小时', 228.00, 140, 1, 'http://localhost:9000/mall-product/products/17.jpg'),
(18, '素陈 日常彩妆组合', '素陈', 1003,
 '含气垫底妆、腮红与唇釉三件，配色偏日常裸调，适合通勤。',
 '裸调配色 通勤套装', 399.00, 110, 1, 'http://localhost:9000/mall-product/products/18.jpg'),
(19, '木序 植物精油护理', '木序', 1003,
 '复方按摩油，含荷荷巴与甜杏仁基础油，适合沐浴后使用。',
 '复方按摩油 易吸收', 268.00, 130, 1, 'http://localhost:9000/mall-product/products/19.jpg'),
(20, '素陈 滴管精华', '素陈', 1003,
 '滴管取量，质地清爽不黏腻，适合早晚护肤的最后一步。',
 '清爽质地 滴管取量', 328.00, 165, 1, 'http://localhost:9000/mall-product/products/20.jpg'),

-- 04 服饰箱包
(21, '序白 棉麻日常穿搭组合', '序白', 1004,
 '棉麻混纺上衣与长裤两件套，版型宽松，适合春夏日常通勤。',
 '棉麻混纺 宽松版型', 589.00, 92, 1, 'http://localhost:9000/mall-product/products/21.jpg'),
(22, '序白 中性色亚麻长外套', '序白', 1004,
 '亚麻质感面料，落肩剪裁，中性色系易搭配，适合春秋外穿。',
 '亚麻质感 落肩剪裁', 729.00, 68, 1, 'http://localhost:9000/mall-product/products/22.jpg'),
(23, '序白 亚麻质感衬衫', '序白', 1004,
 '亚麻混纺面料，垂坠感好，洗后不易起球，适合四季叠穿。',
 '亚麻混纺 不易起球', 329.00, 156, 1, 'http://localhost:9000/mall-product/products/23.jpg'),
(24, '序白 基础款棉质上衣', '序白', 1004,
 '精梳棉面料，克重 220 g，领口为双针加固，久穿不变形。',
 '精梳棉 领口加固', 199.00, 280, 1, 'http://localhost:9000/mall-product/products/24.jpg'),
(25, '沐行 卡其帆布托特包', '沐行', 1004,
 '加厚帆布，可放入 14 英寸笔记本，内置两个分隔袋。',
 '加厚帆布 笔记本位', 219.00, 210, 1, 'http://localhost:9000/mall-product/products/25.jpg'),
(26, '沐行 深蓝通勤双肩包', '沐行', 1004,
 '防泼水面料，背部有行李箱固定带，主仓可容纳 16 英寸笔记本。',
 '防泼水 16 英寸兼容', 399.00, 175, 1, 'http://localhost:9000/mall-product/products/26.jpg'),
(27, '沐行 棕色皮质短钱包', '沐行', 1004,
 '头层牛皮，八个卡位加一个钞位，厚度约 1.8 cm。',
 '头层牛皮 八卡位', 259.00, 190, 1, 'http://localhost:9000/mall-product/products/27.jpg'),

-- 05 食品饮品
(28, '焙野 中深烘焙咖啡豆', '焙野', 1005,
 '中深烘焙，风味偏可可与坚果，适合意式浓缩与手冲，每袋 500 g。',
 '中深烘焙 500g', 128.00, 340, 1, 'http://localhost:9000/mall-product/products/28.jpg'),
(29, '焙野 手冲咖啡组合', '焙野', 1005,
 '含滤杯、分享壶与滤纸，滤杯为陶瓷材质，适合入门手冲。',
 '陶瓷滤杯 入门套装', 268.00, 88, 1, 'http://localhost:9000/mall-product/products/29.jpg'),
(30, '焙野 咖啡与早餐组合', '焙野', 1005,
 '挂耳咖啡十包装搭配燕麦片，适合办公室快节奏早餐。',
 '挂耳十包 早餐搭配', 158.00, 260, 1, 'http://localhost:9000/mall-product/products/30.jpg'),
(31, '焙野 红茶与黄油曲奇', '焙野', 1005,
 '锡兰红茶二十包，搭配手工黄油曲奇，曲奇为独立小包装。',
 '锡兰红茶 手工曲奇', 118.00, 300, 1, 'http://localhost:9000/mall-product/products/31.jpg'),
(32, '果园 当季水果组合', '果园', 1005,
 '当季水果混装约 3 kg，品种随季节调整，产地直发。',
 '当季混装 产地直发', 168.00, 140, 1, 'http://localhost:9000/mall-product/products/32.jpg'),
(33, '果园 轻食能量碗', '果园', 1005,
 '即食谷物与鹰嘴豆组合，每份约 320 g，冷藏配送。',
 '即食 高蛋白', 39.00, 260, 1, 'http://localhost:9000/mall-product/products/33.jpg'),
(34, '果园 鲜榨橙汁', '果园', 1005,
 '非浓缩还原橙汁，单瓶 1 L，全程冷链，开封后需冷藏并尽快饮用。',
 'NFC 冷链配送', 45.00, 320, 1, 'http://localhost:9000/mall-product/products/34.jpg'),

-- 06 生活方式
(40, '久拾 桌面多肉盆栽', '久拾', 1006,
 '多肉植物搭配陶瓷盆，耐阴少水，适合书桌与窗台。',
 '耐阴少水 含陶瓷盆', 68.00, 380, 1, 'http://localhost:9000/mall-product/products/40.jpg');
