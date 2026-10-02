package com.mall.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 编辑秒杀活动请求，字段为 null 表示不修改
 */
@Schema(description = "编辑秒杀活动请求")
public record SeckillActivityUpdateRequest(

        @Schema(description = "活动标题")
        @Size(max = 128, message = "活动标题长度不能超过 128")
        String title,

        @Schema(description = "开始时间")
        LocalDateTime startTime,

        @Schema(description = "结束时间")
        LocalDateTime endTime,

        @Schema(description = "配置状态：1 启用，0 停用")
        Integer status
) {
}
