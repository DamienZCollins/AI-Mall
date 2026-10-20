package com.mall.seckill;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.mq.SeckillOrderMessage;
import com.mall.seckill.service.SeckillOrderService;
import com.mall.support.IntegrationTestBase;
import com.mall.support.SeckillFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单落库与状态流转在真实 MySQL 上的行为：唯一索引、条件更新与库存回补
 */
class SeckillOrderIT extends IntegrationTestBase {

    @Autowired
    private SeckillOrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long productId;
    private long activityId;

    @BeforeEach
    void setUp() {
        SeckillFixture.clean(jdbcTemplate);
        productId = SeckillFixture.createProduct(jdbcTemplate, "订单测试商品", "测试品牌", 1001L, "399.00", 100, 1);
        activityId = SeckillFixture.createRunningActivity(jdbcTemplate, productId, "订单测试活动", 10, 15);
    }

    @Test
    @DisplayName("消费消息创建待支付订单并扣减数据库库存")
    void createFromMessageDeductsDatabaseStock() {
        SeckillOrderView view = SeckillOrderView.from(
                orderService.createFromMessage(message("SK-1", 1L)));

        assertThat(view.status()).isEqualTo(SeckillOrderStatus.PENDING_PAYMENT);
        assertThat(stock()).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order WHERE order_no = 'SK-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("同一用户同一活动的重复消息被唯一索引拦下")
    void duplicateMessageIsRejectedByUniqueKey() {
        orderService.createFromMessage(message("SK-1", 1L));

        assertThatThrownBy(() -> orderService.createFromMessage(message("SK-2", 1L)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    @DisplayName("数据库库存为 0 时下单失败，避免两侧不一致时继续落单")
    void createFailsWhenDatabaseStockExhausted() {
        jdbcTemplate.update("UPDATE seckill_stock SET stock = 0 WHERE activity_id = ?", activityId);

        assertThatThrownBy(() -> orderService.createFromMessage(message("SK-1", 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_SOLD_OUT.getCode());
        // 订单插入与库存扣减在同一事务，失败后订单不落库
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order WHERE order_no = 'SK-1'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("并发消费不同用户的消息时库存不会超卖")
    void concurrentCreateNeverOversells() throws Exception {
        jdbcTemplate.update("UPDATE seckill_stock SET stock = 5 WHERE activity_id = ?", activityId);

        int consumers = 40;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < consumers; i++) {
            long userId = 100L + i;
            String orderNo = "SK-" + userId;
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    orderService.createFromMessage(message(orderNo, userId));
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        long created = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                created++;
            }
        }
        executor.shutdown();

        assertThat(created).isEqualTo(5);
        assertThat(stock()).isZero();
        assertThat(orderCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("支付把待支付订单流转为已支付并记录支付时间")
    void payRecordsPayTime() {
        orderService.createFromMessage(message("SK-1", 1L));

        SeckillOrderView view = orderService.pay("SK-1");

        assertThat(view.status()).isEqualTo(SeckillOrderStatus.PAID);
        assertThat(view.payTime()).isNotNull();
    }

    @Test
    @DisplayName("已支付订单再次支付被状态机拒绝")
    void payIsNotRepeatable() {
        orderService.createFromMessage(message("SK-1", 1L));
        orderService.pay("SK-1");

        assertThatThrownBy(() -> orderService.pay("SK-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("取消订单回补数据库库存，且不能重复回补")
    void cancelRestoresOnce() {
        orderService.createFromMessage(message("SK-1", 1L));

        orderService.cancel("SK-1", 1L);
        assertThat(stock()).isEqualTo(10);

        assertThatThrownBy(() -> orderService.cancel("SK-1", 1L))
                .isInstanceOf(BusinessException.class);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("非本人订单无法取消，且不暴露订单是否存在")
    void cancelRejectsForeignOrder() {
        orderService.createFromMessage(message("SK-1", 1L));

        assertThatThrownBy(() -> orderService.cancel("SK-1", 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ORDER_NOT_FOUND.getCode());
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    @DisplayName("超时扫描只关闭已过期待支付订单并回补库存")
    void cancelExpiredOrdersRestoresStock() {
        orderService.createFromMessage(message("SK-expired", 1L));
        orderService.createFromMessage(message("SK-active", 2L));
        jdbcTemplate.update("UPDATE seckill_order SET expire_time = ? WHERE order_no = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), "SK-expired");

        int cancelled = orderService.cancelExpiredOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(statusOf("SK-expired")).isEqualTo(SeckillOrderStatus.TIMEOUT_CANCELLED.getCode());
        assertThat(statusOf("SK-active")).isEqualTo(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    @DisplayName("超时扫描重复执行不会重复回补")
    void cancelExpiredOrdersIsIdempotent() {
        orderService.createFromMessage(message("SK-expired", 1L));
        jdbcTemplate.update("UPDATE seckill_order SET expire_time = ? WHERE order_no = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), "SK-expired");

        orderService.cancelExpiredOrders();
        int secondRun = orderService.cancelExpiredOrders();

        assertThat(secondRun).isZero();
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("取消或超时关单后，同一用户不能再次下单")
    void cancelledUserCannotBuyAgain() {
        orderService.createFromMessage(message("SK-1", 1L));
        orderService.cancel("SK-1", 1L);

        assertThatThrownBy(() -> orderService.createFromMessage(message("SK-2", 1L)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private SeckillOrderMessage message(String orderNo, long userId) {
        return new SeckillOrderMessage(orderNo, userId, activityId, productId, "订单测试商品", "99.00", 15);
    }

    private int stock() {
        return jdbcTemplate.queryForObject("SELECT stock FROM seckill_stock WHERE activity_id = ?",
                Integer.class, activityId);
    }

    private int orderCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seckill_order WHERE activity_id = ?",
                Integer.class, activityId);
    }

    private int statusOf(String orderNo) {
        return jdbcTemplate.queryForObject("SELECT status FROM seckill_order WHERE order_no = ?",
                Integer.class, orderNo);
    }
}
