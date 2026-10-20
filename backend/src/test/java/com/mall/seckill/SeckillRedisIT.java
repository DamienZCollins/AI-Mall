package com.mall.seckill;

import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.service.SeckillDeductResult;
import com.mall.seckill.service.SeckillRedisService;
import com.mall.support.IntegrationTestBase;
import com.mall.support.SeckillFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Lua 预扣脚本在真实 Redis 上的语义：原子性、去重、时间窗口与回补上限
 */
class SeckillRedisIT extends IntegrationTestBase {

    @Autowired
    private SeckillRedisService redisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long productId;

    @BeforeEach
    void setUp() {
        SeckillFixture.clean(jdbcTemplate);
        productId = SeckillFixture.createProduct(jdbcTemplate, "秒杀测试商品", "测试品牌", 1001L,
                "399.00", 100, 1);
    }

    @Test
    @DisplayName("预热把数据库库存与已下单买家写入 Redis")
    void preheatLoadsStockAndBuyers() {
        long activityId = SeckillFixture.createRunningActivity(jdbcTemplate, productId, "预热活动", 5, 15);
        insertOrder(activityId, 101L, SeckillOrderStatus.PENDING_PAYMENT);

        assertThat(redisService.preheat(activityId)).isTrue();

        assertThat(redisService.isPreheated(activityId)).isTrue();
        assertThat(redisService.readStock(activityId)).isEqualTo(5);
        assertThat(redisService.isBuyer(activityId, 101L)).isTrue();
        assertThat(redisService.isBuyer(activityId, 102L)).isFalse();
    }

    @Test
    @DisplayName("预扣成功后库存减一，同一用户再次预扣返回重复下单")
    void deductIsAtomicAndDeduplicated() {
        long activityId = runningActivity(5);
        redisService.preheat(activityId);

        assertThat(redisService.deduct(activityId, 1L)).isEqualTo(SeckillDeductResult.SUCCESS);
        assertThat(redisService.readStock(activityId)).isEqualTo(4);
        assertThat(redisService.deduct(activityId, 1L)).isEqualTo(SeckillDeductResult.DUPLICATE);
        assertThat(redisService.readStock(activityId)).isEqualTo(4);
    }

    @Test
    @DisplayName("库存扣到 0 后返回售罄，库存不会被扣成负数")
    void deductStopsAtZero() {
        long activityId = runningActivity(2);
        redisService.preheat(activityId);

        assertThat(redisService.deduct(activityId, 1L)).isEqualTo(SeckillDeductResult.SUCCESS);
        assertThat(redisService.deduct(activityId, 2L)).isEqualTo(SeckillDeductResult.SUCCESS);
        assertThat(redisService.deduct(activityId, 3L)).isEqualTo(SeckillDeductResult.SOLD_OUT);
        assertThat(redisService.readStock(activityId)).isZero();
    }

    @Test
    @DisplayName("并发抢购同一份库存时不超卖，成功次数等于库存总量")
    void concurrentDeductNeverOversells() throws Exception {
        int stock = 20;
        int users = 200;
        long activityId = runningActivity(stock);
        redisService.preheat(activityId);

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<SeckillDeductResult>> tasks = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            long userId = 1000L + i;
            tasks.add(() -> {
                start.await();
                return redisService.deduct(activityId, userId);
            });
        }
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();
        for (Callable<SeckillDeductResult> task : tasks) {
            futures.add(executor.submit(task));
        }
        start.countDown();

        long success = 0;
        for (Future<SeckillDeductResult> future : futures) {
            if (future.get() == SeckillDeductResult.SUCCESS) {
                success++;
            }
        }
        executor.shutdown();

