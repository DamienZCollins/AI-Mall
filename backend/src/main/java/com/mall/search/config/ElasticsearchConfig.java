package com.mall.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mall.search.embedding.EmbeddingProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

/**
 * Elasticsearch 与 Embedding 客户端配置
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class ElasticsearchConfig {

    /** 从连接池取连接的等待上限，避免高并发下无限期排队 */
    private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 3000;

    @Bean(destroyMethod = "close")
    public RestClient restClient(@Value("${spring.elasticsearch.uris}") String uris,
                                 @Value("${spring.elasticsearch.connection-timeout:3s}") Duration connectTimeout,
                                 @Value("${spring.elasticsearch.socket-timeout:10s}") Duration socketTimeout) {
        String cleaned = uris.replace("http://", "").replace("https://", "");
        HttpHost[] hosts = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);
        return RestClient.builder(hosts)
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(toMillis(connectTimeout))
                        .setSocketTimeout(toMillis(socketTimeout))
                        .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MILLIS))
                .build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    /**
     * Apache HttpClient 的请求配置以毫秒为单位，至少保留 1 毫秒避免出现无超时
     */
    private static int toMillis(Duration duration) {
        return (int) Math.max(duration.toMillis(), 1L);
    }
}
