package com.mall.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 索引初始化：创建索引与读写别名，mapping 来自 resources/es/product-index.json
 */
@Configuration
public class IndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MAPPING_RESOURCE = "es/product-index.json";

    @Value("${mall.search.index-name}")
    private String indexName;

    @Value("${mall.search.index-alias}")
    private String alias;

    /**
     * 索引不可用只记录告警，不阻止应用启动
     */
    @Bean
    public ApplicationRunner productIndexInitializer(ElasticsearchClient client) {
        return args -> {
            try {
                if (client.indices().exists(e -> e.index(indexName)).value()) {
                    log.info("索引 {} 已存在，跳过创建", indexName);
                    return;
                }
                InputStream body = createBody();
                client.indices().create(c -> c.index(indexName).withJson(body));
                log.info("索引 {} 与别名 {} 创建完成", indexName, alias);
            } catch (Exception e) {
                log.warn("索引初始化失败：{}", e.getMessage());
            }
        };
    }

    /**
     * create 请求体一次成型：settings + mappings + aliases，别名同时作为写入索引
     */
    private InputStream createBody() throws Exception {
        String mapping = new ClassPathResource(MAPPING_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = OBJECT_MAPPER.readTree(mapping);

        ObjectNode aliasConfig = OBJECT_MAPPER.createObjectNode();
        aliasConfig.put("is_write_index", true);

        ObjectNode aliases = OBJECT_MAPPER.createObjectNode();
        aliases.set(alias, aliasConfig);

        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.set("settings", root.path("settings"));
        body.set("mappings", root.path("mappings"));
        body.set("aliases", aliases);

        return new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(body));
    }
}
