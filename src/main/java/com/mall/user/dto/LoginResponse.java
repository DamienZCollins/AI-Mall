package com.mall.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应
 */
@Schema(description = "登录响应")
public record LoginResponse(

        @Schema(description = "JWT 令牌，后续请求放在 Authorization: Bearer 中")
        String token,

        @Schema(description = "令牌有效期，单位秒")
        long expiresIn,

        @Schema(description = "用户信息")
        UserInfo user
) {
}
