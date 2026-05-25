package com.fbw.finance.openfinancedb.repository.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.repository.data.mapper.SyncLogMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SyncLogRepositoryImpl implements SyncLogRepository {

    private final SyncLogMapper syncLogMapper;

    public SyncLogRepositoryImpl(SyncLogMapper syncLogMapper) {
        this.syncLogMapper = syncLogMapper;
    }

    @Override
    public Long create(SyncLogEntity entity) {
        return syncLogMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(SyncLogEntity entity) {
        LambdaUpdateWrapper<SyncLogEntity> updateWrapper = new LambdaUpdateWrapper<SyncLogEntity>()
                .eq(SyncLogEntity::getId, entity.getId())
                .set(SyncLogEntity::getLogId, entity.getLogId())
                .set(SyncLogEntity::getTaskId, entity.getTaskId())
                .set(SyncLogEntity::getSymbol, entity.getSymbol())
                .set(SyncLogEntity::getDataType, entity.getDataType())
                .set(SyncLogEntity::getDataSource, entity.getDataSource())
                .set(SyncLogEntity::getStartTime, entity.getStartTime())
                .set(SyncLogEntity::getEndTime, entity.getEndTime())
                .set(SyncLogEntity::getFetchLatencyMs, entity.getFetchLatencyMs())
                .set(SyncLogEntity::getCleanLatencyMs, entity.getCleanLatencyMs())
                .set(SyncLogEntity::getWriteLatencyMs, entity.getWriteLatencyMs())
                .set(SyncLogEntity::getTotalLatencyMs, entity.getTotalLatencyMs())
                .set(SyncLogEntity::getFetchedCount, entity.getFetchedCount())
                .set(SyncLogEntity::getCleanedCount, entity.getCleanedCount())
                .set(SyncLogEntity::getWrittenCount, entity.getWrittenCount())
                .set(SyncLogEntity::getSuccess, entity.getSuccess())
                .set(SyncLogEntity::getErrorType, entity.getErrorType())
                .set(SyncLogEntity::getErrorMessage, entity.getErrorMessage());
        return syncLogMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return syncLogMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<SyncLogEntity> findById(Long id) {
        return Optional.ofNullable(syncLogMapper.selectById(id));
    }

    @Override
    public Optional<SyncLogEntity> findByLogId(String logId) {
        LambdaQueryWrapper<SyncLogEntity> queryWrapper = new LambdaQueryWrapper<SyncLogEntity>()
                .eq(SyncLogEntity::getLogId, logId);
        return Optional.ofNullable(syncLogMapper.selectOne(queryWrapper));
    }

    @Override
    public PageResult<SyncLogEntity> page(SyncLogPageReqVO reqVO) {
        LambdaQueryWrapper<SyncLogEntity> queryWrapper = buildQueryWrapper(reqVO)
                .orderByDesc(SyncLogEntity::getId);
        return RepositoryQueryHelper.selectPage(syncLogMapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    private LambdaQueryWrapper<SyncLogEntity> buildQueryWrapper(SyncLogPageReqVO reqVO) {
        LambdaQueryWrapper<SyncLogEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.likeIfHasText(queryWrapper, SyncLogEntity::getLogId, reqVO.getLogId());
        RepositoryQueryHelper.likeIfHasText(queryWrapper, SyncLogEntity::getTaskId, reqVO.getTaskId());
        RepositoryQueryHelper.likeIfHasText(queryWrapper, SyncLogEntity::getSymbol, reqVO.getSymbol());
        RepositoryQueryHelper.eqIfHasText(queryWrapper, SyncLogEntity::getDataType, reqVO.getDataType());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, SyncLogEntity::getSuccess, reqVO.getSuccess());
        return queryWrapper;
    }
}
