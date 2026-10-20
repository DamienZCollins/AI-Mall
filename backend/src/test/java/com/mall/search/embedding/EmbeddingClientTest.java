package com.mall.search.embedding;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Embedding 客户端对接本地 HTTP 桩，验证请求格式与响应校验，不依赖真实服务商
 */
class EmbeddingClientTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String responseBody;
    private volatile int responseStatus;
    private volatile String lastAuthorization;
    private volatile String lastRequestBody;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseBody = "{\"data\":[{\"embedding\":[0.1,0.2]}]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("按 OpenAI 协议发送模型与文本，并解析出向量")
    void sendsRequestAndParsesVector() {
        EmbeddingClient client = client(2);

        float[] vector = client.embed("透气跑步鞋");

        assertThat(vector).containsExactly(0.1f, 0.2f);
        assertThat(lastAuthorization).isEqualTo("Bearer test-key");
        assertThat(lastRequestBody).contains("\"model\":\"test-model\"").contains("透气跑步鞋");
    }

    @Test
    @DisplayName("批量请求返回顺序与入参一致")
    void keepsBatchOrder() {
        responseBody = "{\"data\":[{\"embedding\":[1,1]},{\"embedding\":[2,2]}]}";
        EmbeddingClient client = client(2);

        List<float[]> vectors = client.embed(List.of("第一件", "第二件"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1f, 1f);
        assertThat(vectors.get(1)).containsExactly(2f, 2f);
    }

    @Test
    @DisplayName("空入参不发请求，直接返回空列表")
    void skipsEmptyInput() {
        EmbeddingClient client = client(2);

        assertThat(client.embed(List.of())).isEmpty();
        assertThat(lastRequestBody).isNull();
    }

    @Test
    @DisplayName("向量维度和配置不一致时失败，避免写入错误维度的索引")
    void rejectsDimensionMismatch() {
        EmbeddingClient client = client(1024);

        assertThatThrownBy(() -> client.embed("跑步鞋"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度");
    }

    @Test
    @DisplayName("返回向量数量与请求条数不一致时失败")
    void rejectsCountMismatch() {
        EmbeddingClient client = client(2);

        assertThatThrownBy(() -> client.embed(List.of("第一条", "第二条")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量");
    }

    @Test
    @DisplayName("接口返回非 JSON 时转换为向量化失败")
    void rejectsMalformedResponse() {
        responseBody = "<html>bad gateway</html>";
        EmbeddingClient client = client(2);

        assertThatThrownBy(() -> client.embed("跑步鞋"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.EMBEDDING_FAILED.getCode());
    }

    @Test
    @DisplayName("服务端错误转换为向量化失败")
    void mapsServerError() {
        responseStatus = 500;
        responseBody = "{\"error\":\"boom\"}";
        EmbeddingClient client = client(2);

        assertThatThrownBy(() -> client.embed("跑步鞋"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.EMBEDDING_FAILED.getCode());
    }

    @Test
    @DisplayName("未配置密钥时不发请求直接失败")
    void rejectsMissingApiKey() {
        EmbeddingProperties properties = properties(2);
        properties.setApiKey(" ");
        EmbeddingClient client = new EmbeddingClient(properties, RestClient.builder());

        assertThatThrownBy(() -> client.embed("跑步鞋"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("根地址末尾带斜杠时拼接路径不产生双斜杠")
    void buildsUrlWithTrailingSlash() {
        EmbeddingProperties properties = properties(2);
        properties.setBaseUrl("https://example.com/v1/");

        assertThat(properties.embeddingsUrl()).isEqualTo("https://example.com/v1/embeddings");
    }

    private EmbeddingClient client(int dimensions) {
        return new EmbeddingClient(properties(dimensions), RestClient.builder());
    }

    private EmbeddingProperties properties(int dimensions) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setDimensions(dimensions);
        properties.setTimeoutSeconds(5);
        return properties;
    }
}
