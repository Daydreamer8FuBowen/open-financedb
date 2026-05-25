package com.fbw.finance.openfinancedb.service.bootstrap.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareReferenceDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.bootstrap.TradeCalendarBootstrapService;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TradeCalendarBootstrapServiceImpl implements TradeCalendarBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(TradeCalendarBootstrapServiceImpl.class);
    private static final List<String> DEFAULT_EXCHANGES = List.of("SSE", "SZSE");

    private final TushareReferenceDataSource tushareReferenceDataSource;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final LocalDate startDate;

    public TradeCalendarBootstrapServiceImpl(
            TushareReferenceDataSource tushareReferenceDataSource,
            TradeCalendarRepository tradeCalendarRepository,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate startDate) {
        this.tushareReferenceDataSource = tushareReferenceDataSource;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.startDate = startDate;
    }

    @Override
    public void initializeIfEmpty() {
        long existingCount = tradeCalendarRepository.count();
        if (existingCount > 0) {
            log.info("启动装载交易日历：MySQL 已有交易日历 {} 条，跳过初始化", existingCount);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = LocalDate.of(today.getYear(), 12, 31);
        log.info("启动装载交易日历：表为空，开始初始化，交易所={}，范围={} 至 {}", DEFAULT_EXCHANGES, startDate, endDate);
        int success = 0;
        for (String exchange : DEFAULT_EXCHANGES) {
            List<TradeCalendarEntity> calendars = tushareReferenceDataSource.fetchTradeCalendar(exchange, startDate, endDate);
            for (TradeCalendarEntity calendar : calendars) {
                if (tradeCalendarRepository.upsertByExchangeAndTradeDate(calendar)) {
                    success++;
                }
            }
            log.info("启动装载交易日历：交易所={} 返回={} 条", exchange, calendars.size());
        }
        log.info("启动装载交易日历：完成，写入成功={} 条", success);
    }
}
