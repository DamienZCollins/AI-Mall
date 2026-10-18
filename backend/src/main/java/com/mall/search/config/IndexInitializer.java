package com.mall.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
     * 索引不可用不阻止应用启动，但必须留下明确的错误原因：索引缺失时所有搜索都会返回检索不可用
     */
    @Bean
    public ApplicationRunner productIndexInitializer(ElasticsearchClient client) {
        return args -> {
            try {
                init(client);
            } catch (ElasticsearchException e) {
                log.error("索引 {} 初始化失败，ES 返回状态 {}，原因：{}。搜索接口将持续返回 3002，"
                                + "请确认 ES 可用且已安装 analysis-ik 插件", indexName, e.status(), esErrorReason(e), e);
            } catch (Exception e) {
                log.error("索引 {} 初始化失败，无法读取 mapping 或连接 ES（{}）。搜索接口将持续返回 3002",
                        indexName, e.getMessage(), e);
            }
        };
    }

    private void init(ElasticsearchClient client) throws Exception {
        if (client.indices().exists(e -> e.index(indexName)).value()) {
            log.info("索引 {} 已存在，跳过创建", indexName);
        } else {
            InputStream body = createBody();
            client.indices().create(c -> c.index(indexName).withJson(body));
            log.info("索引 {} 与别名 {} 创建完成", indexName, alias);
        }
        ensureAlias(client);
    }

    /**
     * 索引存在但别名缺失时补齐，别名缺失会让写入与检索都失败
     */
    private void ensureAlias(ElasticsearchClient client) throws Exception {
        if (client.indices().existsAlias(e -> e.index(indexName).name(alias)).value()) {
            return;
        }
        client.indices().putAlias(a -> a.index(indexName).name(alias).isWriteIndex(true));
        log.warn("索引 {} 缺少别名 {}，已补充创建", indexName, alias);
    }

    /**
     * 取出 ES 返回的错误原因，缺少 analyzer 时补充插件提示
     */
    private String esErrorReason(ElasticsearchException e) {
        String reason = e.error() == null ? e.getMessage() : e.error().reason();
        if (reason != null && reason.toLowerCase().contains("analyzer")) {
            return reason + "（mapping 使用 ik_smart，需安装 analysis-ik 插件）";
        }
        return reason;
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
