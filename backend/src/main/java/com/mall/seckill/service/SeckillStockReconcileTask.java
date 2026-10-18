package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.mapper.SeckillActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存对账：核对已结束活动的 Redis 库存与 seckill_stock.stock，不一致只告警
 *
 * <p>只核对已结束的活动：进行中的活动存在「Redis 已扣、MQ 尚未消费」的正常中间态，
 * 比对会把中间态误判为异常
 *
 * <p>不做自动修复：两侧都可能才是正确的一侧，修复方向需人工确认，
 * 由管理员调用 POST /api/admin/seckill/activities/{id}/preheat 全量重建后再观察
 */
@Component
@ConditionalOnProperty(name = "mall.seckill.stock-reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class SeckillStockReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockReconcileTask.class);

    private static final long FIXED_DELAY_MS = 300_000L;
    private static final long INITIAL_DELAY_MS = 60_000L;

    /** 单轮扫描的活动数上限，按 ID 倒序取最近结束的活动 */
    private static final int MAX_SCAN = 200;

    /**
     * 结束后的观察冷却期：活动刚结束时可能仍有消息在 MQ 中排队，
     * 预留一段时间等消费追平，避免把正常关闭过程误判为不一致
     */
    private static final Duration SETTLE_AFTER_END = Duration.ofMinutes(5);

    private final SeckillActivityMapper activityMapper;
    private final SeckillActivityService activityService;
    private final SeckillRedisService redisService;

    public SeckillStockReconcileTask(SeckillActivityMapper activityMapper,
                                     SeckillActivityService activityService,
                                     SeckillRedisService redisService) {
        this.activityMapper = activityMapper;
        this.activityService = activityService;
        this.redisService = redisService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = INITIAL_DELAY_MS)
    public void reconcile() {
        try {
            doReconcile();
        } catch (Exception e) {
            // 对账是旁路任务，数据库或 Redis 抖动不应影响其它定时任务
            log.warn("库存对账执行失败：{}", e.getMessage());
        }
    }

    private void doReconcile() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities = activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                .lt(SeckillActivity::getEndTime, now.minus(SETTLE_AFTER_END))
                .orderByDesc(SeckillActivity::getId)
                .last("LIMIT " + MAX_SCAN));

        int scanned = 0;
        int mismatched = 0;
        for (SeckillActivity activity : activities) {
            Integer redisStock = redisService.readStock(activity.getId());
            if (redisStock == null) {
                // Redis 键已过期或被淘汰，没有可比对的基准
                continue;
            }
            Integer dbStock = activityService.remainingStock(activity.getId());
            if (dbStock == null) {
                log.error("活动 {} 缺少库存记录，Redis 库存 {}", activity.getId(), redisStock);
                continue;
            }
            scanned++;
            if (!dbStock.equals(redisStock)) {
                mismatched++;
                log.error("秒杀库存不一致，activityId={} Redis={} DB={}，需人工确认后重建预热",
                        activity.getId(), redisStock, dbStock);
            }
        }

        if (mismatched > 0) {
            log.error("库存对账发现 {} 个活动不一致，本轮比对 {} 个已结束活动", mismatched, scanned);
        }
    }
}
