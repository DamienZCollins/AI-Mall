package com.mall.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证与授权失败时按统一返回体写响应
 */
public final class SecurityErrorWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityErrorWriter() {
    }

    /**
     * 未认证或令牌无效
     */
    public static void writeUnauthorized(HttpServletResponse response) throws IOException {
        write(response, Result.failure(ResultCode.UNAUTHORIZED));
    }

    /**
     * 已认证但权限不足
     */
    public static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, Result.failure(ResultCode.FORBIDDEN));
    }

    private static void write(HttpServletResponse response, Result<?> result) throws IOException {
        response.setStatus(result.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
