package com.mall.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.result.ResultCode;
import com.mall.support.ApiITBase;
import com.mall.support.SeckillFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthApiIT extends ApiITBase {

    @Autowired
    private PasswordEncoder passwordEncoder;

    private long userId;

    @BeforeEach
    void setUp() {
        SeckillFixture.clean(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM sys_user");
        userId = SeckillFixture.createUser(jdbcTemplate, "alice", passwordEncoder.encode("secret"), "USER", 1);
    }

    @Test
    @DisplayName("登录成功返回令牌与用户信息，令牌可用于访问受保护接口")
    void loginIssuesUsableToken() throws Exception {
        JsonNode login = postJson("/api/auth/login", null, Map.of("username", "alice", "password", "secret"));

        assertThat(login.path("code").asInt()).isZero();
        String token = login.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        JsonNode me = getJson("/api/auth/me", token);
        assertThat(me.path("code").asInt()).isZero();
        assertThat(me.path("data").path("username").asText()).isEqualTo("alice");
        assertThat(me.path("data").has("password")).isFalse();
    }

    @Test
    @DisplayName("密码错误返回业务码 1001")
    void wrongPasswordReturnsLoginFailed() throws Exception {
        JsonNode body = postJson("/api/auth/login", null, Map.of("username", "alice", "password", "wrong"));

        assertThat(body.path("code").asInt()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
    }

    @Test
    @DisplayName("用户不存在与密码错误返回同一业务码")
    void unknownUserReturnsSameCode() throws Exception {
        JsonNode body = postJson("/api/auth/login", null, Map.of("username", "nobody", "password", "secret"));

        assertThat(body.path("code").asInt()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
    }

    @Test
    @DisplayName("参数校验失败返回 400")
    void blankUsernameIsRejected() throws Exception {
        int status = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void missingTokenIsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("伪造签名的令牌返回 401")
    void forgedTokenIsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer forged.token.value"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("禁用账号无法登录，返回 1003")
    void disabledUserCannotLogin() throws Exception {
        jdbcTemplate.update("UPDATE sys_user SET status = 0 WHERE id = ?", userId);

        JsonNode body = postJson("/api/auth/login", null, Map.of("username", "alice", "password", "secret"));

        assertThat(body.path("code").asInt()).isEqualTo(ResultCode.USER_DISABLED.getCode());
    }
}
