package com.mall.seckill.controller;

import com.mall.common.result.PageResult;
import com.mall.common.result.Result;
import com.mall.seckill.dto.SeckillActivityCreateRequest;
import com.mall.seckill.dto.SeckillActivityQuery;
import com.mall.seckill.dto.SeckillActivityUpdateRequest;
import com.mall.seckill.dto.SeckillActivityView;
import com.mall.seckill.service.SeckillActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员秒杀活动维护
 */
@Tag(name = "管理员-秒杀")
@RestController
@RequestMapping("/api/admin/seckill/activities")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSeckillController {

    private final SeckillActivityService activityService;

    public AdminSeckillController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @Operation(summary = "创建秒杀活动，同时初始化库存")
    @PostMapping
    public Result<SeckillActivityView> create(@Valid @RequestBody SeckillActivityCreateRequest request) {
        return Result.success(activityService.create(request));
    }

    @Operation(summary = "编辑秒杀活动")
    @PutMapping("/{activityId}")
    public Result<SeckillActivityView> update(
            @PathVariable @Positive(message = "活动 ID 必须为正数") Long activityId,
            @Valid @RequestBody SeckillActivityUpdateRequest request) {
        return Result.success(activityService.update(activityId, request));
    }

    @Operation(summary = "分页查询全部秒杀活动，包含未开始与已结束")
    @GetMapping
    public Result<PageResult<SeckillActivityView>> list(@Valid SeckillActivityQuery query) {
        return Result.success(activityService.list(query));
    }
}
