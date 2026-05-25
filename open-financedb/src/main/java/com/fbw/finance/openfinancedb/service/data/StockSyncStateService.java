package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockSyncStateRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;

public interface StockSyncStateService {

    Long create(StockSyncStateCreateReqVO reqVO);

    void update(Long id, StockSyncStateUpdateReqVO reqVO);

    void delete(Long id);

    StockSyncStateRespVO get(Long id);

    PageResult<StockSyncStateRespVO> page(StockSyncStatePageReqVO reqVO);
}
