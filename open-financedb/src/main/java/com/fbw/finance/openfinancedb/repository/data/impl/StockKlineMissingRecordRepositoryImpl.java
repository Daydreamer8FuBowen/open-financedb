package com.fbw.finance.openfinancedb.repository.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;
import com.fbw.finance.openfinancedb.model.enums.MissingRecordStatus;
import com.fbw.finance.openfinancedb.repository.data.StockKlineMissingRecordRepository;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockKlineMissingRecordMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class StockKlineMissingRecordRepositoryImpl implements StockKlineMissingRecordRepository {

    private final StockKlineMissingRecordMapper mapper;

    public StockKlineMissingRecordRepositoryImpl(StockKlineMissingRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long create(StockKlineMissingRecordEntity entity) {
        return mapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(StockKlineMissingRecordEntity entity) {
        LambdaUpdateWrapper<StockKlineMissingRecordEntity> updateWrapper = new LambdaUpdateWrapper<StockKlineMissingRecordEntity>()
                .eq(StockKlineMissingRecordEntity::getId, entity.getId())
                .set(StockKlineMissingRecordEntity::getSymbol, entity.getSymbol())
                .set(StockKlineMissingRecordEntity::getDataType, entity.getDataType())
                .set(StockKlineMissingRecordEntity::getDataSource, entity.getDataSource())
                .set(StockKlineMissingRecordEntity::getMissingDate, entity.getMissingDate())
                .set(StockKlineMissingRecordEntity::getStatus, entity.getStatus())
                .set(StockKlineMissingRecordEntity::getDetectedAt, entity.getDetectedAt())
                .set(StockKlineMissingRecordEntity::getRepairedAt, entity.getRepairedAt())
                .set(StockKlineMissingRecordEntity::getRemark, entity.getRemark());
        return mapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean upsertMissingDate(StockKlineMissingRecordEntity entity) {
        Optional<StockKlineMissingRecordEntity> existing = findBySymbolAndDataTypeAndDataSourceAndMissingDate(
                entity.getSymbol(),
                entity.getDataType(),
                entity.getDataSource(),
                entity.getMissingDate()
        );
        if (existing.isEmpty()) {
            if (entity.getStatus() == null) {
                entity.setStatus(MissingRecordStatus.OPEN.getCode());
            }
            if (entity.getDetectedAt() == null) {
                entity.setDetectedAt(LocalDateTime.now());
            }
            return create(entity) != null;
        }
        StockKlineMissingRecordEntity current = existing.get();
        LambdaUpdateWrapper<StockKlineMissingRecordEntity> updateWrapper = new LambdaUpdateWrapper<StockKlineMissingRecordEntity>()
                .eq(StockKlineMissingRecordEntity::getId, current.getId())
                .set(StockKlineMissingRecordEntity::getStatus, MissingRecordStatus.OPEN.getCode())
                .set(StockKlineMissingRecordEntity::getDetectedAt, entity.getDetectedAt() == null ? LocalDateTime.now() : entity.getDetectedAt())
                .set(StockKlineMissingRecordEntity::getRepairedAt, null)
                .set(StringUtils.hasText(entity.getRemark()), StockKlineMissingRecordEntity::getRemark, entity.getRemark());
        return mapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<StockKlineMissingRecordEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<StockKlineMissingRecordEntity> findBySymbolAndDataTypeAndDataSourceAndMissingDate(
            String symbol,
            String dataType,
            String dataSource,
            LocalDate missingDate) {
        LambdaQueryWrapper<StockKlineMissingRecordEntity> queryWrapper = new LambdaQueryWrapper<StockKlineMissingRecordEntity>()
                .eq(StockKlineMissingRecordEntity::getSymbol, symbol)
                .eq(StockKlineMissingRecordEntity::getDataType, dataType)
                .eq(StockKlineMissingRecordEntity::getDataSource, dataSource)
                .eq(StockKlineMissingRecordEntity::getMissingDate, missingDate);
        return Optional.ofNullable(mapper.selectOne(queryWrapper));
    }

    @Override
    public PageResult<StockKlineMissingRecordEntity> page(StockKlineMissingRecordPageReqVO reqVO) {
        LambdaQueryWrapper<StockKlineMissingRecordEntity> queryWrapper = buildQueryWrapper(reqVO)
                .orderByDesc(StockKlineMissingRecordEntity::getMissingDate)
                .orderByAsc(StockKlineMissingRecordEntity::getSymbol);
        return RepositoryQueryHelper.selectPage(mapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    @Override
    public List<LocalDate> findOpenMissingDates(String symbol, String dataType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<StockKlineMissingRecordEntity> queryWrapper = new LambdaQueryWrapper<StockKlineMissingRecordEntity>()
                .select(StockKlineMissingRecordEntity::getMissingDate)
                .eq(StockKlineMissingRecordEntity::getSymbol, symbol)
                .eq(StockKlineMissingRecordEntity::getDataType, dataType)
                .eq(StockKlineMissingRecordEntity::getStatus, MissingRecordStatus.OPEN.getCode())
                .ge(StockKlineMissingRecordEntity::getMissingDate, startDate)
                .le(StockKlineMissingRecordEntity::getMissingDate, endDate)
                .orderByAsc(StockKlineMissingRecordEntity::getMissingDate);
        return mapper.selectList(queryWrapper).stream()
                .map(StockKlineMissingRecordEntity::getMissingDate)
                .toList();
    }

    private LambdaQueryWrapper<StockKlineMissingRecordEntity> buildQueryWrapper(StockKlineMissingRecordPageReqVO reqVO) {
        LambdaQueryWrapper<StockKlineMissingRecordEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.likeIfHasText(queryWrapper, StockKlineMissingRecordEntity::getSymbol, reqVO.getSymbol());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockKlineMissingRecordEntity::getDataType, reqVO.getDataType());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockKlineMissingRecordEntity::getDataSource, reqVO.getDataSource());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, StockKlineMissingRecordEntity::getStatus, reqVO.getStatus());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, StockKlineMissingRecordEntity::getMissingDate, reqVO.getMissingDate());
        if (reqVO.getStartDate() != null) {
            queryWrapper.ge(StockKlineMissingRecordEntity::getMissingDate, reqVO.getStartDate());
        }
        if (reqVO.getEndDate() != null) {
            queryWrapper.le(StockKlineMissingRecordEntity::getMissingDate, reqVO.getEndDate());
        }
        return queryWrapper;
    }
}
