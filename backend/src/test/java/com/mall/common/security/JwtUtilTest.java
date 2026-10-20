package com.mall.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-for-jwt-util-at-least-32-bytes";

    @Test
    @DisplayName("签发后能解析出用户 ID、角色与用户名")
    void roundTripsClaims() {
        JwtUtil jwtUtil = jwtUtil(SECRET, 30);

        Claims claims = jwtUtil.parse(jwtUtil.generate(7L, "alice", "ADMIN"));

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(jwtUtil.getUserId(claims)).isEqualTo(7L);
        assertThat(jwtUtil.getRole(claims)).isEqualTo("ADMIN");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("换用其它密钥签发的令牌无法通过验签")
    void rejectsTokenSignedByAnotherKey() {
        JwtUtil issuer = jwtUtil(SECRET, 30);
        JwtUtil verifier = jwtUtil("another-secret-for-jwt-util-at-least-32b", 30);
        String token = issuer.generate(1L, "bob", "USER");

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期令牌解析抛出 ExpiredJwtException")
    void rejectsExpiredToken() {
        JwtUtil jwtUtil = jwtUtil(SECRET, -1);

        String token = jwtUtil.generate(1L, "bob", "USER");

        assertThatThrownBy(() -> jwtUtil.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("被篡改的令牌无法解析")
    void rejectsTamperedToken() {
        JwtUtil jwtUtil = jwtUtil(SECRET, 30);
        String token = jwtUtil.generate(1L, "bob", "USER");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtUtil.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("密钥为空时构造失败，暴露配置问题")
    void failsFastWithoutSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(" ");

        assertThatThrownBy(() -> new JwtUtil(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mall.jwt.secret");
    }

    @Test
    @DisplayName("密钥短于 256 位时构造失败")
    void failsFastWithShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");

        assertThatThrownBy(() -> new JwtUtil(properties)).isInstanceOf(RuntimeException.class);
    }

    private JwtUtil jwtUtil(String secret, long expireMinutes) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpireMinutes(expireMinutes);
        return new JwtUtil(properties);
    }
}
