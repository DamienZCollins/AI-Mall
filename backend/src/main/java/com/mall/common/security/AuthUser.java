package com.mall.common.security;

/**
 * 认证通过后的当前用户，作为 Spring Security 的 principal
 */
public record AuthUser(Long userId, String username, String role) {
}
