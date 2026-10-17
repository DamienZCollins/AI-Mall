package com.mall.search.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的 embedding 客户端
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public EmbeddingClient(EmbeddingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public float[] embed(String text) {
        List<float[]> vectors = embed(List.of(text));
        return vectors.isEmpty() ? null : vectors.get(0);
    }

    /**
     * 批量向量化，返回顺序与入参一致
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(), "mall.embedding.api-key 未配置");
        }

        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(new EmbeddingRequest(properties.getModel(), texts));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(), "构造 embedding 请求失败");
        }

        String response;
        try {
            response = restClient.post()
                    .uri(properties.embeddingsUrl())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            log.error("调用 embedding 接口失败", e);
            throw new BusinessException(ResultCode.EMBEDDING_FAILED);
        }
        return parse(response, texts.size());
    }

    /**
     * 解析响应并校验维度，维度不匹配会让向量检索失效，直接失败更安全
     */
    private List<float[]> parse(String response, int expectedCount) {
        JsonNode root = readTree(response);
        if (!root.isArray()) {
            throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(), "embedding 响应不是数组");
        }

        List<float[]> vectors = new ArrayList<>(root.size());
        for (JsonNode item : root) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(), "embedding 响应缺少向量");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            if (vector.length != properties.getDimensions()) {
                throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(),
                        "向量维度为 " + vector.length + "，与配置的 " + properties.getDimensions() + " 不一致");
            }
            vectors.add(vector);
        }

        if (vectors.size() != expectedCount) {
            throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(),
                    "返回向量数量 " + vectors.size() + " 与请求数量 " + expectedCount + " 不一致");
        }
        return vectors;
    }

    private JsonNode readTree(String response) {
        try {
            return OBJECT_MAPPER.readTree(response).path("data");
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.EMBEDDING_FAILED.getCode(), "embedding 响应解析失败");
        }
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }
}
