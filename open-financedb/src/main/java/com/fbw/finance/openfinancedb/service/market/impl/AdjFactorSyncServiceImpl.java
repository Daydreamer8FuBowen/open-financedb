package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareReferenceDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.AdjFactorRepository;
import com.fbw.finance.openfinancedb.service.market.AdjFactorSyncService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdjFactorSyncServiceImpl implements AdjFactorSyncService {

    private static final Logger log = LoggerFactory.getLogger(AdjFactorSyncServiceImpl.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_FETCH_YEARS = 3;

    private final StockInfoRepository stockInfoRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final AdjFactorRepository adjFactorRepository;
    private final TushareReferenceDataSource tushareReferenceDataSource;
    private final Clock clock;
    private final LocalDate defaultStartDate;
    private final ExecutorService executor;

    @Autowired
    public AdjFactorSyncServiceImpl(
            StockInfoRepository stockInfoRepository,
            TradeCalendarRepository tradeCalendarRepository,
            AdjFactorRepository adjFactorRepository,
            TushareReferenceDataSource tushareReferenceDataSource,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate defaultStartDate,
            @Value("${finance.adj-factor-sync.pool-size:2}") int poolSize) {
        this(
                stockInfoRepository,
                tradeCalendarRepository,
                adjFactorRepository,
                tushareReferenceDataSource,
                Clock.system(MARKET_ZONE),
                defaultStartDate,
                poolSize
        );
    }

    public AdjFactorSyncServiceImpl(
            StockInfoRepository stockInfoRepository,
            TradeCalendarRepository tradeCalendarRepository,
            AdjFactorRepository adjFactorRepository,
            TushareReferenceDataSource tushareReferenceDataSource,
            Clock clock,
            LocalDate defaultStartDate,
            int poolSize) {
        this.stockInfoRepository = stockInfoRepository;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.adjFactorRepository = adjFactorRepository;
        this.tushareReferenceDataSource = tushareReferenceDataSource;
        this.clock = clock;
        this.defaultStartDate = defaultStartDate;
        this.executor = Executors.newFixedThreadPool(Math.max(1, poolSize), new AdjFactorThreadFactory());
    }

    @Override
    public void syncDailyIfTradingDay() {
        LocalDate today = LocalDate.now(clock);
        if (!isTradingDay(today)) {
            log.info("Adj factor daily sync skipped because {} is not a trading day", today);
            return;
        }
        List<StockInfoEntity> stocks = stockInfoRepository.findRealtimeSyncEnabled();
        if (stocks.isEmpty()) {
            log.info("Adj factor daily sync skipped because no listed symbols enable realtime sync");
            return;
        }
        for (StockInfoEntity stock : stocks) {
            syncStockHistory(stock);
        }
    }

    @Override
    public void syncStockHistory(StockInfoEntity stock) {
        if (stock == null || stock.getSymbol() == null || stock.getSymbol().isBlank()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = resolveStartDate(stock);
        if (startDate.isAfter(today)) {
            log.info("Adj factor sync skipped, symbol={} startDate={} is after today={}",
                    stock.getSymbol(), startDate, today);
            return;
        }
        syncSlices(stock.getSymbol(), startDate, today);
    }

    @Override
    public void syncStockHistoryAsync(StockInfoEntity stock) {
        executor.submit(() -> {
            try {
                syncStockHistory(stock);
            } catch (RuntimeException ex) {
                String symbol = stock == null ? null : stock.getSymbol();
                log.error("Adj factor async sync failed, symbol={} reason={}", symbol, ex.getMessage(), ex);
            }
        });
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Adj factor sync executor stop timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTradingDay(LocalDate today) {
        return isOpen("SSE", today) || isOpen("SZSE", today);
    }

    private boolean isOpen(String exchange, LocalDate today) {
        return tradeCalendarRepository.findByExchangeAndTradeDate(exchange, today)
                .map(TradeCalendarEntity::getIsOpen)
                .orElse(false);
    }

    private LocalDate resolveStartDate(StockInfoEntity stock) {
        LocalDate latestDate = adjFactorRepository.findLatestTradeDate(stock.getSymbol()).orElse(null);
        LocalDate startDate = latestDate == null ? defaultStartDate : latestDate.plusDays(1);
        if (stock.getListDate() != null && startDate.isBefore(stock.getListDate())) {
            startDate = stock.getListDate();
        }
        return startDate;
    }

    private void syncSlices(String symbol, LocalDate startDate, LocalDate endDate) {
        LocalDate sliceStart = startDate;
        while (!sliceStart.isAfter(endDate)) {
            LocalDate sliceEnd = sliceStart.plusYears(MAX_FETCH_YEARS).minusDays(1);
            if (sliceEnd.isAfter(endDate)) {
                sliceEnd = endDate;
            }
            List<AdjFactorPoint> factors = tushareReferenceDataSource.fetchAdjFactors(symbol, sliceStart, sliceEnd);
            adjFactorRepository.upsert(factors);
            log.info("Adj factor sync slice completed, symbol={} startDate={} endDate={} fetched={}",
                    symbol, sliceStart, sliceEnd, factors.size());
            sliceStart = sliceEnd.plusDays(1);
        }
    }

    private static final class AdjFactorThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "adj-factor-sync-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
