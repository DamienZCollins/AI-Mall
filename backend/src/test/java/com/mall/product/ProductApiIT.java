package com.mall.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.result.ResultCode;
import com.mall.support.ApiITBase;
import com.mall.support.SeckillFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ProductApiIT extends ApiITBase {

    private long onSaleId;
    private long offSaleId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        SeckillFixture.clean(jdbcTemplate);
        long adminId = createUser("admin", "ADMIN");
        long userId = createUser("alice", "USER");
        adminToken = tokenFor(adminId, "admin", "ADMIN");
        userToken = tokenFor(userId, "alice", "USER");
        onSaleId = SeckillFixture.createProduct(jdbcTemplate, "上架商品", "沃兰", 1004L,
                "399.00", 10, 1);
        offSaleId = SeckillFixture.createProduct(jdbcTemplate, "下架商品", "青岚", 1002L,
                "499.00", 10, 0);
    }

    @Test
    @DisplayName("用户商品列表只返回上架商品")
    void publicListOnlyContainsOnSaleProducts() throws Exception {
        JsonNode body = getJson("/api/products");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(body.path("data").path("list").get(0).path("id").asLong()).isEqualTo(onSaleId);
    }

    @Test
    @DisplayName("用户查询下架商品返回 2001")
    void offSaleDetailIsNotFound() throws Exception {
        JsonNode body = getJson("/api/products/" + offSaleId);

        assertThat(body.path("code").asInt()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("管理员新增商品默认下架，用户侧不可见")
    void adminCreateStartsOffSale() throws Exception {
        JsonNode created = postJson("/api/admin/products", adminToken, Map.of(
                "name", "新品", "brand", "声屿", "categoryId", 1001,
                "price", "199.00", "stock", 5));

        assertThat(created.path("code").asInt()).isZero();
        long id = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("status").asInt()).isZero();

        JsonNode detail = getJson("/api/products/" + id);
        assertThat(detail.path("code").asInt()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("上架后用户侧可见，重复上架返回 2002")
    void changeStatusTogglesVisibility() throws Exception {
        JsonNode patched = perform(json(patch("/api/admin/products/" + offSaleId + "/status")
                .header("Authorization", "Bearer " + adminToken), "{\"status\":1}"));

        assertThat(patched.path("code").asInt()).isZero();
        assertThat(getJson("/api/products/" + offSaleId).path("code").asInt()).isZero();

        JsonNode again = perform(json(patch("/api/admin/products/" + offSaleId + "/status")
                .header("Authorization", "Bearer " + adminToken), "{\"status\":1}"));
        assertThat(again.path("code").asInt()).isEqualTo(ResultCode.PRODUCT_STATUS_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("普通用户访问管理端接口返回 403")
    void nonAdminIsForbidden() throws Exception {
        int status = mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("未登录访问管理端接口返回 401")
    void anonymousAdminCallIsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/api/admin/products")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("售价非法时参数校验返回 400")
    void invalidPriceIsRejected() throws Exception {
        int status = mockMvc.perform(json(put("/api/admin/products/" + onSaleId)
                        .header("Authorization", "Bearer " + adminToken), "{\"price\":\"0\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("编辑商品后返回最新字段，未传字段保持原值")
    void updateAppliesProvidedFields() throws Exception {
        JsonNode body = perform(json(put("/api/admin/products/" + onSaleId)
                .header("Authorization", "Bearer " + adminToken), "{\"name\":\"改名商品\"}"));

        assertThat(body.path("data").path("name").asText()).isEqualTo("改名商品");
        assertThat(body.path("data").path("price").decimalValue()).isEqualByComparingTo("399.00");
        assertThat(body.path("data").path("brand").asText()).isEqualTo("沃兰");
    }

    @Test
    @DisplayName("分页参数越界返回 400")
    void invalidPaginationIsRejected() throws Exception {
        int status = mockMvc.perform(get("/api/products").param("size", "101"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
    }
}
