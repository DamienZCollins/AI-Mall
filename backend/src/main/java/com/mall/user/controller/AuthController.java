package com.mall.user.controller;

import com.mall.common.result.Result;
import com.mall.common.security.AuthContext;
import com.mall.user.dto.LoginRequest;
import com.mall.user.dto.LoginResponse;
import com.mall.user.dto.UserInfo;
import com.mall.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前用户
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "登录并获取令牌")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @Operation(summary = "查询当前登录用户")
    @GetMapping("/me")
    public Result<UserInfo> me() {
        return Result.success(userService.getUserInfo(AuthContext.currentUserId()));
    }
}
