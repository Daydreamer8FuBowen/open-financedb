package com.fbw.finance.openfinancedb.repository.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockSyncStateMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StockSyncStateRepositoryImpl implements StockSyncStateRepository {

    private final StockSyncStateMapper stockSyncStateMapper;

    public StockSyncStateRepositoryImpl(StockSyncStateMapper stockSyncStateMapper) {
        this.stockSyncStateMapper = stockSyncStateMapper;
    }

    @Override
    public Long create(StockSyncStateEntity entity) {
        return stockSyncStateMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(StockSyncStateEntity entity) {
        LambdaUpdateWrapper<StockSyncStateEntity> updateWrapper = new LambdaUpdateWrapper<StockSyncStateEntity>()
                .eq(StockSyncStateEntity::getId, entity.getId())
                .set(StockSyncStateEntity::getSymbol, entity.getSymbol())
                .set(StockSyncStateEntity::getDataType, entity.getDataType())
                .set(StockSyncStateEntity::getStartTime, entity.getStartTime())
                .set(StockSyncStateEntity::getLatestSyncTime, entity.getLatestSyncTime())
                .set(StockSyncStateEntity::getTargetSyncTime, entity.getTargetSyncTime())
                .set(StockSyncStateEntity::getLastSuccessTime, entity.getLastSuccessTime())
                .set(StockSyncStateEntity::getLastFailedTime, entity.getLastFailedTime())
                .set(StockSyncStateEntity::getSyncStatus, entity.getSyncStatus())
                .set(StockSyncStateEntity::getRetryCount, entity.getRetryCount())
                .set(StockSyncStateEntity::getDataSource, entity.getDataSource())
                .set(StockSyncStateEntity::getLastError, entity.getLastError());
        return stockSyncStateMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return stockSyncStateMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<StockSyncStateEntity> findById(Long id) {
        return Optional.ofNullable(stockSyncStateMapper.selectById(id));
    }

    @Override
    public Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
        LambdaQueryWrapper<StockSyncStateEntity> queryWrapper = new LambdaQueryWrapper<StockSyncStateEntity>()
                .eq(StockSyncStateEntity::getSymbol, symbol)
                .eq(StockSyncStateEntity::getDataType, dataType);
        return Optional.ofNullable(stockSyncStateMapper.selectOne(queryWrapper));
    }

    @Override
    public PageResult<StockSyncStateEntity> page(StockSyncStatePageReqVO reqVO) {
        LambdaQueryWrapper<StockSyncStateEntity> queryWrapper = buildQueryWrapper(reqVO)
                .orderByDesc(StockSyncStateEntity::getId);
        return RepositoryQueryHelper.selectPage(stockSyncStateMapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    private LambdaQueryWrapper<StockSyncStateEntity> buildQueryWrapper(StockSyncStatePageReqVO reqVO) {
        LambdaQueryWrapper<StockSyncStateEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.likeIfHasText(queryWrapper, StockSyncStateEntity::getSymbol, reqVO.getSymbol());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockSyncStateEntity::getDataType, reqVO.getDataType());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockSyncStateEntity::getSyncStatus, reqVO.getSyncStatus());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockSyncStateEntity::getDataSource, reqVO.getDataSource());
        return queryWrapper;
    }
}
