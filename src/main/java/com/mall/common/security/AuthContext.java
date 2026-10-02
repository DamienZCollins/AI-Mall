package com.mall.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 读取当前登录用户
 */
public final class AuthContext {

    private AuthContext() {
    }

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }
        return null;
    }

    public static Long currentUserId() {
        AuthUser authUser = currentUser();
        return authUser == null ? null : authUser.userId();
    }
}
