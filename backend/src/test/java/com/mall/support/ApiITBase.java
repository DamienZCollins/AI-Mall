package com.mall.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 接口层集成测试：通过 MockMvc 走完整的过滤链与参数校验，鉴权使用真实签发的令牌
 */
public abstract class ApiITBase extends IntegrationTestBase {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 签发测试令牌：过滤链会按用户 ID 回查数据库，调用前需保证用户已存在
     */
    protected String tokenFor(long userId, String username, String role) {
        return jwtUtil.generate(userId, username, role);
    }

    protected long createUser(String username, String role) {
        return SeckillFixture.createUser(jdbcTemplate, username, "$2a$10$test.test.test.test.test.test.test.test.test",
                role, 1);
    }

    protected JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? JSON.createObjectNode() : JSON.readTree(body);
    }

    protected JsonNode getJson(String url) throws Exception {
        return perform(get(url));
    }

    protected JsonNode getJson(String url, String token) throws Exception {
        return perform(get(url).header("Authorization", "Bearer " + token));
    }

    protected JsonNode postJson(String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8);
        if (body != null) {
            builder.content(JSON.writeValueAsString(body));
        }
        return perform(builder);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(body);
    }

    protected static int httpStatus(MockHttpServletRequestBuilder request, MockMvc mockMvc) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }
}
