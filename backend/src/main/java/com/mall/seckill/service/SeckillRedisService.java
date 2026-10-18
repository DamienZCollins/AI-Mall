package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillStock;
import com.mall.seckill.mapper.SeckillActivityMapper;
import com.mall.seckill.mapper.SeckillOrderMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 秒杀库存的 Redis 操作：预热、原子预扣、回补
 *
 * <p>Redis 是抢购流量的第一道闸门，数据库库存只在消费端扣减，作为最终一致性的落点
 */
@Service
public class SeckillRedisService {

    private static final Logger log = LoggerFactory.getLogger(SeckillRedisService.class);

    private static final String KEY_PREFIX = "mall:seckill:";
    private static final String DEDUCT_SCRIPT = "lua/seckill_deduct.lua";
    private static final String RESTORE_SCRIPT = "lua/seckill_restore.lua";

    /** 空集合在 Redis 中不占键，写入一个不会被命中的占位成员让键真实存在 */
    private static final String SET_PLACEHOLDER = "__init__";

    /** 活动结束后 Key 的保留时长，用于支持活动结束后的结果查询 */
    private static final Duration KEY_RETENTION_AFTER_END = Duration.ofDays(1);

    /** 预热锁的持有时间，超时自动释放，避免持锁线程异常退出后卡死后续预热 */
    private static final Duration PREHEAT_LOCK_TTL = Duration.ofSeconds(10);

    /** 未抢到预热锁时的等待上限与轮询间隔 */
    private static final long PREHEAT_WAIT_MILLIS = 2_000L;
    private static final long PREHEAT_POLL_MILLIS = 50L;

    private final StringRedisTemplate redisTemplate;
    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;
    private final SeckillOrderMapper orderMapper;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> restoreScript;

    public SeckillRedisService(StringRedisTemplate redisTemplate, SeckillActivityMapper activityMapper,
                               SeckillStockMapper stockMapper, SeckillOrderMapper orderMapper) {
        this.redisTemplate = redisTemplate;
        this.activityMapper = activityMapper;
        this.stockMapper = stockMapper;
        this.orderMapper = orderMapper;
        this.deductScript = loadScript(DEDUCT_SCRIPT);
        this.restoreScript = loadScript(RESTORE_SCRIPT);
    }

