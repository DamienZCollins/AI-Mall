package com.mall.search.entity;

/**
 * 商品分类字典：库中没有分类表，分类为固定的 6 个 ID
 */
public enum ProductCategoryEnum {

    DIGITAL(1001L, "数码"),
    HOME(1002L, "家居"),
    PERSONAL_CARE(1003L, "个护美妆"),
    APPAREL(1004L, "服饰箱包"),
    FOOD(1005L, "食品饮品"),
    LIFESTYLE(1006L, "生活方式");

    private final long id;
    private final String name;

    ProductCategoryEnum(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * 按分类 ID 取分类名，未知 ID 或空值返回 null，调用方按空段跳过
     */
    public static String nameOf(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (ProductCategoryEnum category : values()) {
            if (category.id == categoryId) {
                return category.name;
            }
        }
        return null;
    }
}
