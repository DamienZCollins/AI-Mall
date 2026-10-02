package com.mall.config.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

import java.net.URI;

/**
 * 商品图片对象存储配置
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(S3StorageConfig.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * 启动时确保 bucket 存在，并允许匿名读取商品图片；对象存储不可用只记录告警，不阻止应用启动
     */
    @Bean
    public ApplicationRunner storageBucketInitializer(S3Client s3Client, StorageProperties properties) {
        return args -> {
            if (!properties.isInitBucket()) {
                return;
            }
            try {
                createBucketIfAbsent(s3Client, properties.getBucket());
                s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                        .bucket(properties.getBucket())
                        .policy(publicReadPolicy(properties.getBucket()))
                        .build());
                log.info("商品图片 bucket 就绪：{}", properties.getBucket());
            } catch (Exception e) {
                log.warn("商品图片 bucket 初始化失败：{}", e.getMessage());
            }
        };
    }

    private void createBucketIfAbsent(S3Client s3Client, String bucket) {
        try {
            s3Client.createBucket(builder -> builder.bucket(bucket));
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.debug("bucket {} 已存在", bucket);
        }
    }

    /**
     * 只读策略，仅放开 GetObject
     */
    private String publicReadPolicy(String bucket) throws Exception {
        ObjectNode statement = OBJECT_MAPPER.createObjectNode();
        statement.put("Effect", "Allow");
        statement.put("Principal", "*");
        statement.put("Action", "s3:GetObject");
        statement.put("Resource", "arn:aws:s3:::" + bucket + "/*");

        ArrayNode statements = OBJECT_MAPPER.createArrayNode().add(statement);

        ObjectNode policy = OBJECT_MAPPER.createObjectNode();
        policy.put("Version", "2012-10-17");
        policy.set("Statement", statements);
        return OBJECT_MAPPER.writeValueAsString(policy);
    }
}
