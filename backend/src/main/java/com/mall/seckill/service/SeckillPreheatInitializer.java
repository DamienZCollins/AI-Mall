package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.mapper.SeckillActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * 启动时把未结束的活动库存加载到 Redis
 */
@Configuration
public class SeckillPreheatInitializer {

    private static final Logger log = LoggerFactory.getLogger(SeckillPreheatInitializer.class);
    private static final int ENABLED = 1;

    /**
     * 预热失败只记录告警，抢购时会按需再预热
     *
     * <p>测试环境没有真实数据源，用 mall.seckill.preheat-on-startup 关闭
     */
    @Bean
    @ConditionalOnProperty(name = "mall.seckill.preheat-on-startup", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner seckillPreheatRunner(SeckillActivityMapper activityMapper,
                                                  SeckillRedisService redisService) {
        return args -> {
            var activities = activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                    .eq(SeckillActivity::getStatus, ENABLED)
                    .ge(SeckillActivity::getEndTime, LocalDateTime.now()));
            for (SeckillActivity activity : activities) {
                try {
                    redisService.preheat(activity.getId());
                } catch (Exception e) {
                    log.warn("活动 {} 预热失败：{}", activity.getId(), e.getMessage());
                }
            }
            log.info("启动预热完成，共 {} 个未结束活动", activities.size());
        };
    }
}