        assertThat(success).isEqualTo(stock);
        assertThat(redisService.readStock(activityId)).isZero();
    }

    @Test
    @DisplayName("同一用户并发请求只成功一次")
    void concurrentSameUserSucceedsOnce() throws Exception {
        long activityId = runningActivity(50);
        redisService.preheat(activityId);

        int requests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return redisService.deduct(activityId, 7L);
            }));
        }
        start.countDown();

        long success = 0;
        for (Future<SeckillDeductResult> future : futures) {
            if (future.get() == SeckillDeductResult.SUCCESS) {
                success++;
            }
        }
        executor.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(redisService.readStock(activityId)).isEqualTo(49);
    }

    @Test
    @DisplayName("活动开始前与结束后都不能预扣")
    void deductRespectsTimeWindow() {
        long notStarted = activityWithWindow(LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        long ended = activityWithWindow(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        redisService.preheat(notStarted);
        redisService.preheat(ended);

        assertThat(redisService.deduct(notStarted, 1L)).isEqualTo(SeckillDeductResult.NOT_STARTED);
        assertThat(redisService.deduct(ended, 1L)).isEqualTo(SeckillDeductResult.ENDED);
    }

    @Test
    @DisplayName("未预热时预扣返回未预热，脚本不做任何修改")
    void deductWithoutPreheatIsRejected() {
        long activityId = runningActivity(5);

        assertThat(redisService.deduct(activityId, 1L)).isEqualTo(SeckillDeductResult.NOT_PREHEATED);
    }

    @Test
    @DisplayName("回补库存并移除买家标记后，该用户可以重新预扣")
    void restoreReturnsStockAndBuyerSlot() {
        long activityId = runningActivity(3);
        redisService.preheat(activityId);
        redisService.deduct(activityId, 1L);

        assertThat(redisService.restore(activityId, 1L)).isTrue();

        assertThat(redisService.readStock(activityId)).isEqualTo(3);
        assertThat(redisService.isBuyer(activityId, 1L)).isFalse();
        assertThat(redisService.deduct(activityId, 1L)).isEqualTo(SeckillDeductResult.SUCCESS);
    }

    @Test
    @DisplayName("只回补库存时买家标记保留，库存不会超过活动总量")
    void restoreStockOnlyKeepsBuyerAndCapsAtTotal() {
        long activityId = runningActivity(2);
        redisService.preheat(activityId);
        redisService.deduct(activityId, 1L);

        assertThat(redisService.restoreStockOnly(activityId)).isTrue();
        assertThat(redisService.readStock(activityId)).isEqualTo(2);
        assertThat(redisService.isBuyer(activityId, 1L)).isTrue();
        // 已经回到总量上限，再次回补不生效
        assertThat(redisService.restoreStockOnly(activityId)).isFalse();
        assertThat(redisService.readStock(activityId)).isEqualTo(2);
    }

    @Test
    @DisplayName("重新预热按数据库重建，覆盖运行期间的 Redis 状态")
    void preheatRebuildsFromDatabase() {
        long activityId = runningActivity(5);
        redisService.preheat(activityId);
        redisService.deduct(activityId, 1L);
        jdbcTemplate.update("UPDATE seckill_stock SET stock = 4 WHERE activity_id = ?", activityId);

        assertThat(redisService.preheat(activityId)).isTrue();

        assertThat(redisService.readStock(activityId)).isEqualTo(4);
        assertThat(redisService.isBuyer(activityId, 1L)).isFalse();
    }

    @Test
    @DisplayName("清理活动缓存后所有相关键消失")
    void evictRemovesAllKeys() {
        long activityId = runningActivity(5);
        redisService.preheat(activityId);

        redisService.evict(activityId);

        assertThat(redisService.isPreheated(activityId)).isFalse();
        assertThat(redisTemplate.keys("mall:seckill:*:" + activityId)).isEmpty();
    }

    private long runningActivity(int totalStock) {
        return SeckillFixture.createRunningActivity(jdbcTemplate, productId, "并发测试活动", totalStock, 15);
    }

    private long activityWithWindow(LocalDateTime start, LocalDateTime end) {
        jdbcTemplate.update("INSERT INTO seckill_activity "
                        + "(product_id, title, seckill_price, total_stock, per_user_limit, start_time, end_time, "
                        + "pay_timeout_minutes, status) VALUES (?, ?, ?, ?, 1, ?, ?, 15, 1)",
                productId, "时间窗口活动", new java.math.BigDecimal("99.00"), 5,
                Timestamp.valueOf(start), Timestamp.valueOf(end));
        Long activityId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO seckill_stock (activity_id, stock) VALUES (?, 5)", activityId);
        return activityId;
    }

    private void insertOrder(long activityId, long userId, SeckillOrderStatus status) {
        jdbcTemplate.update("INSERT INTO seckill_order (order_no, user_id, activity_id, product_id, product_name, "
                        + "seckill_price, status, expire_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "SK-" + activityId + "-" + userId, userId, activityId, productId, "秒杀测试商品",
                new java.math.BigDecimal("99.00"), status.getCode(), Timestamp.valueOf(LocalDateTime.now().plusMinutes(15)));
    }
}
