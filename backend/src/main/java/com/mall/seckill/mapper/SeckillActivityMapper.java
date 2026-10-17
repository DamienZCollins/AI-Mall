package com.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀活动数据访问
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {
}
