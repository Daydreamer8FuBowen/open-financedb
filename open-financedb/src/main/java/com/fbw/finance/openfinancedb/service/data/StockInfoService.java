package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;

public interface StockInfoService {

    Long create(StockInfoCreateReqVO reqVO);

    void update(Long id, StockInfoUpdateReqVO reqVO);

    void delete(Long id);

    StockInfoRespVO get(Long id);

    PageResult<StockInfoRespVO> page(StockInfoPageReqVO reqVO);

    int batchUpdateSyncEnabled(StockInfoBatchSyncReqVO reqVO);
}
