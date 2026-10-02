package com.mall.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;

/**
 * 商品状态
 */
public enum ProductStatus {

    OFF_SALE(0, "下架"),
    ON_SALE(1, "上架");

    private final int code;
    private final String description;

    ProductStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static ProductStatus fromCode(Integer code) {
        if (code != null) {
            for (ProductStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BusinessException(ResultCode.PRODUCT_STATUS_ILLEGAL);
    }
}
