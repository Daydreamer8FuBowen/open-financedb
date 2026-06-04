package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.enums.MissingRecordStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockKlineMissingRecordRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.HistoryKlineSyncWorker;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistoryKlineSyncWorkerImpl implements HistoryKlineSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(HistoryKlineSyncWorkerImpl.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final StockKlineMissingRecordRepository stockKlineMissingRecordRepository;
    private final SyncLogRepository syncLogRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final KlineRepository klineRepository;
    private final TushareKlineDataSource tushareKlineDataSource;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final Clock clock;
    private final LocalDate defaultStartDate;
    private final Duration idleSleep;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long scanAfterId = 0L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "history-kline-sync-worker");
        thread.setDaemon(false);
        return thread;
    });

    @Autowired
    public HistoryKlineSyncWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            StockKlineMissingRecordRepository stockKlineMissingRecordRepository,
            SyncLogRepository syncLogRepository,
            TradeCalendarRepository tradeCalendarRepository,
            KlineRepository klineRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate defaultStartDate,
            @Value("${finance.history-sync.idle-sleep:30s}") Duration idleSleep) {
        this(
                stockInfoRepository,
                stockSyncStateRepository,
                stockKlineMissingRecordRepository,
                syncLogRepository,
                tradeCalendarRepository,
                klineRepository,
                tushareKlineDataSource,
                tradeMinuteWindowService,
                defaultStartDate,
                idleSleep,
                Clock.systemUTC()
        );
    }

    public HistoryKlineSyncWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            SyncLogRepository syncLogRepository,
            TradeCalendarRepository tradeCalendarRepository,
            KlineRepository klineRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            LocalDate defaultStartDate,
            Duration idleSleep) {
        this(
                stockInfoRepository,
                stockSyncStateRepository,
                syncLogRepository,
                tradeCalendarRepository,
                klineRepository,
                tushareKlineDataSource,
                tradeMinuteWindowService,
                defaultStartDate,
                idleSleep,
                Clock.systemUTC()
        );
    }

    public HistoryKlineSyncWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            SyncLogRepository syncLogRepository,
            TradeCalendarRepository tradeCalendarRepository,
            KlineRepository klineRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            LocalDate defaultStartDate,
            Duration idleSleep,
            Clock clock) {
        this(
                stockInfoRepository,
                stockSyncStateRepository,
                new NoopStockKlineMissingRecordRepository(),
                syncLogRepository,
                tradeCalendarRepository,
                klineRepository,
                tushareKlineDataSource,
                tradeMinuteWindowService,
                defaultStartDate,
                idleSleep,
                clock
        );
    }

    public HistoryKlineSyncWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            StockKlineMissingRecordRepository stockKlineMissingRecordRepository,
            SyncLogRepository syncLogRepository,
            TradeCalendarRepository tradeCalendarRepository,
            KlineRepository klineRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            LocalDate defaultStartDate,
            Duration idleSleep,
            Clock clock) {
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.stockKlineMissingRecordRepository = stockKlineMissingRecordRepository;
        this.syncLogRepository = syncLogRepository;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.klineRepository = klineRepository;
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.clock = clock;
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
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        Map<String, LocalDateTime> targetTimeByExchange = new HashMap<>();
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
            LocalDateTime targetSyncTime = targetTimeByExchange.computeIfAbsent(
                    exchange,
                    key -> resolveTargetSyncTime(key, today)
            );
            if (syncNextMonthlySlice(stock, targetSyncTime)) {
                progressed = true;
            }
        }
    }

    private LocalDateTime resolveTargetSyncTime(String exchange, LocalDate today) {
        LocalDate endDate = today.minusDays(1);
        LocalDate startDate = endDate.minusDays(60);
        List<TradeCalendarEntity> openDays = tradeCalendarRepository.findOpenDays(exchange, startDate, endDate);
        if (openDays.isEmpty()) {
            return null;
        }
        LocalDate lastOpenDay = openDays.get(openDays.size() - 1).getTradeDate();
        List<Instant> expectedMinutes = tradeMinuteWindowService.expectedMinuteInstants(exchange, lastOpenDay, lastOpenDay);
        if (expectedMinutes.isEmpty()) {
            return null;
        }
        return toLocalDateTime(expectedMinutes.getLast());
    }

    private boolean syncNextMonthlySlice(StockInfoEntity stock, LocalDateTime targetSyncTime) {
        if (targetSyncTime == null) {
            return false;
        }
        StockSyncStateEntity state = stockSyncStateRepository
                .findBySymbolAndDataType(stock.getSymbol(), SyncDataType.KLINE_1M.getCode())
                .orElseGet(() -> newState(stock));
        if (false && SyncStatus.INCOMPLETE.getCode().equals(state.getSyncStatus())) {
            log.warn("历史分钟线同步：symbol={} 已标记为数据不完整，跳过后续同步", stock.getSymbol());
            return false;
        }

        LocalDateTime sliceStart = nextSliceStart(state, stock);
        if (sliceStart.isAfter(targetSyncTime)) {
            markCompleteIfCovered(state, targetSyncTime);
            return repairTodayPrefixGapIfNeeded(stock, state);
        }
        LocalDateTime sliceEndExclusive = sliceStart.plusMonths(1);
        LocalDateTime targetExclusive = targetSyncTime.plusMinutes(1);
        if (sliceEndExclusive.isAfter(targetExclusive)) {
            sliceEndExclusive = targetExclusive;
        }

        String exchange = exchangeForCompleteness(stock);
        long taskStartMillis = System.currentTimeMillis();
        long preCheckStartMillis = taskStartMillis;
        List<Instant> expectedMinutes = expectedMinutesForRange(exchange, sliceStart, sliceEndExclusive);
        long expectedLatencyMs = System.currentTimeMillis() - preCheckStartMillis;
        if (expectedMinutes.isEmpty()) {
            return false;
        }
        LocalDateTime sliceLatestExpectedTime = toLocalDateTime(expectedMinutes.getLast());
        boolean hasEarlierLocalData = hasEarlierLocalData(stock.getSymbol(), sliceStart);
        log.info("历史分钟线同步：symbol={} 片段={} 至 {}，交易所={}，预期分钟数={}，目标时间={}",
                stock.getSymbol(), sliceStart, sliceEndExclusive, exchange, expectedMinutes.size(), targetSyncTime);

        int fetchedCount = 0;
        int writtenCount = 0;
        Long fetchLatencyMs = null;
        Long writeLatencyMs = null;
        Long validateLatencyMs = expectedLatencyMs;
        try {
            long existingCheckStartMillis = System.currentTimeMillis();
            if (isSliceComplete(stock.getSymbol(), sliceStart, sliceEndExclusive, expectedMinutes)) {
                long existingCheckLatencyMs = System.currentTimeMillis() - existingCheckStartMillis;
                validateLatencyMs = expectedLatencyMs + existingCheckLatencyMs;
                LocalDateTime startTimeCandidate = !hasEarlierLocalData
                        ? toLocalDateTime(expectedMinutes.getFirst())
                        : state.getStartTime();
                advanceStateOnSuccessfulSlice(
                        state,
                        stock,
                        startTimeCandidate,
                        sliceLatestExpectedTime,
                        targetSyncTime,
                        !hasEarlierLocalData
                );
                writeSyncLog(
                        stock.getSymbol(),
                        sliceStart,
                        sliceEndExclusive,
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
                return true;
            }

            long fetchStartMillis = System.currentTimeMillis();
            List<KlineBar> fetchedBars = filterFetchedBars(
                    stock.getSymbol(),
                    sliceStart,
                    sliceEndExclusive,
                    tushareKlineDataSource.fetchMinuteBars(stock.getSymbol(), sliceStart, sliceEndExclusive)
            );
            fetchLatencyMs = System.currentTimeMillis() - fetchStartMillis;
            fetchedCount = fetchedBars.size();
            log.info("历史分钟线同步：symbol={} 从 Tushare 获取 {} 条分钟线，开始写入 InfluxDB", stock.getSymbol(), fetchedBars.size());

            if (fetchedBars.isEmpty()) {
                MissingDayFiltering filtered = filterMissingDays(stock.getSymbol(), expectedMinutes, fetchedBars);
                recordMissingDates(stock.getSymbol(), filtered.missingDates());
                advanceStateOnSuccessfulSlice(
                        state,
                        stock,
                        sliceEndExclusive,
                        sliceLatestExpectedTime,
                        targetSyncTime,
                        true
                );
                writeSyncLog(
                        stock.getSymbol(),
                        sliceStart,
                        sliceEndExclusive,
                        fetchedCount,
                        0,
                        true,
                        null,
                        "tushare empty historical slice, skipped as external missing data",
                        taskStartMillis,
                        fetchLatencyMs,
                        0L,
                        validateLatencyMs
                );
                return true;
            }

            PrefixFallback prefixFallback = resolvePrefixFallback(
                    stock.getSymbol(),
                    sliceStart,
                    sliceEndExclusive,
                    expectedMinutes,
                    fetchedBars
            );
            MissingDayFiltering filtered = filterMissingDays(stock.getSymbol(), expectedMinutes, fetchedBars);
            recordMissingDates(stock.getSymbol(), filtered.missingDates());
            fetchedBars = filtered.bars();

            long writeStartMillis = System.currentTimeMillis();
            if (!fetchedBars.isEmpty()) {
                klineRepository.upsert(fetchedBars);
            }
            writeLatencyMs = System.currentTimeMillis() - writeStartMillis;
            writtenCount = fetchedBars.size();

            long verifyStartMillis = System.currentTimeMillis();
            if (false && prefixFallback.hasMissingPrefix() && !hasEarlierLocalData) {
                log.warn("历史分钟线同步：symbol={} 片段={} 至 {} Tushare 前置缺失 {} 个预期分钟，数据起点移动到 {}",
                        stock.getSymbol(), sliceStart, sliceEndExclusive, prefixFallback.skippedCount(), prefixFallback.dataStart());
                assertSliceComplete(stock.getSymbol(), sliceStart, sliceEndExclusive, prefixFallback.expectedForValidation());
                LocalDateTime actualDataStart = prefixFallback.dataStart() != null
                        ? prefixFallback.dataStart()
                        : firstBarTime(fetchedBars);
                advanceStateOnSuccessfulSlice(
                        state,
                        stock,
                        actualDataStart,
                        sliceLatestExpectedTime,
                        targetSyncTime,
                        true
                );
            } else {
                assertSliceComplete(stock.getSymbol(), sliceStart, sliceEndExclusive, filtered.expectedMinutes());
                LocalDateTime startTimeCandidate = !hasEarlierLocalData
                        ? firstCompleteStart(filtered.expectedMinutes(), sliceEndExclusive)
                        : state.getStartTime();
                advanceStateOnSuccessfulSlice(
                        state,
                        stock,
                        startTimeCandidate,
                        sliceLatestExpectedTime,
                        targetSyncTime,
                        !hasEarlierLocalData
                );
            }
            long verifyLatencyMs = System.currentTimeMillis() - verifyStartMillis;
            validateLatencyMs = expectedLatencyMs + verifyLatencyMs;

            writeSyncLog(
                    stock.getSymbol(),
                    sliceStart,
                    sliceEndExclusive,
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
                    "历史分钟线同步：symbol={} 片段写入完成，状态推进至 latest={} target={}，fetched={} expected={}",
                    stock.getSymbol(),
                    sliceLatestExpectedTime,
                    targetSyncTime,
                    fetchedCount,
                    expectedMinutes.size()
            );
            return true;
        } catch (RuntimeException ex) {
            if (ex instanceof KlineIntegrityException) {
                markIncomplete(state, stock, sliceStart, ex);
            } else {
                markFailed(state, stock, sliceStart, ex);
            }
            writeSyncLog(
                    stock.getSymbol(),
                    sliceStart,
                    sliceEndExclusive,
                    fetchedCount,
                    writtenCount,
                    false,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    taskStartMillis,
                    fetchLatencyMs,
                    writeLatencyMs,
                    validateLatencyMs
            );
            log.error("历史分钟线同步：symbol={} 片段={} 至 {} 同步失败，原因={}",
                    stock.getSymbol(), sliceStart, sliceEndExclusive, ex.getMessage(), ex);
            return true;
        }
    }

    private boolean repairTodayPrefixGapIfNeeded(StockInfoEntity stock, StockSyncStateEntity state) {
        if (!SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus())) {
            return false;
        }
        String exchange = exchangeForCompleteness(stock);
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        if (tradeCalendarRepository.findByExchangeAndTradeDate(exchange, today)
                .filter(TradeCalendarEntity::getIsOpen)
                .isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        List<Instant> expectedToday = tradeMinuteWindowService.expectedMinuteInstants(exchange, today, today).stream()
                .filter(time -> !time.isAfter(now))
                .sorted()
                .toList();
        if (expectedToday.isEmpty()) {
            return false;
        }
        Instant dayStart = today.atStartOfDay(MARKET_ZONE).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant();
        List<KlineBar> localBars = klineRepository.query(stock.getSymbol(), KlinePeriod.MINUTE_1, dayStart, dayEnd);
        Set<Instant> localTimes = localBars.stream()
                .map(KlineBar::time)
                .collect(java.util.stream.Collectors.toSet());
        Instant validationEnd = localTimes.stream()
                .filter(time -> !time.isAfter(now))
                .max(java.util.Comparator.naturalOrder())
                .orElse(expectedToday.getLast());
        List<Instant> expectedPrefix = expectedToday.stream()
                .filter(time -> !time.isAfter(validationEnd))
                .toList();
        List<Instant> missingPrefix = expectedPrefix.stream()
                .filter(expected -> !localTimes.contains(expected))
                .toList();
        if (missingPrefix.isEmpty()) {
            return false;
        }
        Set<Instant> missingSet = new HashSet<>(missingPrefix);
        List<KlineBar> repairBars = tushareKlineDataSource.fetchRealtimeDailyMinuteBars(stock.getSymbol(), KlinePeriod.MINUTE_1).stream()
                .filter(KlineBar::complete)
                .filter(bar -> stock.getSymbol().equals(bar.symbol()))
                .filter(bar -> bar.period() == KlinePeriod.MINUTE_1)
                .filter(bar -> missingSet.contains(bar.time()))
                .sorted(java.util.Comparator.comparing(KlineBar::time))
                .toList();
        if (repairBars.isEmpty()) {
            return false;
        }
        klineRepository.upsert(repairBars);
        log.info("历史分钟线同步：symbol={} 使用 rt_min_daily 补齐今日前缀缺口，missing={} written={}",
                stock.getSymbol(), missingPrefix.size(), repairBars.size());
        return true;
    }

    private List<Instant> expectedMinutesForRange(String exchange, LocalDateTime sliceStart, LocalDateTime sliceEndExclusive) {
        Instant startInstant = sliceStart.atZone(MARKET_ZONE).toInstant();
        Instant endInstant = sliceEndExclusive.atZone(MARKET_ZONE).toInstant();
        return tradeMinuteWindowService.expectedMinuteInstants(
                exchange,
                sliceStart.toLocalDate(),
                sliceEndExclusive.minusNanos(1).toLocalDate()
        ).stream()
                .filter(time -> !time.isBefore(startInstant) && time.isBefore(endInstant))
                .toList();
    }

    private boolean isSliceComplete(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEndExclusive,
            List<Instant> expectedMinutes) {
        if (expectedMinutes.isEmpty()) {
            return true;
        }
        List<KlineBar> existing = klineRepository.query(
                symbol,
                KlinePeriod.MINUTE_1,
                sliceStart.atZone(MARKET_ZONE).toInstant(),
                sliceEndExclusive.atZone(MARKET_ZONE).toInstant()
        );
        Set<Instant> existingTimes = new HashSet<>();
        for (KlineBar bar : existing) {
            existingTimes.add(bar.time());
        }
        boolean complete = existingTimes.containsAll(expectedMinutes);
        log.info("历史分钟线同步：symbol={} InfluxDB 已有分钟数={}，完整={}", symbol, existingTimes.size(), complete);
        return complete;
    }

    private PrefixFallback resolvePrefixFallback(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEndExclusive,
            List<Instant> expectedMinutes,
            List<KlineBar> bars) {
        if (expectedMinutes.isEmpty()) {
            return new PrefixFallback(false, false, null, 0, expectedMinutes);
        }
        if (bars == null || bars.isEmpty()) {
            return new PrefixFallback(true, false, null, expectedMinutes.size(), List.of());
        }
        Instant sliceStartInstant = sliceStart.atZone(MARKET_ZONE).toInstant();
        Instant sliceEndInstant = sliceEndExclusive.atZone(MARKET_ZONE).toInstant();
        Set<Instant> fetchedTimes = new HashSet<>();
        for (KlineBar bar : bars) {
            if (symbol.equals(bar.symbol())
                    && bar.period() == KlinePeriod.MINUTE_1
                    && !bar.time().isBefore(sliceStartInstant)
                    && bar.time().isBefore(sliceEndInstant)) {
                fetchedTimes.add(bar.time());
            }
        }
        int firstPresentIndex = -1;
        for (int index = 0; index < expectedMinutes.size(); index++) {
            if (fetchedTimes.contains(expectedMinutes.get(index))) {
                firstPresentIndex = index;
                break;
            }
        }
        if (firstPresentIndex <= 0) {
            return new PrefixFallback(false, false, null, 0, expectedMinutes);
        }
        List<Instant> suffix = expectedMinutes.subList(firstPresentIndex, expectedMinutes.size());
        if (!fetchedTimes.containsAll(suffix)) {
            return new PrefixFallback(false, false, null, 0, expectedMinutes);
        }
        return new PrefixFallback(
                false,
                true,
                toLocalDateTime(expectedMinutes.get(firstPresentIndex)),
                firstPresentIndex,
                List.copyOf(suffix)
        );
    }

    private void assertSliceComplete(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEndExclusive,
            List<Instant> expectedMinutes) {
        if (expectedMinutes.isEmpty()) {
            return;
        }
        List<KlineBar> persisted = klineRepository.query(
                symbol,
                KlinePeriod.MINUTE_1,
                sliceStart.atZone(MARKET_ZONE).toInstant(),
                sliceEndExclusive.atZone(MARKET_ZONE).toInstant()
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

    private List<KlineBar> filterFetchedBars(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEndExclusive,
            List<KlineBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        Instant startInstant = sliceStart.atZone(MARKET_ZONE).toInstant();
        Instant endInstant = sliceEndExclusive.atZone(MARKET_ZONE).toInstant();
        return bars.stream()
                .filter(bar -> symbol.equals(bar.symbol()))
                .filter(bar -> bar.period() == KlinePeriod.MINUTE_1)
                .filter(bar -> !bar.time().isBefore(startInstant) && bar.time().isBefore(endInstant))
                .sorted(java.util.Comparator.comparing(KlineBar::time))
                .toList();
    }

    private MissingDayFiltering filterMissingDays(String symbol, List<Instant> expectedMinutes, List<KlineBar> bars) {
        if (expectedMinutes.isEmpty()) {
            return new MissingDayFiltering(List.of(), List.of(), filterCompleteBars(symbol, bars, Set.of()));
        }
        Map<LocalDate, List<Instant>> expectedByDate = new HashMap<>();
        for (Instant expected : expectedMinutes) {
            LocalDate date = toLocalDateTime(expected).toLocalDate();
            expectedByDate.computeIfAbsent(date, ignored -> new java.util.ArrayList<>()).add(expected);
        }
        Set<Instant> fetchedCompleteTimes = new HashSet<>();
        if (bars != null) {
            for (KlineBar bar : bars) {
                if (symbol.equals(bar.symbol()) && bar.period() == KlinePeriod.MINUTE_1 && bar.complete()) {
                    fetchedCompleteTimes.add(bar.time());
                }
            }
        }
        Set<LocalDate> missingDates = new HashSet<>();
        for (Map.Entry<LocalDate, List<Instant>> entry : expectedByDate.entrySet()) {
            if (!fetchedCompleteTimes.containsAll(entry.getValue())) {
                missingDates.add(entry.getKey());
            }
        }
        Set<LocalDate> completeDates = new HashSet<>(expectedByDate.keySet());
        completeDates.removeAll(missingDates);
        List<Instant> completeExpected = expectedMinutes.stream()
                .filter(expected -> completeDates.contains(toLocalDateTime(expected).toLocalDate()))
                .toList();
        return new MissingDayFiltering(
                completeExpected,
                missingDates.stream().sorted().toList(),
                filterCompleteBars(symbol, bars, completeDates)
        );
    }

    private List<KlineBar> filterCompleteBars(String symbol, List<KlineBar> bars, Set<LocalDate> completeDates) {
        if (bars == null || bars.isEmpty() || completeDates.isEmpty()) {
            return List.of();
        }
        return bars.stream()
                .filter(KlineBar::complete)
                .filter(bar -> symbol.equals(bar.symbol()))
                .filter(bar -> bar.period() == KlinePeriod.MINUTE_1)
                .filter(bar -> completeDates.contains(toLocalDateTime(bar.time()).toLocalDate()))
                .sorted(java.util.Comparator.comparing(KlineBar::time))
                .toList();
    }

    private void recordMissingDates(String symbol, List<LocalDate> missingDates) {
        for (LocalDate missingDate : missingDates) {
            StockKlineMissingRecordEntity entity = new StockKlineMissingRecordEntity();
            entity.setSymbol(symbol);
            entity.setDataType(SyncDataType.KLINE_1M.getCode());
            entity.setDataSource("tushare");
            entity.setMissingDate(missingDate);
            entity.setStatus(MissingRecordStatus.OPEN.getCode());
            entity.setDetectedAt(LocalDateTime.now(MARKET_ZONE));
            entity.setRemark("historical minute kline missing from tushare");
            stockKlineMissingRecordRepository.upsertMissingDate(entity);
        }
    }

    private LocalDateTime firstCompleteStart(List<Instant> expectedMinutes, LocalDateTime fallback) {
        if (expectedMinutes.isEmpty()) {
            return fallback;
        }
        return toLocalDateTime(expectedMinutes.getFirst());
    }

    private boolean hasEarlierLocalData(String symbol, LocalDateTime sliceStart) {
        Instant sliceStartInstant = sliceStart.atZone(MARKET_ZONE).toInstant();
        return klineRepository.findEarliestTime(symbol, KlinePeriod.MINUTE_1)
                .filter(earliest -> earliest.isBefore(sliceStartInstant))
                .isPresent();
    }

    private LocalDateTime nextSliceStart(StockSyncStateEntity state, StockInfoEntity stock) {
        LocalDateTime cursor = state.getCursorTime();
        if (cursor == null && state.getLatestSyncTime() != null) {
            cursor = state.getLatestSyncTime().plusMinutes(1);
        }
        if (cursor == null) {
            cursor = state.getStartTime();
        }
        if (cursor == null) {
            cursor = resolveInitialStartTime(stock);
        }
        LocalDate listDate = stock == null ? null : stock.getListDate();
        if (listDate != null && cursor.toLocalDate().isBefore(listDate)) {
            return listDate.atStartOfDay();
        }
        return cursor;
    }

    private void advanceStateOnSuccessfulSlice(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime startTimeCandidate,
            LocalDateTime latestSyncTime,
            LocalDateTime targetSyncTime,
            boolean updateStartTime) {
        initializeState(state, stock);
        if (updateStartTime && startTimeCandidate != null) {
            state.setStartTime(startTimeCandidate);
        } else if (state.getStartTime() == null && startTimeCandidate != null) {
            state.setStartTime(startTimeCandidate);
        }
        state.setLatestSyncTime(latestSyncTime);
        state.setCursorTime(latestSyncTime.plusMinutes(1));
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setSyncStatus(latestSyncTime.isBefore(targetSyncTime)
                ? SyncStatus.PENDING.getCode()
                : SyncStatus.SUCCESS.getCode());
        state.setLastError(null);
        persistState(state);
    }

    private void markCompleteIfCovered(StockSyncStateEntity state, LocalDateTime targetSyncTime) {
        LocalDateTime cursorTime = state.getCursorTime();
        if (cursorTime == null || !cursorTime.isAfter(targetSyncTime)) {
            return;
        }
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setLastError(null);
        persistState(state);
    }

    private void advanceStateForInitialGap(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime nextProbeStart,
            LocalDateTime targetSyncTime) {
        initializeState(state, stock);
        state.setStartTime(nextProbeStart);
        state.setCursorTime(nextProbeStart);
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setSyncStatus(nextProbeStart.isAfter(targetSyncTime)
                ? SyncStatus.SUCCESS.getCode()
                : SyncStatus.PENDING.getCode());
        state.setLastError(null);
        persistState(state);
    }

    private void markFailed(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime sliceStart,
            RuntimeException ex) {
        initializeState(state, stock);
        if (state.getStartTime() == null) {
            state.setStartTime(sliceStart);
        }
        if (state.getCursorTime() == null) {
            state.setCursorTime(sliceStart);
        }
        state.setLastFailedTime(LocalDateTime.now(MARKET_ZONE));
        state.setRetryCount(state.getRetryCount() == null ? 1 : state.getRetryCount() + 1);
        state.setSyncStatus(SyncStatus.FAILED.getCode());
        state.setLastError(ex.getMessage());
        persistState(state);
    }

    private void markIncomplete(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime sliceStart,
            RuntimeException ex) {
        initializeState(state, stock);
        if (state.getStartTime() == null) {
            state.setStartTime(sliceStart);
        }
        if (state.getCursorTime() == null) {
            state.setCursorTime(sliceStart);
        }
        state.setLastFailedTime(LocalDateTime.now(MARKET_ZONE));
        state.setRetryCount(state.getRetryCount() == null ? 1 : state.getRetryCount() + 1);
        state.setSyncStatus(SyncStatus.INCOMPLETE.getCode());
        state.setLastError(ex.getMessage());
        persistState(state);
    }

    private void initializeState(StockSyncStateEntity state, StockInfoEntity stock) {
        if (state.getId() == null) {
            state.setSymbol(stock.getSymbol());
            state.setDataType(SyncDataType.KLINE_1M.getCode());
            state.setRetryCount(0);
            state.setDataSource("tushare");
            if (state.getStartTime() == null) {
                state.setStartTime(resolveInitialStartTime(stock));
            }
            if (state.getCursorTime() == null) {
                state.setCursorTime(state.getStartTime());
            }
        }
    }

    private void persistState(StockSyncStateEntity state) {
        if (state.getId() == null) {
            stockSyncStateRepository.create(state);
        } else {
            stockSyncStateRepository.update(state);
        }
    }

    private void writeSyncLog(
            String symbol,
            LocalDateTime sliceStart,
            LocalDateTime sliceEndExclusive,
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
        entity.setDataType(SyncDataType.KLINE_1M.getCode());
        entity.setDataSource("tushare");
        entity.setStartTime(sliceStart);
        entity.setEndTime(sliceEndExclusive);
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

    private StockSyncStateEntity newState(StockInfoEntity stock) {
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol(stock.getSymbol());
        state.setDataType(SyncDataType.KLINE_1M.getCode());
        state.setStartTime(resolveInitialStartTime(stock));
        state.setCursorTime(state.getStartTime());
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

    private LocalDateTime firstBarTime(List<KlineBar> bars) {
        return bars.stream()
                .map(KlineBar::time)
                .min(java.util.Comparator.naturalOrder())
                .map(this::toLocalDateTime)
                .orElse(null);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, MARKET_ZONE);
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

    private record PrefixFallback(
            boolean emptySlice,
            boolean hasMissingPrefix,
            LocalDateTime dataStart,
            int skippedCount,
            List<Instant> expectedForValidation) {
    }

    private record MissingDayFiltering(
            List<Instant> expectedMinutes,
            List<LocalDate> missingDates,
            List<KlineBar> bars) {
    }

    private static final class NoopStockKlineMissingRecordRepository implements StockKlineMissingRecordRepository {

        @Override
        public Long create(StockKlineMissingRecordEntity entity) {
            return null;
        }

        @Override
        public boolean update(StockKlineMissingRecordEntity entity) {
            return true;
        }

        @Override
        public boolean upsertMissingDate(StockKlineMissingRecordEntity entity) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public Optional<StockKlineMissingRecordEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<StockKlineMissingRecordEntity> findBySymbolAndDataTypeAndDataSourceAndMissingDate(
                String symbol,
                String dataType,
                String dataSource,
                LocalDate missingDate) {
            return Optional.empty();
        }

        @Override
        public com.fbw.finance.openfinancedb.framework.web.PageResult<StockKlineMissingRecordEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO reqVO) {
            return new com.fbw.finance.openfinancedb.framework.web.PageResult<>(List.of(), 0L);
        }

        @Override
        public List<LocalDate> findOpenMissingDates(String symbol, String dataType, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }
    }
}
