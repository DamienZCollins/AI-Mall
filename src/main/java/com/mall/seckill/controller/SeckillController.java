package com.mall.seckill.controller;

import com.mall.common.result.PageResult;
import com.mall.common.result.Result;
import com.mall.common.security.AuthContext;
import com.mall.seckill.dto.SeckillActivityQuery;
import com.mall.seckill.dto.SeckillActivityView;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.service.SeckillActivityService;
import com.mall.seckill.service.SeckillOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧秒杀接口
 */
@Tag(name = "秒杀")
@RestController
@RequestMapping("/api/seckill")
@Validated
public class SeckillController {

    private final SeckillActivityService activityService;
    private final SeckillOrderService orderService;

    public SeckillController(SeckillActivityService activityService, SeckillOrderService orderService) {
        this.activityService = activityService;
        this.orderService = orderService;
    }

    @Operation(summary = "分页查询秒杀活动，runningOnly 为 true 时只返回进行中的")
    @GetMapping("/activities")
    public Result<PageResult<SeckillActivityView>> listActivities(@Valid SeckillActivityQuery query) {
        return Result.success(activityService.list(query));
    }

    @Operation(summary = "查询秒杀活动详情")
    @GetMapping("/activities/{activityId}")
    public Result<SeckillActivityView> getActivity(
            @PathVariable @Positive(message = "活动 ID 必须为正数") Long activityId) {
        return Result.success(activityService.getById(activityId));
    }

    @Operation(summary = "抢购下单")
    @PostMapping("/{activityId}")
    public Result<SeckillOrderView> placeOrder(
            @PathVariable @Positive(message = "活动 ID 必须为正数") Long activityId) {
        return Result.success(orderService.placeOrder(activityId, AuthContext.currentUserId()));
    }

    @Operation(summary = "查询秒杀订单")
    @GetMapping("/orders/{orderNo}")
    public Result<SeckillOrderView> getOrder(@PathVariable String orderNo) {
        return Result.success(orderService.getOrder(orderNo));
    }

    @Operation(summary = "模拟支付回调")
    @PostMapping("/orders/{orderNo}/pay")
    public Result<SeckillOrderView> pay(@PathVariable String orderNo) {
        return Result.success(orderService.pay(orderNo));
    }
}
