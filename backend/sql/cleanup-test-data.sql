-- 清理端到端与联调过程产生的测试数据，恢复到种子状态
DELETE FROM `seckill_order`;
DELETE FROM `seckill_stock`;
DELETE FROM `seckill_activity`;
ALTER TABLE `seckill_order` AUTO_INCREMENT = 1;
ALTER TABLE `seckill_stock` AUTO_INCREMENT = 1;
ALTER TABLE `seckill_activity` AUTO_INCREMENT = 1;
