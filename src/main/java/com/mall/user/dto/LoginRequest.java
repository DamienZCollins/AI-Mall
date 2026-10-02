package com.mall.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求
 */
@Schema(description = "登录请求")
public record LoginRequest(

        @Schema(description = "用户名", example = "admin")
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32, message = "用户名长度不能超过 32")
        String username,

        @Schema(description = "密码", example = "admin123")
        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过 64")
        String password
) {
}
