package com.fbw.finance.openfinancedb.repository.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradeCalendarRepository {

    Long create(TradeCalendarEntity entity);

    boolean update(TradeCalendarEntity entity);

    boolean upsertByExchangeAndTradeDate(TradeCalendarEntity entity);

    boolean deleteById(Long id);

    Optional<TradeCalendarEntity> findById(Long id);

    Optional<TradeCalendarEntity> findByExchangeAndTradeDate(String exchange, LocalDate tradeDate);

    long count();

    List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate);

    PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO);
}
