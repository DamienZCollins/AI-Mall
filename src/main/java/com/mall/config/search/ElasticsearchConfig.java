package com.mall.config.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mall.common.embedding.EmbeddingProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Elasticsearch 与 Embedding 客户端配置
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient(@Value("${spring.elasticsearch.uris}") String uris) {
        String cleaned = uris.replace("http://", "").replace("https://", "");
        HttpHost[] hosts = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);
        return RestClient.builder(hosts).build();
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
}
