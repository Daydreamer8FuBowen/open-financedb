package com.fbw.finance.openfinancedb.repository.apikey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiUsageLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiUsageLogMapper extends BaseMapper<ApiUsageLogEntity> {
}
