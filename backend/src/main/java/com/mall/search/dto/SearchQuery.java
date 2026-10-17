package com.mall.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 搜索入参
 */
@Schema(description = "搜索入参")
public class SearchQuery {

    @Schema(description = "搜索词，支持自然语言描述", example = "夏天穿的透气鞋")
    @NotBlank(message = "搜索词不能为空")
    @Size(max = 128, message = "搜索词长度不能超过 128")
    private String keyword;

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Schema(description = "每页条数，最大 50", defaultValue = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private Integer size = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

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
}
