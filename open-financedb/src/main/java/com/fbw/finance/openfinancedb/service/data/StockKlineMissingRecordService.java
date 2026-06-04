package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordStatusReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockKlineMissingRecordRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import java.time.LocalDate;

public interface StockKlineMissingRecordService {

    Long create(StockKlineMissingRecordCreateReqVO reqVO);

    void update(Long id, StockKlineMissingRecordUpdateReqVO reqVO);

    void delete(Long id);

    StockKlineMissingRecordRespVO get(Long id);

    PageResult<StockKlineMissingRecordRespVO> page(StockKlineMissingRecordPageReqVO reqVO);

    void changeStatus(Long id, StockKlineMissingRecordStatusReqVO reqVO);

    void changeStatus(String symbol, String dataType, String dataSource, LocalDate missingDate, String status, String remark);
}
