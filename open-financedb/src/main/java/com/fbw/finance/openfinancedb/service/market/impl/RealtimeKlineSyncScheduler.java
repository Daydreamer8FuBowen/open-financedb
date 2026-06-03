package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "finance.realtime-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeKlineSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeKlineSyncScheduler.class);

    private final StockInfoRepository stockInfoRepository;
    private final TushareKlineDataSource tushareKlineDataSource;
    private final KlineRepository klineRepository;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final RealtimeKlineSyncMonitor monitor;
    private final Clock clock;
    private final int poolSize;
    private final long retrySleepMillis;

    @Autowired
    public RealtimeKlineSyncScheduler(
            StockInfoRepository stockInfoRepository,
            TushareKlineDataSource tushareKlineDataSource,
            KlineRepository klineRepository,
            TradeMinuteWindowService tradeMinuteWindowService,
            RealtimeKlineSyncMonitor monitor,
            @Value("${finance.realtime-sync.pool-size:4}") int poolSize,
            @Value("${finance.realtime-sync.retry-sleep-millis:1000}") long retrySleepMillis) {
        this(
                stockInfoRepository,
                tushareKlineDataSource,
                klineRepository,
                tradeMinuteWindowService,
                monitor,
                Clock.systemUTC(),
                poolSize,
                retrySleepMillis
        );
    }

    public RealtimeKlineSyncScheduler(
            StockInfoRepository stockInfoRepository,
            TushareKlineDataSource tushareKlineDataSource,
            KlineRepository klineRepository,
            TradeMinuteWindowService tradeMinuteWindowService,
            RealtimeKlineSyncMonitor monitor,
            Clock clock,
            int poolSize,
            long retrySleepMillis) {
        this.stockInfoRepository = stockInfoRepository;
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.klineRepository = klineRepository;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.monitor = monitor;
        this.clock = clock;
        this.poolSize = Math.max(1, poolSize);
        this.retrySleepMillis = Math.max(1L, retrySleepMillis);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void syncRealtimeMinuteBars() {
        List<String> symbols = stockInfoRepository.findRealtimeSyncEnabled().stream()
                .map(StockInfoEntity::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .toList();
        if (symbols.isEmpty()) {
            log.info("Realtime minute kline sync skipped because no listed symbols enable realtime sync");
            return;
        }
        // Split symbols into chunks of max symbols per request
        List<List<String>> chunks = chunks(symbols, TushareKlineDataSource.REALTIME_MINUTE_MAX_SYMBOLS);
        String roundId = "rt-" + UUID.randomUUID();
        monitor.startRound(roundId, clock.instant(), symbols.size(), chunks.size(), 1);
        try {
            int totalBars = 0;
            for (List<String> chunk : chunks) {
                // TODO ： 这里是同步调用不能发挥Realtime的效率
                List<KlineBar> bars = tushareKlineDataSource.fetchRealtimeMinuteBars(chunk, KlinePeriod.MINUTE_1);
                klineRepository.upsert(bars);
                monitor.recordChunkSuccess(roundId, bars.size());
                totalBars += bars.size();
            }
            monitor.finishRound(roundId, clock.instant());
            log.info("Realtime minute kline sync round completed, roundId={} symbols={} chunks={} bars={}",
                    roundId, symbols.size(), chunks.size(), totalBars);
        } catch (RuntimeException ex) {
            monitor.failRound(roundId, ex.getMessage(), clock.instant());
            log.error("Realtime minute kline sync failed, roundId={} reason={}", roundId, ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void stop() {
        // Sequential scheduler no longer keeps background workers alive between rounds.
    }

    private List<List<String>> chunks(List<String> symbols, int chunkSize) {
        List<List<String>> result = new ArrayList<>();
        for (int start = 0; start < symbols.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, symbols.size());
            result.add(List.copyOf(symbols.subList(start, end)));
        }
        return result;
    }
}
