package com.mall.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单元测试初始化 MyBatis-Plus 的实体元数据
 *
 * <p>Lambda 条件构造器依赖 {@link TableInfoHelper} 中的缓存，脱离 Spring 容器时需要手动注册实体
 */
public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    public static void registerEntity(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
