package com.fbw.finance.openfinancedb.repository.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfoEntity> {
}
