package com.fbw.finance.openfinancedb.repository.apikey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {
}
