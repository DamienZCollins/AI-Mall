package com.mall.product.entity;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductStatusTest {

    @Test
    @DisplayName("按状态码解析上下架状态")
    void resolvesKnownCodes() {
        assertThat(ProductStatus.fromCode(0)).isEqualTo(ProductStatus.OFF_SALE);
        assertThat(ProductStatus.fromCode(1)).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("未知或空状态码抛出业务异常，错误码为 2002")
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> ProductStatus.fromCode(9))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_STATUS_ILLEGAL.getCode());

        assertThatThrownBy(() -> ProductStatus.fromCode(null))
                .isInstanceOf(BusinessException.class);
    }
}
