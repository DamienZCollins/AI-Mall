package com.mall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置，秒杀超时订单扫描依赖它
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
