package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品维护与查询
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductMapper productMapper, ApplicationEventPublisher eventPublisher) {
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 新增商品，默认下架，需要管理员显式上架
     */
    @Transactional
    public ProductView create(ProductCreateRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setBrand(request.brand());
        product.setCategoryId(request.categoryId());
        product.setDescription(request.description());
        product.setSellingPoint(request.sellingPoint());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setImageUrl(request.imageUrl());
        product.setStatus(ProductStatus.OFF_SALE.getCode());
        productMapper.insert(product);
        publishChanged(product.getId());
        return ProductView.from(product);
    }

    @Transactional
    public ProductView update(Long id, ProductUpdateRequest request) {
        Product product = requireProduct(id);
        if (StringUtils.hasText(request.name())) {
            product.setName(request.name());
        }
        if (request.brand() != null) {
            product.setBrand(request.brand());
        }
        if (request.categoryId() != null) {
            product.setCategoryId(request.categoryId());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.sellingPoint() != null) {
            product.setSellingPoint(request.sellingPoint());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.stock() != null) {
            product.setStock(request.stock());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        publishChanged(product.getId());
        return ProductView.from(product);
    }

    @Transactional
    public ProductView changeStatus(Long id, ProductStatusRequest request) {
        Product product = requireProduct(id);
        ProductStatus target = ProductStatus.fromCode(request.status());
        if (Integer.valueOf(target.getCode()).equals(product.getStatus())) {
            throw new BusinessException(ResultCode.PRODUCT_STATUS_ILLEGAL.getCode(),
                    "商品已处于「" + target.getDescription() + "」状态");
        }
        product.setStatus(target.getCode());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        publishChanged(product.getId());
        return ProductView.from(product);
    }

    /**
     * 管理员查询，可按状态过滤
     */
    @Transactional(readOnly = true)
    public PageResult<ProductView> list(ProductQuery query) {
        return queryPage(query, query.getStatus());
    }

    /**
     * 用户查询，只返回上架商品
     */
    @Transactional(readOnly = true)
    public PageResult<ProductView> listOnSale(ProductQuery query) {
        return queryPage(query, ProductStatus.ON_SALE.getCode());
    }

    /**
     * 用户查询详情，下架商品视为不存在
     */
    @Transactional(readOnly = true)
    public ProductView getOnSale(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getStatus() == null || product.getStatus() != ProductStatus.ON_SALE.getCode()) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return ProductView.from(product);
    }

    /**
     * 按 ID 查询，不限状态，供索引同步使用
     */
    @Transactional(readOnly = true)
    public ProductView getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return ProductView.from(product);
    }

    /**
     * 按 ID 批量查询，不限状态，供其它模块批量组装数据使用
     */
    @Transactional(readOnly = true)
    public Map<Long, ProductView> findViewsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(Wrappers.<Product>lambdaQuery().in(Product::getId, ids)).stream()
                .map(ProductView::from)
                .collect(Collectors.toMap(ProductView::id, view -> view, (a, b) -> a));
    }

    private void publishChanged(Long productId) {
        eventPublisher.publishEvent(new ProductChangedEvent(productId));
    }

    private PageResult<ProductView> queryPage(ProductQuery query, Integer status) {
        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .eq(status != null, Product::getStatus, status)
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(Product::getName, query.getKeyword()).or().like(Product::getBrand, query.getKeyword()))
                .orderByDesc(Product::getId);

        Page<Product> page = productMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        List<ProductView> list = page.getRecords().stream().map(ProductView::from).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
