package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.HistoryKlineSyncWorker;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistoryKlineSyncWorkerImpl implements HistoryKlineSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(HistoryKlineSyncWorkerImpl.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final SyncLogRepository syncLogRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final KlineRepository klineRepository;
    private final TushareKlineDataSource tushareKlineDataSource;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final LocalDate defaultStartDate;
    private final Duration idleSleep;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long scanAfterId = 0L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "history-kline-sync-worker");
        thread.setDaemon(false);
        return thread;
    });

    public HistoryKlineSyncWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            SyncLogRepository syncLogRepository,
            TradeCalendarRepository tradeCalendarRepository,
            KlineRepository klineRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate defaultStartDate,
            @Value("${finance.history-sync.idle-sleep:30s}") Duration idleSleep) {
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.syncLogRepository = syncLogRepository;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.klineRepository = klineRepository;
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.defaultStartDate = defaultStartDate;
        this.idleSleep = idleSleep;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.info("历史分钟线同步线程：已经启动，忽略重复启动请求");
            return;
        }
        executor.submit(this::loop);
        log.info("历史分钟线同步线程：已启动，默认起始日期={}，空闲休眠={}", defaultStartDate, idleSleep);
    }

    @Override
    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("历史分钟线同步线程：停止超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                boolean progressed = runOneRound();
                if (!progressed) {
                    sleepQuietly(idleSleep);
                }
            } catch (RuntimeException ex) {
                log.error("历史分钟线同步线程：循环异常，稍后重试，原因={}", ex.getMessage(), ex);
                sleepQuietly(idleSleep);
            }
        }
        log.info("历史分钟线同步线程：已退出");
    }

    private boolean runOneRound() {
        LocalDate today = LocalDate.now(MARKET_ZONE);
        Map<String, LocalDateTime> targetExclusiveByExchange = new HashMap<>();
        boolean progressed = false;
        while (true) {
            var next = stockInfoRepository.findNextRealtimeSyncEnabledAfterId(scanAfterId);
            if (next.isEmpty()) {
                if (!progressed && scanAfterId == 0L) {
                    log.info("历史分钟线同步线程：没有开启实时同步的股票，本轮休眠");
                }
                scanAfterId = 0L;
                return progressed;
            }
            StockInfoEntity stock = next.get();
            scanAfterId = stock.getId() == null ? scanAfterId : stock.getId();
            String exchange = exchangeForCompleteness(stock);
            LocalDateTime targetExclusive = targetExclusiveByExchange.computeIfAbsent(
                    exchange,
                    key -> resolveTargetExclusive(key, today)
            );
            if (syncNextMonthlySlice(stock, targetExclusive)) {
                progressed = true;
            }
        }
    }

    private LocalDateTime resolveTargetExclusive(String exchange, LocalDate today) {
        LocalDate endDate = today.minusDays(1);
        LocalDate startDate = endDate.minusDays(60);
        List<TradeCalendarEntity> openDays = tradeCalendarRepository.findOpenDays(exchange, startDate, endDate);
        if (openDays.isEmpty()) {
            return today.atStartOfDay();
        }
        LocalDate lastOpenDay = openDays.get(openDays.size() - 1).getTradeDate();
        return lastOpenDay.plusDays(1).atStartOfDay();
    }

    private boolean syncNextMonthlySlice(StockInfoEntity stock, LocalDateTime targetExclusive) {
        StockSyncStateEntity state = stockSyncStateRepository
                .findBySymbolAndDataType(stock.getSymbol(), SyncDataType.MINUTE_1M.getCode())
                .orElseGet(() -> newState(stock, targetExclusive));

        LocalDateTime sliceStart = nextSliceStart(state, stock);
        if (!sliceStart.isBefore(targetExclusive)) {
            return false;
        }
        LocalDateTime sliceEnd = sliceStart.plusMonths(1);
        if (sliceEnd.isAfter(targetExclusive)) {
            sliceEnd = targetExclusive;
        }

        String exchange = exchangeForCompleteness(stock);
        long taskStartMillis = System.currentTimeMillis();
        long preCheckStartMillis = taskStartMillis;
        List<Instant> expectedMinutes = tradeMinuteWindowService.expectedMinuteInstants(
                exchange,
                sliceStart.toLocalDate(),
                sliceEnd.minusNanos(1).toLocalDate()
        );
        long expectedLatencyMs = System.currentTimeMillis() - preCheckStartMillis;
        log.info("历史分钟线同步：symbol={} 片段={} 至 {}，交易所={}，预期分钟数={}",
                stock.getSymbol(), sliceStart, sliceEnd, exchange, expectedMinutes.size());

        int fetchedCount = 0;
        int writtenCount = 0;
        Long fetchLatencyMs = null;
        Long writeLatencyMs = null;
        Long validateLatencyMs = expectedLatencyMs;
        try {
            long existingCheckStartMillis = System.currentTimeMillis();
            if (isSliceComplete(stock.getSymbol(), sliceStart, sliceEnd, expectedMinutes)) {
                long existingCheckLatencyMs = System.currentTimeMillis() - existingCheckStartMillis;
                validateLatencyMs = expectedLatencyMs + existingCheckLatencyMs;
                log.info("历史分钟线同步：symbol={} 片段数据已存在，跳过 Tushare 获取，直接推进状态", stock.getSymbol());
                advanceStateOnly(state, stock, sliceStart, sliceEnd, targetExclusive);
                writeSyncLog(
                        stock.getSymbol(),
                        sliceStart,
                        sliceEnd,
                        0,
                        0,
                        true,
                        null,
                        null,
                        taskStartMillis,
                        0L,
                        0L,
                        validateLatencyMs
                );
                log.info(
                        "历史分钟线同步耗时：symbol={} 片段={} 至 {} expected={}ms existingCheck={}ms total={}ms",
                        stock.getSymbol(),
                        sliceStart,
                        sliceEnd,
                        expectedLatencyMs,
                        existingCheckLatencyMs,
                        System.currentTimeMillis() - taskStartMillis
                );
                return true;
            }

            long fetchStartMillis = System.currentTimeMillis();
            List<KlineBar> bars = tushareKlineDataSource.fetchMinuteBars(stock.getSymbol(), sliceStart, sliceEnd);
            fetchLatencyMs = System.currentTimeMillis() - fetchStartMillis;
            fetchedCount = bars.size();
            log.info("历史分钟线同步：symbol={} 从 Tushare 获取 {} 条分钟线，开始写入 InfluxDB", stock.getSymbol(), bars.size());
            long writeStartMillis = System.currentTimeMillis();
            klineRepository.upsert(bars);
            writeLatencyMs = System.currentTimeMillis() - writeStartMillis;
            writtenCount = bars.size();
            long verifyStartMillis = System.currentTimeMillis();
            assertSliceComplete(stock.getSymbol(), sliceStart, sliceEnd, expectedMinutes);
            long verifyLatencyMs = System.currentTimeMillis() - verifyStartMillis;
            validateLatencyMs = expectedLatencyMs + verifyLatencyMs;
            advanceStateOnly(state, stock, sliceStart, sliceEnd, targetExclusive);
            writeSyncLog(
                    stock.getSymbol(),
                    sliceStart,
                    sliceEnd,
                    fetchedCount,
                    writtenCount,
                    true,
                    null,
                    null,
                    taskStartMillis,
                    fetchLatencyMs,
                    writeLatencyMs,
                    validateLatencyMs
            );
            log.info(
                    "历史分钟线同步：symbol={} 片段写入完成，状态推进至 {}，fetched={} expected={}",
                    stock.getSymbol(),
                    sliceEnd,
                    bars.size(),
                    expectedMinutes.size()
            );
            log.info(
                    "历史分钟线同步耗时：symbol={} 片段={} 至 {} expected={}ms fetch={}ms write={}ms verify={}ms total={}ms",
                    stock.getSymbol(),
                    sliceStart,
                    sliceEnd,
                    expectedLatencyMs,
                    fetchLatencyMs,
                    writeLatencyMs,
                    verifyLatencyMs,
                    System.currentTimeMillis() - taskStartMillis
            );
            return true;
        } catch (RuntimeException ex) {
            // 可能QPS限制导致的异常，记录失败状态，稍后重试
            markFailed(state, stock, sliceStart, targetExclusive, ex);
            writeSyncLog(stock.getSymbol(), sliceStart, sliceEnd, fetchedCount, writtenCount, false,
                    ex.getClass().getSimpleName(), ex.getMessage(), taskStartMillis, fetchLatencyMs, writeLatencyMs,
                    validateLatencyMs);
            log.error("历史分钟线同步：symbol={} 片段={} 至 {} 同步失败，原因={}",
                    stock.getSymbol(), sliceStart, sliceEnd, ex.getMessage(), ex);
            return true;
        }
    }

    private boolean isSliceComplete(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEnd,
            List<Instant> expectedMinutes) {
        if (expectedMinutes.isEmpty()) {
            return true;
        }
        List<KlineBar> existing = klineRepository.query(
                symbol,
                KlinePeriod.MINUTE_1,
                sliceStart.atZone(MARKET_ZONE).toInstant(),
                sliceEnd.atZone(MARKET_ZONE).toInstant()
        );
        Set<Instant> existingTimes = new HashSet<>();
        for (KlineBar bar : existing) {
            existingTimes.add(bar.time());
        }
        boolean complete = existingTimes.containsAll(expectedMinutes);
        log.info("历史分钟线同步：symbol={} InfluxDB 已有分钟数={}，完整={}", symbol, existingTimes.size(), complete);
        return complete;
    }

    private void assertSliceComplete(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEnd,
            List<Instant> expectedMinutes) {
        if (expectedMinutes.isEmpty()) {
            return;
        }
        List<KlineBar> persisted = klineRepository.query(
                symbol,
                KlinePeriod.MINUTE_1,
                sliceStart.atZone(MARKET_ZONE).toInstant(),
                sliceEnd.atZone(MARKET_ZONE).toInstant()
        );
        Set<Instant> persistedTimes = new HashSet<>();
        for (KlineBar bar : persisted) {
            persistedTimes.add(bar.time());
        }
        if (persistedTimes.containsAll(expectedMinutes)) {
            log.info("历史分钟线同步：symbol={} 完整性校验通过，expected={} actual={}",
                    symbol, expectedMinutes.size(), persistedTimes.size());
            return;
        }
        List<Instant> missing = expectedMinutes.stream()
                .filter(expected -> !persistedTimes.contains(expected))
                .limit(5)
                .toList();
        throw new KlineIntegrityException(symbol, expectedMinutes.size(), persistedTimes.size(), missing);
    }

    private LocalDateTime nextSliceStart(StockSyncStateEntity state, StockInfoEntity stock) {
        LocalDateTime cursor = state.getLatestSyncTime() != null
                ? state.getLatestSyncTime()
                : state.getStartTime() != null ? state.getStartTime() : defaultStartDate.atStartOfDay();
        LocalDate listDate = stock == null ? null : stock.getListDate();
        if (listDate != null && cursor.toLocalDate().isBefore(listDate)) {
            return listDate.atStartOfDay();
        }
        return cursor;
    }

    private void advanceStateOnly(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime sliceStart,
            LocalDateTime sliceEnd,
            LocalDateTime targetExclusive) {
        if (state.getId() == null) {
            state.setSymbol(stock.getSymbol());
            state.setDataType(SyncDataType.MINUTE_1M.getCode());
            state.setStartTime(sliceStart);
            state.setRetryCount(0);
            state.setDataSource("influxdb");
        }
        state.setLatestSyncTime(sliceEnd);
        state.setTargetSyncTime(targetExclusive);
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        state.setLastError(null);
        if (state.getId() == null) {
            stockSyncStateRepository.create(state);
        } else {
            stockSyncStateRepository.update(state);
        }
    }

    private void updateTargetTime(String symbol, LocalDateTime targetExclusive) {
        stockSyncStateRepository.findBySymbolAndDataType(symbol, SyncDataType.MINUTE_1M.getCode())
                .ifPresent(state -> {
                    state.setTargetSyncTime(targetExclusive);
                    stockSyncStateRepository.update(state);
                });
    }

    private void markFailed(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime sliceStart,
            LocalDateTime targetExclusive,
            RuntimeException ex) {
        if (state.getId() == null) {
            state.setSymbol(stock.getSymbol());
            state.setDataType(SyncDataType.MINUTE_1M.getCode());
            state.setStartTime(sliceStart);
            state.setRetryCount(0);
            state.setDataSource("tushare");
        }
        state.setTargetSyncTime(targetExclusive);
        state.setLastFailedTime(LocalDateTime.now(MARKET_ZONE));
        state.setRetryCount(state.getRetryCount() == null ? 1 : state.getRetryCount() + 1);
        state.setSyncStatus(SyncStatus.FAILED.getCode());
        state.setLastError(ex.getMessage());
        if (state.getId() == null) {
            stockSyncStateRepository.create(state);
        } else {
            stockSyncStateRepository.update(state);
        }
    }

    private void writeSyncLog(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEnd,
            int fetchedCount,
            int writtenCount,
            boolean success,
            String errorType,
            String errorMessage,
            long taskStartMillis,
            Long fetchLatencyMs,
            Long writeLatencyMs,
            Long validateLatencyMs) {
        SyncLogEntity entity = new SyncLogEntity();
        entity.setLogId("hist-" + UUID.randomUUID());
        entity.setTaskId(symbol + "-" + sliceStart.toLocalDate());
        entity.setSymbol(symbol);
        entity.setDataType(SyncDataType.MINUTE_1M.getCode());
        entity.setDataSource("tushare");
        entity.setStartTime(sliceStart);
        entity.setEndTime(sliceEnd);
        entity.setFetchLatencyMs(fetchLatencyMs);
        entity.setCleanLatencyMs(validateLatencyMs);
        entity.setWriteLatencyMs(writeLatencyMs);
        entity.setFetchedCount(fetchedCount);
        entity.setCleanedCount(writtenCount);
        entity.setWrittenCount(writtenCount);
        entity.setSuccess(success);
        entity.setErrorType(errorType);
        entity.setErrorMessage(truncate(errorMessage, 5000));
        entity.setTotalLatencyMs(System.currentTimeMillis() - taskStartMillis);
        syncLogRepository.create(entity);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private StockSyncStateEntity newState(StockInfoEntity stock, LocalDateTime targetExclusive) {
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol(stock.getSymbol());
        state.setDataType(SyncDataType.MINUTE_1M.getCode());
        state.setStartTime(resolveInitialStartTime(stock));
        state.setTargetSyncTime(targetExclusive);
        state.setRetryCount(0);
        state.setSyncStatus(SyncStatus.PENDING.getCode());
        state.setDataSource("tushare");
        return state;
    }

    private LocalDateTime resolveInitialStartTime(StockInfoEntity stock) {
        LocalDate startDate = defaultStartDate;
        if (stock != null && stock.getListDate() != null && stock.getListDate().isAfter(startDate)) {
            startDate = stock.getListDate();
        }
        return startDate.atStartOfDay();
    }

    private String exchangeForCompleteness(StockInfoEntity stock) {
        if (stock.getExchange() != null && !stock.getExchange().isBlank()) {
            return stock.getExchange();
        }
        String symbol = stock.getSymbol();
        if (symbol != null && symbol.endsWith(".SZ")) {
            return "SZSE";
        }
        return "SSE";
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class KlineIntegrityException extends RuntimeException {

        private KlineIntegrityException(String symbol, int expectedCount, int actualCount, List<Instant> missingSamples) {
            super("Kline integrity check failed, symbol=" + symbol
                    + ", expectedCount=" + expectedCount
                    + ", actualCount=" + actualCount
                    + ", missingSamples=" + missingSamples);
        }
    }
}
