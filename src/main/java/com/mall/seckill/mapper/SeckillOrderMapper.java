package com.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 秒杀订单数据访问
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 按状态机流转，影响行数为 0 说明订单已不在源状态，调用方需据此决定是否回补库存
     */
    @Update("UPDATE seckill_order SET status = #{target}, pay_time = #{payTime} "
            + "WHERE order_no = #{orderNo} AND status = #{source}")
    int transferStatus(@Param("orderNo") String orderNo,
                       @Param("source") Integer source,
                       @Param("target") Integer target,
                       @Param("payTime") LocalDateTime payTime);
}
