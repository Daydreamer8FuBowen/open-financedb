package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.SyncLogRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.service.data.SyncLogService;
import com.fbw.finance.openfinancedb.service.data.convert.SyncLogConvert;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncLogServiceImpl implements SyncLogService {

    private final SyncLogRepository syncLogRepository;

    public SyncLogServiceImpl(SyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    @Override
    @Transactional
    public Long create(SyncLogCreateReqVO reqVO) {
        validateLogIdUnique(reqVO.getLogId(), null);
        SyncLogEntity entity = SyncLogConvert.toEntity(reqVO);
        Long id = syncLogRepository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create sync log");
        }
        return id;
    }

    @Override
    @Transactional
    public void update(Long id, SyncLogUpdateReqVO reqVO) {
        SyncLogEntity entity = getEntity(id);
        validateLogIdUnique(reqVO.getLogId(), id);
        SyncLogConvert.copy(reqVO, entity);
        entity.setId(id);
        if (!syncLogRepository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update sync log");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        syncLogRepository.deleteById(id);
    }

    @Override
    public SyncLogRespVO get(Long id) {
        return SyncLogConvert.toRespVO(getEntity(id));
    }

    @Override
    public PageResult<SyncLogRespVO> page(SyncLogPageReqVO reqVO) {
        PageResult<SyncLogEntity> pageResult = syncLogRepository.page(reqVO);
        List<SyncLogRespVO> list = pageResult.getList().stream().map(SyncLogConvert::toRespVO).toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    private SyncLogEntity getEntity(Long id) {
        return syncLogRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.SYNC_LOG_NOT_FOUND, "sync log not found"));
    }

    private void validateLogIdUnique(String logId, Long currentId) {
        syncLogRepository.findByLogId(logId).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ServiceException(ErrorCodeConstants.SYNC_LOG_LOG_ID_DUPLICATE, "sync log id already exists");
            }
        });
    }
}
