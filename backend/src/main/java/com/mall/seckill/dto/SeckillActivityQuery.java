package com.mall.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 秒杀活动查询条件
 */
@Schema(description = "秒杀活动查询条件")
public class SeckillActivityQuery {

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Schema(description = "每页条数，最大 100", defaultValue = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer size = 10;

    @Schema(description = "只查进行中的活动")
    private Boolean runningOnly;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Boolean getRunningOnly() {
        return runningOnly;
    }

    public void setRunningOnly(Boolean runningOnly) {
        this.runningOnly = runningOnly;
    }
}
