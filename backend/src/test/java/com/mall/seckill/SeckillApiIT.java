package com.mall.seckill;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.result.ResultCode;
import com.mall.seckill.service.SeckillRedisService;
import com.mall.support.ApiITBase;
import com.mall.support.SeckillFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 秒杀接口：活动查询、管理端校验、鉴权入口
 *
 * <p>抢购主链路依赖 RocketMQ，MQ 相关分支由单元测试覆盖；这里验证不经过消息队列的接口行为
 */
class SeckillApiIT extends ApiITBase {

    @Autowired
    private SeckillRedisService redisService;

    private long productId;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        SeckillFixture.clean(jdbcTemplate);
        productId = SeckillFixture.createProduct(jdbcTemplate, "秒杀商品", "沃兰", 1004L, "399.00", 100, 1);
        long userId = createUser("alice", "USER");
        long adminId = createUser("admin", "ADMIN");
        userToken = tokenFor(userId, "alice", "USER");
        adminToken = tokenFor(adminId, "admin", "ADMIN");
    }

    @Test
    @DisplayName("活动列表无需登录，返回剩余库存与进行中状态")
    void activityListIsPublic() throws Exception {
        long activityId = SeckillFixture.createRunningActivity(jdbcTemplate, productId, "公开活动", 5, 15);

        JsonNode body = getJson("/api/seckill/activities?runningOnly=true");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asLong()).isEqualTo(1);
        JsonNode activity = body.path("data").path("list").get(0);
        assertThat(activity.path("id").asLong()).isEqualTo(activityId);
        assertThat(activity.path("remainingStock").asInt()).isEqualTo(5);
        assertThat(activity.path("status").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("runningOnly 过滤掉未开始的活动")
    void runningOnlyExcludesNotStarted() throws Exception {
        jdbcTemplate.update("INSERT INTO seckill_activity (product_id, title, seckill_price, total_stock, "
                        + "per_user_limit, start_time, end_time, pay_timeout_minutes, status) "
                        + "VALUES (?, '未开始活动', 99.00, 5, 1, DATE_ADD(NOW(), INTERVAL 1 HOUR), "
                        + "DATE_ADD(NOW(), INTERVAL 2 HOUR), 15, 1)", productId);

        JsonNode body = getJson("/api/seckill/activities?runningOnly=true");

        assertThat(body.path("data").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("查询不存在的活动返回 4001")
    void missingActivityIsNotFound() throws Exception {
        assertThat(getJson("/api/seckill/activities/999999").path("code").asInt())
                .isEqualTo(ResultCode.SECKILL_ACTIVITY_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("未登录抢购返回 401")
    void anonymousPurchaseIsUnauthorized() throws Exception {
        long activityId = SeckillFixture.createRunningActivity(jdbcTemplate, productId, "抢购活动", 5, 15);

        int status = mockMvc.perform(post("/api/seckill/" + activityId)).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("未抢购的用户查询结果返回未抢到；占用库存后返回排队中")
    void resultReflectsBuyerState() throws Exception {
        long activityId = SeckillFixture.createRunningActivity(jdbcTemplate, productId, "结果活动", 5, 15);

        JsonNode before = getJson("/api/seckill/" + activityId + "/result", userToken);
        assertThat(before.path("data").path("state").asText()).isEqualTo("FAILED");

        long aliceId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'alice'", Long.class);
        redisService.preheat(activityId);
        redisService.deduct(activityId, aliceId);

        JsonNode after = getJson("/api/seckill/" + activityId + "/result", userToken);
        assertThat(after.path("data").path("state").asText()).isEqualTo("QUEUING");
    }

    @Test
    @DisplayName("秒杀价不低于原价时返回 4009")
    void adminCreateRejectsPriceNotLowerThanOriginal() throws Exception {
        JsonNode body = postJson("/api/admin/seckill/activities", adminToken, Map.of(
                "productId", productId,
                "title", "非法价格活动",
                "seckillPrice", "399.00",
                "totalStock", 5,
                "startTime", "2026-10-20T10:00:00",
                "endTime", "2026-10-20T12:00:00"));

        assertThat(body.path("code").asInt()).isEqualTo(ResultCode.SECKILL_PRICE_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("创建活动同时初始化库存，管理员可手动预热")
    void adminCreateInitializesStockAndPreheat() throws Exception {
        JsonNode created = postJson("/api/admin/seckill/activities", adminToken, Map.of(
                "productId", productId,
                "title", "正常活动",
                "seckillPrice", "199.00",
                "totalStock", 8,
                "startTime", "2026-10-20T10:00:00",
                "endTime", "2026-10-20T12:00:00"));

        assertThat(created.path("code").asInt()).isZero();
        long activityId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("remainingStock").asInt()).isEqualTo(8);

        JsonNode preheated = postJson("/api/admin/seckill/activities/" + activityId + "/preheat", adminToken, null);
        assertThat(preheated.path("code").asInt()).isZero();
        assertThat(redisService.readStock(activityId)).isEqualTo(8);
    }

    @Test
    @DisplayName("普通用户调用管理端秒杀接口返回 403")
    void nonAdminCannotCreateActivity() throws Exception {
        // 请求体合法，确保走到方法级授权而不是参数校验
        int status = mockMvc.perform(json(post("/api/admin/seckill/activities")
                        .header("Authorization", "Bearer " + userToken),
                        "{\"productId\":" + productId + ",\"title\":\"活动\",\"seckillPrice\":\"199.00\","
                                + "\"totalStock\":5,\"startTime\":\"2026-10-20T10:00:00\","
                                + "\"endTime\":\"2026-10-20T12:00:00\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("订单不存在时查询返回 4006")
    void missingOrderIsNotFound() throws Exception {
        assertThat(getJson("/api/seckill/orders/missing", userToken).path("code").asInt())
                .isEqualTo(ResultCode.SECKILL_ORDER_NOT_FOUND.getCode());
    }
}
