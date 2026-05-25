package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.SyncLogRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;

public interface SyncLogService {

    Long create(SyncLogCreateReqVO reqVO);

    void update(Long id, SyncLogUpdateReqVO reqVO);

    void delete(Long id);

    SyncLogRespVO get(Long id);

    PageResult<SyncLogRespVO> page(SyncLogPageReqVO reqVO);
}
