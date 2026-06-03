package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;

public interface AdjFactorSyncService {

    void syncDailyIfTradingDay();

    void syncStockHistory(StockInfoEntity stock);

    void syncStockHistoryAsync(StockInfoEntity stock);
}
