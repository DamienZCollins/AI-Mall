package com.mall.search.controller;

import com.mall.common.result.Result;
import com.mall.search.service.ProductIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员索引维护
 */
@Tag(name = "管理员-搜索索引")
@RestController
@RequestMapping("/api/admin/search")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchController {

    private final ProductIndexService productIndexService;

    public AdminSearchController(ProductIndexService productIndexService) {
        this.productIndexService = productIndexService;
    }

    @Operation(summary = "全量重建商品索引，用于同步失败后的恢复")
    @PostMapping("/reindex")
    public Result<Integer> reindex() {
        return Result.success(productIndexService.reindex());
    }
}
