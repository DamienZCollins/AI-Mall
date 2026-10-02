package com.mall.user.dto;

import com.mall.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息，不包含密码
 */
@Schema(description = "用户信息")
public record UserInfo(

        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "昵称")
        String nickname,

        @Schema(description = "角色：USER / ADMIN")
        String role
) {

    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
