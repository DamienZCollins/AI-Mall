package com.mall.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置项
 */
@ConfigurationProperties(prefix = "mall.storage")
public class StorageProperties {

    /** S3 兼容服务地址 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String region = "us-east-1";

    /** 商品图片 bucket */
    private String bucket;

    /** 对外访问前缀，用于拼接图片 URL */
    private String publicBaseUrl;

    /** 启动时是否创建 bucket 并开放匿名读 */
    private boolean initBucket = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isInitBucket() {
        return initBucket;
    }

    public void setInitBucket(boolean initBucket) {
        this.initBucket = initBucket;
    }
}
