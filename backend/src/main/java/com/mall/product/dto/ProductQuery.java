package com.mall.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 商品查询条件
 */
@Schema(description = "商品查询条件")
public class ProductQuery {

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Schema(description = "每页条数，最大 100", defaultValue = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer size = 10;

    @Schema(description = "关键字，匹配商品名称与品牌")
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    @Schema(description = "分类 ID")
    @Positive(message = "分类 ID 必须为正数")
    private Long categoryId;

    @Schema(description = "状态：1 上架，0 下架；不传表示全部")
    private Integer status;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
