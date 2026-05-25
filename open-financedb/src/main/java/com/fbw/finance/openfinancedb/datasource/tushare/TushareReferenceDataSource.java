package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import java.time.LocalDate;
import java.util.List;

public interface TushareReferenceDataSource {

    List<StockInfoEntity> fetchStockBasicList();

    List<TradeCalendarEntity> fetchTradeCalendar(String exchange, LocalDate startDate, LocalDate endDate);

    List<AdjFactorPoint> fetchAdjFactors(String symbol, LocalDate startDate, LocalDate endDate);
}
