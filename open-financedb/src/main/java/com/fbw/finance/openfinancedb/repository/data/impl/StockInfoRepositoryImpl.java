package com.fbw.finance.openfinancedb.repository.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockInfoMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StockInfoRepositoryImpl implements StockInfoRepository {

    private final StockInfoMapper stockInfoMapper;

    public StockInfoRepositoryImpl(StockInfoMapper stockInfoMapper) {
        this.stockInfoMapper = stockInfoMapper;
    }

    @Override
    public Long create(StockInfoEntity entity) {
        return stockInfoMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(StockInfoEntity entity) {
        LambdaUpdateWrapper<StockInfoEntity> updateWrapper = new LambdaUpdateWrapper<StockInfoEntity>()
                .eq(StockInfoEntity::getId, entity.getId())
                .set(StockInfoEntity::getIsRealtimeSyncEnabled, entity.getIsRealtimeSyncEnabled());
        fillBaseUpdate(updateWrapper, entity);
        return stockInfoMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean upsertPreservingRealtimeFlag(StockInfoEntity entity) {
        Optional<StockInfoEntity> existing = findBySymbol(entity.getSymbol());
        if (existing.isEmpty()) {
            if (entity.getIsRealtimeSyncEnabled() == null) {
                entity.setIsRealtimeSyncEnabled(false);
            }
            return create(entity) != null;
        }
        entity.setId(existing.get().getId());
        LambdaUpdateWrapper<StockInfoEntity> updateWrapper = new LambdaUpdateWrapper<StockInfoEntity>()
                .eq(StockInfoEntity::getId, entity.getId());
        fillBaseUpdate(updateWrapper, entity);
        return stockInfoMapper.update(null, updateWrapper) > 0;
    }

    private void fillBaseUpdate(LambdaUpdateWrapper<StockInfoEntity> updateWrapper, StockInfoEntity entity) {
        updateWrapper
                .set(StockInfoEntity::getSymbol, entity.getSymbol())
                .set(StockInfoEntity::getRawSymbol, entity.getRawSymbol())
                .set(StockInfoEntity::getName, entity.getName())
                .set(StockInfoEntity::getExchange, entity.getExchange())
                .set(StockInfoEntity::getMarket, entity.getMarket())
                .set(StockInfoEntity::getArea, entity.getArea())
                .set(StockInfoEntity::getIndustry, entity.getIndustry())
                .set(StockInfoEntity::getType, entity.getType())
                .set(StockInfoEntity::getListDate, entity.getListDate())
                .set(StockInfoEntity::getDelistDate, entity.getDelistDate())
                .set(StockInfoEntity::getStatus, entity.getStatus())
                .set(StockInfoEntity::getActEntType, entity.getActEntType())
                .set(StockInfoEntity::getDataSource, entity.getDataSource())
                .set(StockInfoEntity::getLatestQuoteDate, entity.getLatestQuoteDate());
    }

    @Override
    public boolean deleteById(Long id) {
        return stockInfoMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<StockInfoEntity> findById(Long id) {
        return Optional.ofNullable(stockInfoMapper.selectById(id));
    }

    @Override
    public Optional<StockInfoEntity> findBySymbol(String symbol) {
        LambdaQueryWrapper<StockInfoEntity> queryWrapper = new LambdaQueryWrapper<StockInfoEntity>()
                .eq(StockInfoEntity::getSymbol, symbol);
        return Optional.ofNullable(stockInfoMapper.selectOne(queryWrapper));
    }

    @Override
    public List<StockInfoEntity> findRealtimeSyncEnabled() {
        LambdaQueryWrapper<StockInfoEntity> queryWrapper = new LambdaQueryWrapper<StockInfoEntity>()
                .eq(StockInfoEntity::getIsRealtimeSyncEnabled, true)
                .eq(StockInfoEntity::getStatus, "LISTED")
                .orderByAsc(StockInfoEntity::getSymbol);
        return stockInfoMapper.selectList(queryWrapper);
    }

    @Override
    public PageResult<StockInfoEntity> page(StockInfoPageReqVO reqVO) {
        LambdaQueryWrapper<StockInfoEntity> queryWrapper = buildQueryWrapper(reqVO)
                .orderByDesc(StockInfoEntity::getId);
        return RepositoryQueryHelper.selectPage(stockInfoMapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    @Override
    public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
        LambdaUpdateWrapper<StockInfoEntity> updateWrapper = new LambdaUpdateWrapper<StockInfoEntity>()
                .in(StockInfoEntity::getId, ids)
                .set(StockInfoEntity::getIsRealtimeSyncEnabled, enabled);
        return stockInfoMapper.update(null, updateWrapper);
    }

    private LambdaQueryWrapper<StockInfoEntity> buildQueryWrapper(StockInfoPageReqVO reqVO) {
        LambdaQueryWrapper<StockInfoEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.likeIfHasText(queryWrapper, StockInfoEntity::getSymbol, reqVO.getSymbol());
        RepositoryQueryHelper.likeIfHasText(queryWrapper, StockInfoEntity::getName, reqVO.getName());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockInfoEntity::getExchange, reqVO.getExchange());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockInfoEntity::getMarket, reqVO.getMarket());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockInfoEntity::getType, reqVO.getType());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockInfoEntity::getStatus, reqVO.getStatus());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, StockInfoEntity::getIsRealtimeSyncEnabled, reqVO.getIsRealtimeSyncEnabled());
        return queryWrapper;
    }
}
