package com.fbw.finance.openfinancedb.repository.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import org.springframework.util.StringUtils;

public final class RepositoryQueryHelper {

    private RepositoryQueryHelper() {
    }

    public static <T> LambdaQueryWrapper<T> lambdaQuery() {
        return new LambdaQueryWrapper<>();
    }

    public static <T> LambdaQueryWrapper<T> likeIfHasText(
            LambdaQueryWrapper<T> queryWrapper,
            SFunction<T, ?> column,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            queryWrapper.like(column, value.trim());
        }
        return queryWrapper;
    }

    public static <T> LambdaQueryWrapper<T> eqIfHasText(
            LambdaQueryWrapper<T> queryWrapper,
            SFunction<T, ?> column,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            queryWrapper.eq(column, value);
        }
        return queryWrapper;
    }

    public static <T, V> LambdaQueryWrapper<T> eqIfPresent(
            LambdaQueryWrapper<T> queryWrapper,
            SFunction<T, ?> column,
            V value
    ) {
        if (value != null) {
            queryWrapper.eq(column, value);
        }
        return queryWrapper;
    }

    public static <T> PageResult<T> selectPage(
            BaseMapper<T> mapper,
            Integer pageNo,
            Integer pageSize,
            LambdaQueryWrapper<T> queryWrapper
    ) {
        Page<T> page = mapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
