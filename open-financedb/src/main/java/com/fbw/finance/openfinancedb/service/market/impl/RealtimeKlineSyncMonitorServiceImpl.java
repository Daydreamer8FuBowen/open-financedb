package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RealtimeKlineSyncMonitorServiceImpl implements RealtimeKlineSyncMonitorService {

    private final RealtimeKlineSyncMonitor monitor;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public RealtimeKlineSyncMonitorServiceImpl(
            RealtimeKlineSyncMonitor monitor,
            TradeMinuteWindowService tradeMinuteWindowService,
            @Value("${finance.realtime-sync.enabled:true}") boolean enabled) {
        this(monitor, tradeMinuteWindowService, Clock.systemUTC(), enabled);
    }

    RealtimeKlineSyncMonitorServiceImpl(
            RealtimeKlineSyncMonitor monitor,
            TradeMinuteWindowService tradeMinuteWindowService,
            Clock clock,
            boolean enabled) {
        this.monitor = monitor;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public RealtimeKlineSyncStatusSnapshot getStatus() {
        Instant now = clock.instant();
        return monitor.snapshot(enabled, tradeMinuteWindowService.isTradingTime(now), now);
    }
}
