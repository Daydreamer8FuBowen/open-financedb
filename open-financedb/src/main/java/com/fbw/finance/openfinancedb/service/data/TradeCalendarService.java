package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.TradeCalendarRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;

public interface TradeCalendarService {

    Long create(TradeCalendarCreateReqVO reqVO);

    void update(Long id, TradeCalendarUpdateReqVO reqVO);

    void delete(Long id);

    TradeCalendarRespVO get(Long id);

    PageResult<TradeCalendarRespVO> page(TradeCalendarPageReqVO reqVO);
}
