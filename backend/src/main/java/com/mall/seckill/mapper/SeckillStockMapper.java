package com.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.seckill.entity.SeckillStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 秒杀库存数据访问
 */
@Mapper
public interface SeckillStockMapper extends BaseMapper<SeckillStock> {

    /**
     * 条件扣减库存，影响行数为 0 说明已售罄，依赖数据库行锁保证并发安全
     */
    @Update("UPDATE seckill_stock SET stock = stock - 1 WHERE activity_id = #{activityId} AND stock > 0")
    int deduct(@Param("activityId") Long activityId);

    /**
     * 回补库存，带上限校验避免异常回补把库存撑过活动总量
     */
    @Update("UPDATE seckill_stock SET stock = stock + 1 "
            + "WHERE activity_id = #{activityId} AND stock < #{maxStock}")
    int restore(@Param("activityId") Long activityId, @Param("maxStock") Integer maxStock);
}
