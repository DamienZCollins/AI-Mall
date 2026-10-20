package com.mall.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 秒杀相关测试数据：直接写库，避免通过业务接口创建时触发的预热与事件
 */
public final class SeckillFixture {

    private SeckillFixture() {
    }

    /**
     * 清空秒杀相关表，测试之间互不影响；容器按测试类独立启动，不涉及并发冲突
     */
    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM seckill_order");
        jdbcTemplate.update("DELETE FROM seckill_stock");
        jdbcTemplate.update("DELETE FROM seckill_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    /**
     * 写入一个进行中的秒杀活动及其库存
     *
     * @return 活动 ID
     */
    public static long createRunningActivity(JdbcTemplate jdbcTemplate, long productId, String title,
                                             int totalStock, int payTimeoutMinutes) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO seckill_activity "
                        + "(product_id, title, seckill_price, total_stock, per_user_limit, start_time, end_time, "
                        + "pay_timeout_minutes, status) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 1)",
                productId, title, new BigDecimal("99.00"), totalStock,
                Timestamp.valueOf(now.minusHours(1)), Timestamp.valueOf(now.plusHours(1)), payTimeoutMinutes);
        Long activityId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO seckill_stock (activity_id, stock) VALUES (?, ?)", activityId, totalStock);
        return activityId;
    }

    public static long createProduct(JdbcTemplate jdbcTemplate, String name, String brand, long categoryId,
                                     String price, int stock, int status) {
        jdbcTemplate.update("INSERT INTO product (name, brand, category_id, description, selling_point, "
                        + "price, stock, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name, brand, categoryId, "测试描述", "测试卖点", new BigDecimal(price), stock, status);
        return lastInsertId(jdbcTemplate);
    }

    public static long createUser(JdbcTemplate jdbcTemplate, String username, String passwordHash,
                                  String role, int status) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, nickname, role, status) VALUES (?, ?, ?, ?, ?)",
                username, passwordHash, username, role, status);
        return lastInsertId(jdbcTemplate);
    }

    private static long lastInsertId(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
