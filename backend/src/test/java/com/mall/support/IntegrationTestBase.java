package com.mall.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.mall.search.service.ProductIndexService;
import com.mall.seckill.mq.SeckillConsumerRegistrar;
import com.mall.seckill.service.SeckillOrderTimeoutTask;
import com.mall.seckill.service.SeckillStockReconcileTask;
import org.mockito.Answers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 集成测试基础环境：MySQL 与 Redis 由 {@link MallContainers} 提供，整个测试进程共用一套
 *
 * <p>RocketMQ、Elasticsearch 与对象存储不在集成测试范围内：相关消费者、初始化器与定时任务
 * 替换为空实现，避免测试期间发起外部连接
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MallContainers::mysqlUrl);
        registry.add("spring.datasource.username", MallContainers::mysqlUsername);
        registry.add("spring.datasource.password", MallContainers::mysqlPassword);
        registry.add("spring.data.redis.host", MallContainers::redisHost);
        registry.add("spring.data.redis.port", MallContainers::redisPort);
    }

    /** 消费者启动会连接 RocketMQ 代理，集成测试不覆盖消息链路 */
    @MockitoBean
    protected SeckillConsumerRegistrar consumerRegistrar;

    /** 定时任务会在测试期间扫描数据库，关闭后由用例显式调用被测方法 */
    @MockitoBean
    protected SeckillOrderTimeoutTask orderTimeoutTask;

    @MockitoBean
    protected SeckillStockReconcileTask stockReconcileTask;

    /**
     * 索引初始化会访问 ES 建立索引与别名，集成测试不覆盖检索链路
     *
     * <p>用深度桩代替默认 mock：初始化逻辑连续调用 indices().exists(...).value()，普通 mock 会在空指针处中断
     */
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    protected ElasticsearchClient elasticsearchClient;

    /** 商品写入后触发的索引同步依赖 embedding 与 ES，这里替换为空实现 */
    @MockitoBean
    protected ProductIndexService productIndexService;
}
