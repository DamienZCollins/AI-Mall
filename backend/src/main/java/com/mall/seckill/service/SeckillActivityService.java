package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.PageResult;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.seckill.dto.SeckillActivityCreateRequest;
import com.mall.seckill.dto.SeckillActivityQuery;
import com.mall.seckill.dto.SeckillActivityUpdateRequest;
import com.mall.seckill.dto.SeckillActivityView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillStatus;
import com.mall.seckill.entity.SeckillStock;
import com.mall.seckill.mapper.SeckillActivityMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import com.mall.seckill.mq.SeckillActivityChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀活动维护与查询
 */
@Service
public class SeckillActivityService {

    /** 配置状态：启用 */
    private static final int ENABLED = 1;

    /** 默认待支付超时分钟数 */
    private static final int DEFAULT_PAY_TIMEOUT_MINUTES = 15;

    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    public SeckillActivityService(SeckillActivityMapper activityMapper, SeckillStockMapper stockMapper,
                                  ProductService productService, ApplicationEventPublisher eventPublisher) {
        this.activityMapper = activityMapper;
        this.stockMapper = stockMapper;
        this.productService = productService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建活动，同时建立库存行，两者必须在同一事务内
     */
    @Transactional
    public SeckillActivityView create(SeckillActivityCreateRequest request) {
        ProductView product = requireOnSaleProduct(request.productId());
        if (request.seckillPrice().compareTo(product.price()) >= 0) {
            throw new BusinessException(ResultCode.SECKILL_PRICE_ILLEGAL);
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_TIME_ILLEGAL.getCode(),
                    "结束时间必须晚于开始时间");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(request.productId());
        activity.setTitle(request.title());
        activity.setSeckillPrice(request.seckillPrice());
        activity.setTotalStock(request.totalStock());
        activity.setPerUserLimit(request.perUserLimit() == null ? 1 : request.perUserLimit());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setPayTimeoutMinutes(request.payTimeoutMinutes() == null
                ? DEFAULT_PAY_TIMEOUT_MINUTES : request.payTimeoutMinutes());
        activity.setStatus(ENABLED);
        activityMapper.insert(activity);

        SeckillStock stock = new SeckillStock();
        stock.setActivityId(activity.getId());
        stock.setStock(request.totalStock());
        stockMapper.insert(stock);

        publishChanged(activity.getId());
        return toView(activity, product);
    }

    @Transactional
    public SeckillActivityView update(Long activityId, SeckillActivityUpdateRequest request) {
        SeckillActivity activity = requireActivity(activityId);
        if (request.title() != null) {
            activity.setTitle(request.title());
        }
        if (request.startTime() != null) {
            activity.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            activity.setEndTime(request.endTime());
        }
        if (request.status() != null) {
            activity.setStatus(request.status());
        }
        if (!activity.getStartTime().isBefore(activity.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_TIME_ILLEGAL.getCode(),
                    "结束时间必须晚于开始时间");
        }
        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        publishChanged(activity.getId());
        return toView(activity, productService.getById(activity.getProductId()));
    }

    @Transactional(readOnly = true)
    public SeckillActivityView getById(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        return toView(activity, productService.getById(activity.getProductId()));
    }

    /**
     * 进行中仅作为查询条件，活动未开始或已结束时也返回，由状态字段体现
     */
    @Transactional(readOnly = true)
    public PageResult<SeckillActivityView> list(SeckillActivityQuery query) {
        var wrapper = Wrappers.<SeckillActivity>lambdaQuery().orderByDesc(SeckillActivity::getId);
        if (Boolean.TRUE.equals(query.getRunningOnly())) {
            LocalDateTime now = LocalDateTime.now();
            wrapper.eq(SeckillActivity::getStatus, ENABLED)
                    .le(SeckillActivity::getStartTime, now)
                    .ge(SeckillActivity::getEndTime, now);
        }

        Page<SeckillActivity> page = activityMapper.selectPage(
                Page.of(query.getPage(), query.getSize()), wrapper);
        Map<Long, Integer> stockMap = remainingStockOf(page.getRecords());
        Map<Long, BigDecimal> priceMap = originalPriceOf(page.getRecords());
        List<SeckillActivityView> list = page.getRecords().stream()
                .map(activity -> SeckillActivityView.from(activity,
                        stockMap.get(activity.getId()),
                        priceMap.get(activity.getProductId()),
                        statusOf(activity)))
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    @Transactional(readOnly = true)
    public SeckillActivity requireActivity(Long activityId) {
        SeckillActivity activity = activityId == null ? null : activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    public SeckillStatus statusOf(SeckillActivity activity) {
        return SeckillStatus.of(Integer.valueOf(ENABLED).equals(activity.getStatus()),
                activity.getStartTime(), activity.getEndTime(), LocalDateTime.now());
    }

    public Integer remainingStock(Long activityId) {
        SeckillStock stock = stockMapper.selectOne(
                Wrappers.<SeckillStock>lambdaQuery().eq(SeckillStock::getActivityId, activityId));
        return stock == null ? null : stock.getStock();
    }

    private SeckillActivityView toView(SeckillActivity activity, ProductView product) {
        return SeckillActivityView.from(activity, remainingStock(activity.getId()),
                product.price(), statusOf(activity));
    }

    private Map<Long, Integer> remainingStockOf(List<SeckillActivity> activities) {
        if (activities.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = activities.stream().map(SeckillActivity::getId).toList();
        Map<Long, Integer> map = new HashMap<>();
        for (SeckillStock stock : stockMapper.selectList(
                Wrappers.<SeckillStock>lambdaQuery().in(SeckillStock::getActivityId, ids))) {
            map.put(stock.getActivityId(), stock.getStock());
        }
        return map;
    }

    /**
     * 批量取商品原价，商品被删除时该活动取不到价格，用 null 占位
     */
    private Map<Long, BigDecimal> originalPriceOf(List<SeckillActivity> activities) {
        if (activities.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = activities.stream().map(SeckillActivity::getProductId).distinct().toList();
        Map<Long, ProductView> products = productService.findViewsByIds(productIds);
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Long productId : productIds) {
            ProductView product = products.get(productId);
            map.put(productId, product == null ? null : product.price());
        }
        return map;
    }

    private ProductView requireOnSaleProduct(Long productId) {
        return productService.getOnSale(productId);
    }

    private void publishChanged(Long activityId) {
        eventPublisher.publishEvent(new SeckillActivityChangedEvent(activityId));
    }
}
