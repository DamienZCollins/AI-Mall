package com.mall.user.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.common.security.JwtProperties;
import com.mall.common.security.JwtUtil;
import com.mall.user.dto.LoginRequest;
import com.mall.user.dto.LoginResponse;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private UserMapper userMapper;

    private UserService userService;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-for-user-service-at-least-32b");
        properties.setExpireMinutes(60);
        jwtUtil = new JwtUtil(properties);
        userService = new UserService(userMapper, passwordEncoder, jwtUtil, properties);
    }

    @Test
    @DisplayName("登录成功返回可解析的令牌与用户信息")
    void loginIssuesToken() {
        when(userMapper.selectOne(any())).thenReturn(user("alice", "secret", "USER", 1));

        LoginResponse response = userService.login(new LoginRequest("alice", "secret"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().role()).isEqualTo("USER");
        assertThat(jwtUtil.getUserId(jwtUtil.parse(response.token()))).isEqualTo(1L);
    }

    @Test
    @DisplayName("密码错误与用户不存在返回同一错误码，不泄露账号是否存在")
    void wrongPasswordAndUnknownUserShareErrorCode() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertLoginFailed(new LoginRequest("nobody", "secret"));

        when(userMapper.selectOne(any())).thenReturn(user("alice", "secret", "USER", 1));
        assertLoginFailed(new LoginRequest("alice", "wrong"));
    }

    @Test
    @DisplayName("账号被禁用时返回禁用错误码")
    void disabledUserCannotLogin() {
        when(userMapper.selectOne(any())).thenReturn(user("alice", "secret", "USER", 0));

        assertThatThrownBy(() -> userService.login(new LoginRequest("alice", "secret")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.USER_DISABLED.getCode());
    }

    @Test
    @DisplayName("查询用户信息时用户不存在返回 1002")
    void getUserInfoFailsForMissingUser() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserInfo(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("按 ID 查询用户信息不包含密码字段")
    void getUserInfoHidesPassword() {
        when(userMapper.selectById(1L)).thenReturn(user("alice", "secret", "ADMIN", 1));

        assertThat(userService.getUserInfo(1L))
                .extracting(info -> info.username(), info -> info.role())
                .containsExactly("alice", "ADMIN");
    }

    @Test
    @DisplayName("ID 为空时不查库，直接返回 null")
    void findByIdReturnsNullForMissingId() {
        assertThat(userService.findById(null)).isNull();
    }

    private void assertLoginFailed(LoginRequest request) {
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.LOGIN_FAILED.getCode());
    }

    private User user(String username, String rawPassword, String role, int status) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname("昵称");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
