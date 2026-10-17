package com.mall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.common.security.JwtProperties;
import com.mall.common.security.JwtUtil;
import com.mall.user.dto.LoginRequest;
import com.mall.user.dto.LoginResponse;
import com.mall.user.dto.UserInfo;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 用户与登录
 */
@Service
public class UserService {

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 校验账号密码并签发令牌，用户不存在与密码错误返回同一错误
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        Duration expire = Duration.ofMinutes(jwtProperties.getExpireMinutes());
        return new LoginResponse(token, expire.toSeconds(), UserInfo.from(user));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }

    @Transactional(readOnly = true)
    public UserInfo getUserInfo(Long id) {
        User user = findById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return UserInfo.from(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }
}
