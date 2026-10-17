package com.mall.search.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 配置项，兼容 OpenAI 接口协议的任意服务商
 */
@ConfigurationProperties(prefix = "mall.embedding")
public class EmbeddingProperties {

    /** 接口根地址，例如 https://api.siliconflow.cn/v1 */
    private String baseUrl;

    private String apiKey;

    private String model;

    /** 向量维度，需与索引 mapping 的 dims 一致 */
    private int dimensions = 1024;

    /** 单次请求超时，单位秒 */
    private int timeoutSeconds = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String embeddingsUrl() {
        return baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
    }
}
