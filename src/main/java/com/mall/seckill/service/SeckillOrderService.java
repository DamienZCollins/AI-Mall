package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillOrder;
import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.mapper.SeckillOrderMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抢购下单与支付，正确性由数据库约束保证：库存靠条件更新，去重靠唯一索引
 */
@Service
public class SeckillOrderService {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderService.class);
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_TIMEOUT_SCAN = 200;

    private final SeckillOrderMapper orderMapper;
    private final SeckillStockMapper stockMapper;
    private final SeckillActivityService activityService;
    private final ProductService productService;

    public SeckillOrderService(SeckillOrderMapper orderMapper, SeckillStockMapper stockMapper,
                               SeckillActivityService activityService, ProductService productService) {
        this.orderMapper = orderMapper;
        this.stockMapper = stockMapper;
        this.activityService = activityService;
        this.productService = productService;
    }

    /**
     * 同步下单：先插入订单占位，再条件扣减库存，扣减失败则整个事务回滚
     *
     * <p>顺序不能颠倒：先扣库存的话，重复下单被唯一索引拦下时库存已经扣掉，会造成白扣
     */
    @Transactional
    public SeckillOrderView placeOrder(Long activityId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SeckillActivity activity = activityService.requireActivity(activityId);
        activityService.requireRunning(activity);

        ProductView product = productService.getById(activity.getProductId());
        SeckillOrder order = buildOrder(activity, product, userId);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // uk_user_activity 唯一索引拦截重复下单，不依赖先查后插
            throw new BusinessException(ResultCode.SECKILL_DUPLICATE_ORDER);
        }

        // 影响行数为 0 表示库存已耗尽，抛异常让订单插入一起回滚
        if (stockMapper.deduct(activityId) == 0) {
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }

        log.info("秒杀下单成功，orderNo={}, userId={}, activityId={}", order.getOrderNo(), userId, activityId);
        return SeckillOrderView.from(order);
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

    @Transactional
    public SeckillOrderView getOrder(String orderNo) {
        return SeckillOrderView.from(requireOrder(orderNo));
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
            cancelled++;
        }
        if (cancelled > 0) {
            log.info("超时取消订单 {} 条并回补库存", cancelled);
        }
        return cancelled;
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

    private SeckillOrder buildOrder(SeckillActivity activity, ProductView product, Long userId) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setActivityId(activity.getId());
        order.setProductId(product.id());
        order.setProductName(product.name());
        order.setSeckillPrice(activity.getSeckillPrice());
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        order.setExpireTime(LocalDateTime.now().plusMinutes(activity.getPayTimeoutMinutes()));
        return order;
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
     * 订单号：SK + 时间戳 + 四位随机数
     */
    private String generateOrderNo() {
        return "SK" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
