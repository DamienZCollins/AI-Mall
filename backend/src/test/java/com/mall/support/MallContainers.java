package com.mall.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试共享容器：整个测试进程只启动一次
 *
 * <p>按测试类启停容器会让 Spring 缓存的上下文指向已停止的容器，
 * 因此这里用静态单例持有，进程退出时由 Testcontainers 的 Ryuk 统一回收
 */
public final class MallContainers {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("mall")
            .withUsername("mall")
            .withPassword("mall")
            .withInitScript("db/schema.sql");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    private MallContainers() {
    }

    public static String mysqlUrl() {
        return MYSQL.getJdbcUrl();
    }

    public static String mysqlUsername() {
        return MYSQL.getUsername();
    }

    public static String mysqlPassword() {
        return MYSQL.getPassword();
    }

    public static String redisHost() {
        return REDIS.getHost();
    }

    public static int redisPort() {
        return REDIS.getMappedPort(6379);
    }
}
