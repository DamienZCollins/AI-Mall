package com.mall.search.controller;

import com.mall.common.result.PageResult;
import com.mall.common.result.Result;
import com.mall.search.dto.SearchHit;
import com.mall.search.dto.SearchQuery;
import com.mall.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品混合搜索
 */
@Tag(name = "搜索")
@RestController
@RequestMapping("/api/search")
@Validated
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "关键词与语义混合搜索，无需登录")
    @GetMapping
    public Result<PageResult<SearchHit>> search(@Valid SearchQuery query) {
        return Result.success(searchService.search(query));
    }
}
