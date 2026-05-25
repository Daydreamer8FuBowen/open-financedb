package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.SyncSlice;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.KlineSyncService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KlineSyncServiceImpl implements KlineSyncService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final KlineRepository klineRepository;
    private final StockSyncStateRepository stockSyncStateRepository;

    public KlineSyncServiceImpl(KlineRepository klineRepository, StockSyncStateRepository stockSyncStateRepository) {
        this.klineRepository = klineRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
    }

    @Override
    public void persistMinuteSlice(SyncSlice slice, List<KlineBar> bars) {
        // State advances only after the K-line write succeeds. If upsert throws, the cursor stays
        // unchanged and the same slice can be retried.
        klineRepository.upsert(bars);

        StockSyncStateEntity state = stockSyncStateRepository
                .findBySymbolAndDataType(slice.symbol(), SyncDataType.MINUTE_1M.getCode())
                .orElseGet(() -> newState(slice));
        state.setLatestSyncTime(LocalDateTime.ofInstant(slice.endTime(), MARKET_ZONE));
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());

        if (state.getId() == null) {
            stockSyncStateRepository.create(state);
        } else {
            stockSyncStateRepository.update(state);
        }
    }

    private StockSyncStateEntity newState(SyncSlice slice) {
        // Query-time completion may encounter a symbol before the background sync loop has created
        // its cursor, so the slice writer creates the minimal state row idempotently.
        StockSyncStateEntity entity = new StockSyncStateEntity();
        entity.setSymbol(slice.symbol());
        entity.setDataType(SyncDataType.MINUTE_1M.getCode());
        entity.setStartTime(LocalDateTime.ofInstant(slice.startTime(), MARKET_ZONE));
        entity.setSyncStatus(SyncStatus.PENDING.getCode());
        entity.setRetryCount(0);
        entity.setDataSource("tushare");
        return entity;
    }
}
