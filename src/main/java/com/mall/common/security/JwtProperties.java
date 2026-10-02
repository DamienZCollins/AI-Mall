package com.mall.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项
 */
@ConfigurationProperties(prefix = "mall.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求不短于 32 字节 */
    private String secret;

    /** 令牌有效期，单位分钟 */
    private long expireMinutes = 120;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }
}
