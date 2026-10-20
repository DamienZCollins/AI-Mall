package com.mall.product.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.PageResult;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductCreateRequest;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.ProductStatusRequest;
import com.mall.product.dto.ProductUpdateRequest;
import com.mall.product.dto.ProductView;
import com.mall.product.entity.Product;
import com.mall.product.entity.ProductStatus;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mq.ProductChangedEvent;
import com.mall.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @BeforeAll
    static void registerEntity() {
        MybatisPlusTestSupport.registerEntity(Product.class);
    }

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("新增商品默认下架，并发布变更事件")
    void createStartsOffSaleAndPublishesEvent() {
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(10L);
            return 1;
        });

        ProductView view = productService.create(new ProductCreateRequest(
                "网面透气跑步鞋", "沃兰", 1004L, "适合夏季", "网面透气",
                new BigDecimal("399.00"), 100, null));

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.status()).isEqualTo(ProductStatus.OFF_SALE.getCode());
        verify(eventPublisher).publishEvent(new ProductChangedEvent(10L));
    }

    @Test
    @DisplayName("编辑商品只覆盖非空字段")
    void updateOnlyAppliesProvidedFields() {
        Product existing = product(1L, "旧名称", "旧品牌", 20, ProductStatus.ON_SALE);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ProductView view = productService.update(1L, new ProductUpdateRequest(
                "新名称", null, null, null, null, new BigDecimal("299.00"), null, null));

        assertThat(view.name()).isEqualTo("新名称");
        assertThat(view.brand()).isEqualTo("旧品牌");
        assertThat(view.price()).isEqualByComparingTo("299.00");
        assertThat(view.stock()).isEqualTo(20);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L));
    }

    @Test
    @DisplayName("编辑不存在的商品返回 2001")
    void updateFailsForMissingProduct() {
        when(productMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> productService.update(1L, new ProductUpdateRequest(
                "名称", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("上下架状态未变化时返回 2002")
    void changeStatusRejectsSameStatus() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "商品", "品牌", 10, ProductStatus.ON_SALE));

        assertThatThrownBy(() -> productService.changeStatus(1L, new ProductStatusRequest(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_STATUS_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("上下架成功时更新状态并发布事件")
    void changeStatusUpdatesAndPublishes() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "商品", "品牌", 10, ProductStatus.OFF_SALE));

        ProductView view = productService.changeStatus(1L, new ProductStatusRequest(1));

        assertThat(view.status()).isEqualTo(ProductStatus.ON_SALE.getCode());
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.ON_SALE.getCode());
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L));
    }

    @Test
    @DisplayName("用户查询详情时下架商品视为不存在")
    void getOnSaleHidesOffSaleProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "商品", "品牌", 10, ProductStatus.OFF_SALE));

        assertThatThrownBy(() -> productService.getOnSale(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("按 ID 批量查询在入参为空时不查库")
    void findViewsByIdsSkipsEmptyInput() {
        assertThat(productService.findViewsByIds(List.of())).isEmpty();
        assertThat(productService.findViewsByIds(null)).isEmpty();
        verify(productMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("批量查询按 ID 去重，重复 ID 只保留一条")
    void findViewsByIdsKeepsFirstOccurrence() {
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(1L, "商品一", "品牌", 10, ProductStatus.ON_SALE),
                product(2L, "商品二", "品牌", 10, ProductStatus.ON_SALE)));

        Map<Long, ProductView> views = productService.findViewsByIds(List.of(1L, 2L, 1L));

        assertThat(views).containsOnlyKeys(1L, 2L);
        assertThat(views.get(1L).name()).isEqualTo("商品一");
    }

    @Test
    @DisplayName("分页结果把 MyBatis-Plus 分页信息转换为统一结构")
    void listConvertsPage() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Product> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 10, 25);
        page.setRecords(List.of(product(11L, "商品", "品牌", 10, ProductStatus.ON_SALE)));
        when(productMapper.selectPage(any(), any())).thenReturn(page);

        ProductQuery query = new ProductQuery();
        query.setPage(2);
        query.setSize(10);
        PageResult<ProductView> result = productService.list(query);

        assertThat(result.total()).isEqualTo(25);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.list()).extracting(ProductView::id).containsExactly(11L);
    }

    private Product product(Long id, String name, String brand, int stock, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setBrand(brand);
        product.setCategoryId(1001L);
        product.setPrice(new BigDecimal("199.00"));
        product.setStock(stock);
        product.setStatus(status.getCode());
        return product;
    }
}
