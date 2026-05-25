package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.SyncSlice;
import com.fbw.finance.openfinancedb.service.market.KlineCompletionService;
import com.fbw.finance.openfinancedb.service.market.KlineSyncService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KlineCompletionServiceImpl implements KlineCompletionService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final TushareKlineDataSource tushareKlineDataSource;
    private final KlineSyncService klineSyncService;

    public KlineCompletionServiceImpl(TushareKlineDataSource tushareKlineDataSource, KlineSyncService klineSyncService) {
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.klineSyncService = klineSyncService;
    }

    @Override
    public void completeMinuteData(KlineQuery query) {
        LocalDateTime startTime = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE);
        LocalDateTime endTime = LocalDateTime.ofInstant(query.endTime(), MARKET_ZONE);
        List<KlineBar> bars = tushareKlineDataSource.fetchMinuteBars(query.symbol(), startTime, endTime);
        klineSyncService.persistMinuteSlice(new SyncSlice(query.symbol(), query.startTime(), query.endTime()), bars);
    }
}
