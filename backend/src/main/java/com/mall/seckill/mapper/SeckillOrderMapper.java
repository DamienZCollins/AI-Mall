package com.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 取活动下所有下过单的用户，供预热回填买家集合
     *
     * <p>不按状态过滤：uk_user_activity 限定终身一单，已取消的订单同样占用该用户的资格
     */
    @Select("SELECT user_id FROM seckill_order WHERE activity_id = #{activityId}")
    List<Long> selectUserIdsByActivityId(@Param("activityId") Long activityId);

    /**
     * 加锁读取订单状态，拿到其它事务已提交的最新值，用于条件更新落空后定位实际状态
     */
    @Select("SELECT status FROM seckill_order WHERE order_no = #{orderNo} FOR UPDATE")
    Integer selectStatusForUpdate(@Param("orderNo") String orderNo);
}
