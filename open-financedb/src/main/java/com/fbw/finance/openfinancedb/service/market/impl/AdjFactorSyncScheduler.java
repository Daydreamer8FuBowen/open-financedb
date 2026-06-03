package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.service.market.AdjFactorSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "finance.adj-factor-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdjFactorSyncScheduler {

    private final AdjFactorSyncService adjFactorSyncService;

    public AdjFactorSyncScheduler(AdjFactorSyncService adjFactorSyncService) {
        this.adjFactorSyncService = adjFactorSyncService;
    }

    @Scheduled(cron = "${finance.adj-factor-sync.cron:0 0 23 * * ?}", zone = "Asia/Shanghai")
    public void syncDailyAdjFactors() {
        adjFactorSyncService.syncDailyIfTradingDay();
    }
}