    /**
     * 把活动状态从数据库完整重建到 Redis：库存取秒杀库存表，买家集合按已存在的订单行回填
     *
     * <p>买家集合必须回填而不是清空：uk_user_activity 限定终身一单，
     * 清空后已下单的用户会再次通过预扣，消费端虽被唯一索引拦下，Redis 库存却已经扣掉，
     * 变成只减不还的静默泄漏
     *
     * <p>并发预热会互相删除对方刚写入的状态，用短锁收敛成一次
     *
     * @return 本次调用确实持有锁并完成重建返回 true；
     *         锁被其它线程持有、只能等待对方结果时返回 false，调用方需重试才能确保重建由自己完成
     */
    public boolean preheat(Long activityId) {
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(preheatLockKey(activityId), "1", PREHEAT_LOCK_TTL))) {
            // 等待持锁线程落地，让调用方紧接着的重试更容易成功；本次未完成重建
            awaitPreheated(activityId);
            return false;
        }
        try {
            rebuild(activityId);
            return true;
        } finally {
            redisTemplate.delete(preheatLockKey(activityId));
        }
    }

    private void rebuild(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        SeckillStock stock = stockMapper.selectOne(
                Wrappers.<SeckillStock>lambdaQuery().eq(SeckillStock::getActivityId, activityId));
        if (stock == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND.getCode(), "活动缺少库存记录");
        }
        List<Long> buyers = orderMapper.selectUserIdsByActivityId(activityId);

        Duration ttl = keyTtl(activity);
        redisTemplate.delete(allKeys(activityId));

        redisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(stock.getStock()), ttl);
        redisTemplate.opsForValue().set(startKey(activityId), String.valueOf(toMillis(activity.getStartTime())), ttl);
        redisTemplate.opsForValue().set(endKey(activityId), String.valueOf(toMillis(activity.getEndTime())), ttl);
        redisTemplate.opsForValue().set(totalKey(activityId), String.valueOf(activity.getTotalStock()), ttl);

        String buyersKey = buyersKey(activityId);
        redisTemplate.opsForSet().add(buyersKey, SET_PLACEHOLDER);
        if (!buyers.isEmpty()) {
            redisTemplate.opsForSet().add(buyersKey,
                    buyers.stream().map(String::valueOf).toArray(String[]::new));
        }
        redisTemplate.expire(buyersKey, ttl);

        log.info("活动 {} 预热完成，库存 {}，总量 {}，回填买家 {} 个",
                activityId, stock.getStock(), activity.getTotalStock(), buyers.size());
    }

    /**
     * 等待持锁线程预热完成；超时后由调用方的预扣逻辑决定如何响应
     *
     * @return 等待窗口内观察到缓存已就绪返回 true，超时或被中断返回 false
     */
    private boolean awaitPreheated(Long activityId) {
        long deadline = System.currentTimeMillis() + PREHEAT_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (isPreheated(activityId)) {
                return true;
            }
            try {
                Thread.sleep(PREHEAT_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public boolean isPreheated(Long activityId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(stockKey(activityId)));
    }

    /**
     * 读取 Redis 中的剩余库存，键不存在或值非法时返回 null
     *
     * <p>供库存对账使用，不做任何修改
     */
    public Integer readStock(Long activityId) {
        String value = redisTemplate.opsForValue().get(stockKey(activityId));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            log.warn("活动 {} 的 Redis 库存值非法：{}", activityId, value);
            return null;
        }
    }

    /**
     * 原子预扣，库存不足或用户已下单时脚本不做任何修改
     */
    public SeckillDeductResult deduct(Long activityId, Long userId) {
        Long result = redisTemplate.execute(deductScript,
                List.of(stockKey(activityId), buyersKey(activityId), startKey(activityId), endKey(activityId)),
                String.valueOf(System.currentTimeMillis()), String.valueOf(userId));
        return SeckillDeductResult.fromCode(result == null ? SeckillDeductResult.UNKNOWN.getCode() : result);
    }

    /**
     * 回补库存并移除买家标记，用于消息发送失败补偿与超时关单
     *
     * @return 是否真正回补成功
     */
    public boolean restore(Long activityId, Long userId) {
        Long result = redisTemplate.execute(restoreScript,
                List.of(stockKey(activityId), buyersKey(activityId)),
                userId == null ? "" : String.valueOf(userId),
                String.valueOf(maxStock(activityId)));
        if (result == null || result <= 0) {
            log.warn("活动 {} 回补未生效，返回值 {}，用户 {}", activityId, result, userId);
            return false;
        }
        log.info("活动 {} 回补库存，移除买家 {}", activityId, userId);
        return true;
    }

    /**
     * 只回补库存不移除买家标记，用于超时关单：uk_user_activity 限定终身一单，
     * 释放的库存留给其他用户，本人不能再抢
     */
    public boolean restoreStockOnly(Long activityId) {
        Long result = redisTemplate.execute(restoreScript,
                List.of(stockKey(activityId), buyersKey(activityId)),
                "", String.valueOf(maxStock(activityId)));
        if (result == null || result <= 0) {
            log.warn("活动 {} 库存回补未生效，返回值 {}", activityId, result);
            return false;
        }
        log.info("活动 {} 回补库存，买家标记保留", activityId);
        return true;
    }

    /**
     * 判断用户是否已占用库存，用于区分「排队中」与「未参与」
     */
    public boolean isBuyer(Long activityId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(buyersKey(activityId), String.valueOf(userId)));
    }

    public void evict(Long activityId) {
        redisTemplate.delete(allKeys(activityId));
    }

    private List<String> allKeys(Long activityId) {
        return List.of(stockKey(activityId), buyersKey(activityId), startKey(activityId),
                endKey(activityId), totalKey(activityId));
    }

    private int maxStock(Long activityId) {
        String total = redisTemplate.opsForValue().get(totalKey(activityId));
        if (total != null) {
            return Integer.parseInt(total);
        }
        SeckillActivity activity = activityMapper.selectById(activityId);
        return activity == null ? Integer.MAX_VALUE : activity.getTotalStock();
    }

    private Duration keyTtl(SeckillActivity activity) {
        LocalDateTime expireAt = activity.getEndTime().plus(KEY_RETENTION_AFTER_END);
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        return ttl.isNegative() ? KEY_RETENTION_AFTER_END : ttl;
    }

    private long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String stockKey(Long activityId) {
        return KEY_PREFIX + "stock:" + activityId;
    }

    private String buyersKey(Long activityId) {
        return KEY_PREFIX + "buyers:" + activityId;
    }

    private String startKey(Long activityId) {
        return KEY_PREFIX + "start:" + activityId;
    }

    private String endKey(Long activityId) {
        return KEY_PREFIX + "end:" + activityId;
    }

    private String totalKey(Long activityId) {
        return KEY_PREFIX + "total:" + activityId;
    }

    private String preheatLockKey(Long activityId) {
        return KEY_PREFIX + "preheat:lock:" + activityId;
    }

    private DefaultRedisScript<Long> loadScript(String path) {
        try {
            String script = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(script, Long.class);
        } catch (IOException e) {
            throw new UncheckedIOException("加载秒杀脚本失败：" + path, e);
        }
    }
}
