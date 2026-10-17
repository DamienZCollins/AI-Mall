package com.mall.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分页返回结构
 */
@Schema(description = "分页结果")
public record PageResult<T>(

        @Schema(description = "总记录数")
        long total,

        @Schema(description = "页码，从 1 开始")
        long page,

        @Schema(description = "每页条数")
        long size,

        @Schema(description = "当前页数据")
        List<T> list
) {

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        return new PageResult<>(total, page, size, list);
    }
}
