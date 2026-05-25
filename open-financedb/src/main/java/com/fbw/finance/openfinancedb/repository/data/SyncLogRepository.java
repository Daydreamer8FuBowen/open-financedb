package com.fbw.finance.openfinancedb.repository.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import java.util.Optional;

public interface SyncLogRepository {

    Long create(SyncLogEntity entity);

    boolean update(SyncLogEntity entity);

    boolean deleteById(Long id);

    Optional<SyncLogEntity> findById(Long id);

    Optional<SyncLogEntity> findByLogId(String logId);

    PageResult<SyncLogEntity> page(SyncLogPageReqVO reqVO);
}
