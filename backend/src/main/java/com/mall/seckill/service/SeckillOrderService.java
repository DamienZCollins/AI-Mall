package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillOrder;
import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.mapper.SeckillOrderMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import com.mall.seckill.mq.SeckillOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 抢购下单与支付，正确性由数据库约束保证：库存靠条件更新，去重靠唯一索引
 */
@Service
public class SeckillOrderService {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderService.class);
    private static final int MAX_TIMEOUT_SCAN = 200;

    private final SeckillOrderMapper orderMapper;
    private final SeckillStockMapper stockMapper;
    private final SeckillActivityService activityService;
    private final SeckillRedisService redisService;

    public SeckillOrderService(SeckillOrderMapper orderMapper, SeckillStockMapper stockMapper,
                               SeckillActivityService activityService, SeckillRedisService redisService) {
        this.orderMapper = orderMapper;
        this.stockMapper = stockMapper;
        this.activityService = activityService;
        this.redisService = redisService;
    }

    /**
     * 模拟支付回调，只有待支付订单能流转到已支付
     */
    @Transactional
    public SeckillOrderView pay(String orderNo) {
        SeckillOrder order = requireOrder(orderNo);
        SeckillOrderStatus current = SeckillOrderStatus.fromCode(order.getStatus());
        if (current == null || !current.canTransitionTo(SeckillOrderStatus.PAID)) {
            throw new BusinessException(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode(),
                    "当前状态为「" + (current == null ? "未知" : current.getDescription()) + "」，不能支付");
        }

        int affected = orderMapper.transferStatus(orderNo, SeckillOrderStatus.PENDING_PAYMENT.getCode(),
                SeckillOrderStatus.PAID.getCode(), LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL);
        }
        return SeckillOrderView.from(requireOrder(orderNo));
    }

    /**
     * 用户主动取消订单，仅限本人的待支付订单
     *
     * <p>取消与超时关单复用同一套状态转移与库存回补，条件更新成功才回补
     *
     * <p>订单按订单号与用户 ID 联合查询，「订单不存在」与「不属于本人」落到同一分支，
     * 不通过返回码差异泄露订单是否存在
     */
    @Transactional
    public SeckillOrderView cancel(String orderNo, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SeckillOrder order = requireOwnedOrder(orderNo, userId);
        SeckillOrderStatus current = SeckillOrderStatus.fromCode(order.getStatus());
        if (current == null || !current.canTransitionTo(SeckillOrderStatus.CANCELLED)) {
            throw new BusinessException(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode(),
                    "当前状态为「" + (current == null ? "未知" : current.getDescription()) + "」，不能取消");
        }

        int affected = orderMapper.transferStatus(orderNo, SeckillOrderStatus.PENDING_PAYMENT.getCode(),
                SeckillOrderStatus.CANCELLED.getCode(), null);
        if (affected == 0) {
            // 并发下已被支付或超时关单抢先流转，普通查询读到的是事务快照，改用加锁读定位实际状态
            Integer latestStatus = orderMapper.selectStatusForUpdate(orderNo);
            if (latestStatus == null) {
                throw new BusinessException(ResultCode.SECKILL_ORDER_NOT_FOUND);
            }
            SeckillOrderStatus latest = SeckillOrderStatus.fromCode(latestStatus);
            throw new BusinessException(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode(),
                    "当前状态为「" + (latest == null ? "未知" : latest.getDescription()) + "」，不能取消");
        }

        restoreStock(order);
        // 只回补库存不移除 Redis 买家标记：移除后用户能再次通过预扣，而 uk_user_activity 会拦下插入，
        // 释放的 Redis 库存不会再被回补，形成只减不还的静默泄漏
        restoreRedisAfterCommit(order);
        log.info("订单 {} 已取消并回补库存，用户 {} 活动 {}", orderNo, userId, order.getActivityId());
        return SeckillOrderView.from(requireOrder(orderNo));
    }

    @Transactional
    public SeckillOrderView getOrder(String orderNo) {
        return SeckillOrderView.from(requireOrder(orderNo));
    }

    /**
     * 消费端创建订单，库存已在 Redis 预扣，这里落库并同步扣减数据库库存
     *
     * <p>幂等依赖 uk_user_activity，重复消息由唯一索引拒绝后由调用方按已处理对待
     *
     * @throws BusinessException 数据库库存扣减失败，调用方应重投消息而非吞掉
     */
    @Transactional
    public SeckillOrder createFromMessage(SeckillOrderMessage message) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(message.orderNo());
        order.setUserId(message.userId());
        order.setActivityId(message.activityId());
        order.setProductId(message.productId());
        order.setProductName(message.productName());
        order.setSeckillPrice(new BigDecimal(message.seckillPrice()));
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        order.setExpireTime(LocalDateTime.now().plusMinutes(message.payTimeoutMinutes()));
        orderMapper.insert(order);

        // 数据库库存是最终一致性的落点，扣不动说明两侧不一致，抛出让消息重投
        if (stockMapper.deduct(message.activityId()) == 0) {
            log.error("数据库库存扣减失败，activityId={} orderNo={}", message.activityId(), message.orderNo());
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT.getCode(), "数据库库存扣减失败");
        }
        return order;
    }

    /**
     * 延迟消息到期后的关单：条件流转成功才回补，重复投递不会重复回补
     */
    @Transactional
    public void cancelByTimeout(String orderNo) {
        SeckillOrder order = orderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            log.info("订单 {} 不存在，无需关单", orderNo);
            return;
        }
        if (!Integer.valueOf(SeckillOrderStatus.PENDING_PAYMENT.getCode()).equals(order.getStatus())) {
            log.info("订单 {} 当前状态为 {}，无需关单", orderNo, order.getStatus());
            return;
        }
        int affected = orderMapper.transferStatus(orderNo, SeckillOrderStatus.PENDING_PAYMENT.getCode(),
                SeckillOrderStatus.TIMEOUT_CANCELLED.getCode(), null);
        if (affected == 0) {
            log.info("订单 {} 状态已被其它路径流转，跳过关单", orderNo);
            return;
        }
        restoreStock(order);
        // 库存还给活动，但买家标记保留：uk_user_activity 限定终身一单
        restoreRedisAfterCommit(order);
        log.info("订单 {} 已超时关单并回补库存", orderNo);
    }

    @Transactional(readOnly = true)
    public SeckillOrderView findByUserAndActivity(Long userId, Long activityId) {
        SeckillOrder order = orderMapper.selectOne(Wrappers.<SeckillOrder>lambdaQuery()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getActivityId, activityId));
        return order == null ? null : SeckillOrderView.from(order);
    }

    /**
     * 超时取消并回补库存，返回处理条数
     *
     * <p>状态流转用条件更新，影响行数为 1 才回补，保证任务重跑不会重复回补
     */
    @Transactional
    public int cancelExpiredOrders() {
        List<SeckillOrder> expired = orderMapper.selectList(Wrappers.<SeckillOrder>lambdaQuery()
                .eq(SeckillOrder::getStatus, SeckillOrderStatus.PENDING_PAYMENT.getCode())
                .lt(SeckillOrder::getExpireTime, LocalDateTime.now())
                .last("LIMIT " + MAX_TIMEOUT_SCAN));

        int cancelled = 0;
        for (SeckillOrder order : expired) {
            int affected = orderMapper.transferStatus(order.getOrderNo(),
                    SeckillOrderStatus.PENDING_PAYMENT.getCode(),
                    SeckillOrderStatus.TIMEOUT_CANCELLED.getCode(), null);
            if (affected == 0) {
                continue;
            }
            restoreStock(order);
            // 延迟消息丢失时靠本任务兜底；库存还回活动，买家标记保留：uk_user_activity 限定终身一单
            restoreRedisAfterCommit(order);
            cancelled++;
        }
        if (cancelled > 0) {
            log.info("超时取消订单 {} 条并回补库存", cancelled);
        }
        return cancelled;
    }

    /**
     * 事务提交后再回补 Redis：提交失败时订单和库存都留在原状态，
     * 避免库存先被回补而数据库又回滚，两侧状态不一致
     */
    private void restoreRedisAfterCommit(SeckillOrder order) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean restored = redisService.restoreStockOnly(order.getActivityId());
                if (!restored) {
                    log.warn("订单 {} 关单后 Redis 库存回补未生效，activityId={}",
                            order.getOrderNo(), order.getActivityId());
                }
            }
        });
    }

    private void restoreStock(SeckillOrder order) {
        SeckillActivity activity = activityService.requireActivity(order.getActivityId());
        int affected = stockMapper.restore(order.getActivityId(), activity.getTotalStock());
        if (affected == 0) {
            // 触发回补上限，说明库存已经回到活动总量，属于异常但不应阻断取消
            log.warn("订单 {} 回补库存未生效，活动 {} 库存已达上限 {}", order.getOrderNo(),
                    order.getActivityId(), activity.getTotalStock());
        }
    }

    private SeckillOrder requireOrder(String orderNo) {
        SeckillOrder order = orderNo == null ? null : orderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.SECKILL_ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 按订单号与用户 ID 联合查询，两种不匹配都返回订单不存在
     */
    private SeckillOrder requireOwnedOrder(String orderNo, Long userId) {
        SeckillOrder order = orderNo == null ? null : orderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery()
                        .eq(SeckillOrder::getOrderNo, orderNo)
                        .eq(SeckillOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.SECKILL_ORDER_NOT_FOUND);
        }
        return order;
    }
}
