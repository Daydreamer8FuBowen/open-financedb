package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareRateLimitExceededException;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.KlineAggregationWorker;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KlineAggregationWorkerImpl implements KlineAggregationWorker {

    private static final Logger log = LoggerFactory.getLogger(KlineAggregationWorkerImpl.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<KlinePeriod> DEFAULT_TARGET_PERIODS = List.of(
            KlinePeriod.MINUTE_5,
            KlinePeriod.MINUTE_15,
            KlinePeriod.MINUTE_30,
            KlinePeriod.HOUR_1,
            KlinePeriod.DAY_1
    );

    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final KlineRepository klineRepository;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final LocalDate defaultStartDate;
    private final Duration idleSleep;
    private final List<KlinePeriod> targetPeriods;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(namedThreadFactory("kline-aggregation-coordinator-"));
    private final ExecutorService aggregationExecutor = Executors.newFixedThreadPool(5, namedThreadFactory("kline-aggregation-worker-"));
    private long scanAfterId = 0L;

    @Autowired
    public KlineAggregationWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            KlineRepository klineRepository,
            TradeMinuteWindowService tradeMinuteWindowService,
            TradeCalendarRepository tradeCalendarRepository,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate defaultStartDate,
            @Value("${finance.kline-aggregation.idle-sleep:30s}") Duration idleSleep) {
        this(
                stockInfoRepository,
                stockSyncStateRepository,
                klineRepository,
                tradeMinuteWindowService,
                tradeCalendarRepository,
                defaultStartDate,
                idleSleep,
                DEFAULT_TARGET_PERIODS,
                Clock.systemUTC()
        );
    }

    public KlineAggregationWorkerImpl(
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            KlineRepository klineRepository,
            TradeMinuteWindowService tradeMinuteWindowService,
            TradeCalendarRepository tradeCalendarRepository,
            LocalDate defaultStartDate,
            Duration idleSleep,
            List<KlinePeriod> targetPeriods,
            Clock clock) {
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.klineRepository = klineRepository;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.defaultStartDate = defaultStartDate;
        this.idleSleep = idleSleep;
        this.targetPeriods = List.copyOf(targetPeriods);
        this.clock = clock;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.info("K-line aggregation worker already started; duplicate start ignored");
            return;
        }
        coordinator.submit(this::loop);
        log.info("K-line aggregation worker started, targetPeriods={}, idleSleep={}", targetPeriods, idleSleep);
    }

    @Override
    @PreDestroy
    public void stop() {
        running.set(false);
        coordinator.shutdownNow();
        aggregationExecutor.shutdownNow();
        awaitTermination(coordinator, "K-line aggregation coordinator");
        awaitTermination(aggregationExecutor, "K-line aggregation worker pool");
    }

    private void loop() {
        while (running.get()) {
            try {
                boolean progressed = runOneRound();
                if (!progressed) {
                    sleepQuietly(idleSleep);
                }
            } catch (RuntimeException ex) {
                log.error("K-line aggregation loop failed; retry later, reason={}", ex.getMessage(), ex);
                sleepQuietly(idleSleep);
            }
        }
        log.info("K-line aggregation worker exited");
    }

    private boolean runOneRound() {
        boolean progressed = false;
        List<Future<Boolean>> futures = new ArrayList<>();
        while (true) {
            var next = stockInfoRepository.findNextRealtimeSyncEnabledAfterId(scanAfterId);
            if (next.isEmpty()) {
                scanAfterId = 0L;
                break;
            }
            StockInfoEntity stock = next.get();
            scanAfterId = stock.getId() == null ? scanAfterId : stock.getId();
            for (KlinePeriod targetPeriod : targetPeriods) {
                futures.add(aggregationExecutor.submit(() -> aggregateStockPeriod(stock, targetPeriod)));
            }
        }
        for (Future<Boolean> future : futures) {
            try {
                if (future.get()) {
                    progressed = true;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return progressed;
            } catch (TushareRateLimitExceededException ex) {
                log.info("Tushare rate limit exceeded, retry later, apiName={}", ex.getMessage());
                return progressed;
            } catch (Exception ex) {
                throw new IllegalStateException("K-line aggregation task failed", ex);
            }
        }
        return progressed;
    }

    private boolean aggregateStockPeriod(StockInfoEntity stock, KlinePeriod targetPeriod) {
        String symbol = stock.getSymbol();
        String dataType = dataType(targetPeriod).getCode();
        StockSyncStateEntity state = stockSyncStateRepository
                .findBySymbolAndDataType(symbol, dataType)
                .orElseGet(() -> newState(stock, dataType));
        Instant cursor = cursorInstant(state, stock);
        Instant sourceDataFloor = sourceDataFloor(symbol);
        if (sourceDataFloor != null && cursor.isBefore(sourceDataFloor)) {
            cursor = sourceDataFloor;
        }
        Instant targetMinute = resolveTargetSyncTime(exchangeForCompleteness(stock), LocalDate.now(clock.withZone(MARKET_ZONE)));
        if (targetMinute == null) {
            return false;
        }
        if (cursor.isAfter(targetMinute)) {
            markCompleteIfCovered(state, targetMinute);
            return aggregateTodayIfReady(stock, targetPeriod, state);
        }

        List<Instant> expectedMinutes = expectedMinutes(stock, cursor, targetMinute);
        if (expectedMinutes.isEmpty()) {
            return false;
        }

        List<KlineBar> minuteBars = klineRepository.query(
                symbol,
                KlinePeriod.MINUTE_1,
                cursor,
                targetMinute.plus(Duration.ofMinutes(1))
        );
        Map<Instant, KlineBar> minuteByTime = new HashMap<>();
        for (KlineBar bar : minuteBars) {
            minuteByTime.put(bar.time(), bar);
        }

        List<KlineBar> aggregatedBars = new ArrayList<>();
        Instant nextCursor = cursor;
        for (List<Instant> window : buildWindows(expectedMinutes, targetPeriod)) {
            if (window.isEmpty() || window.get(window.size() - 1).isAfter(targetMinute)) {
                break;
            }
            List<KlineBar> windowBars = new ArrayList<>(window.size());
            for (Instant expected : window) {
                KlineBar bar = minuteByTime.get(expected);
                if (bar == null) {
                    if (!aggregatedBars.isEmpty()) {
                        klineRepository.upsert(aggregatedBars);
                    }
                    persistState(state, stock, dataType, nextCursor, targetMinute, false, null);
                    return !aggregatedBars.isEmpty();
                }
                windowBars.add(bar);
            }
            aggregatedBars.add(aggregateWindow(windowBars, targetPeriod));
            nextCursor = window.get(window.size() - 1).plus(Duration.ofMinutes(1));
        }

        if (aggregatedBars.isEmpty()) {
            return false;
        }
        klineRepository.upsert(aggregatedBars);
        persistState(state, stock, dataType, nextCursor, targetMinute, !nextCursor.isBefore(targetMinute), null);
        log.info(
                "K-line aggregation advanced, symbol={}, targetPeriod={}, written={}, cursor={}",
                symbol,
                targetPeriod.getCode(),
                aggregatedBars.size(),
                nextCursor
        );
        return true;
    }

    private boolean aggregateTodayIfReady(StockInfoEntity stock, KlinePeriod targetPeriod, StockSyncStateEntity state) {
        if (!SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus())) {
            return false;
        }
        String symbol = stock.getSymbol();
        String exchange = exchangeForCompleteness(stock);
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        if (tradeCalendarRepository.findByExchangeAndTradeDate(exchange, today)
                .filter(TradeCalendarEntity::getIsOpen)
                .isEmpty()) {
            return false;
        }
        List<Instant> fullDayExpected = tradeMinuteWindowService.expectedMinuteInstants(exchange, today, today).stream()
                .sorted()
                .toList();
        if (fullDayExpected.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        List<Instant> eligibleExpected = fullDayExpected.stream()
                .filter(time -> !time.isAfter(now))
                .toList();
        if (targetPeriod == KlinePeriod.DAY_1 && fullDayExpected.getLast().isAfter(now)) {
            return false;
        }
        if (targetPeriod == KlinePeriod.DAY_1) {
            eligibleExpected = fullDayExpected;
        }
        List<List<Instant>> windows = buildWindows(eligibleExpected, targetPeriod);
        if (windows.isEmpty()) {
            return false;
        }
        Instant latestCompleteWindowStart = windows.getLast().getFirst();
        if (klineRepository.findLatestTime(symbol, targetPeriod)
                .filter(latest -> !latest.isBefore(latestCompleteWindowStart))
                .isPresent()) {
            return false;
        }

        Instant queryStart = today.atStartOfDay(MARKET_ZONE).toInstant();
        Instant queryEnd = windows.getLast().getLast().plus(Duration.ofMinutes(1));
        List<KlineBar> minuteBars = klineRepository.query(symbol, KlinePeriod.MINUTE_1, queryStart, queryEnd);
        Map<Instant, KlineBar> minuteByTime = new HashMap<>();
        for (KlineBar bar : minuteBars) {
            minuteByTime.put(bar.time(), bar);
        }

        List<KlineBar> aggregatedBars = new ArrayList<>();
        for (List<Instant> window : windows) {
            List<KlineBar> windowBars = new ArrayList<>(window.size());
            for (Instant expected : window) {
                KlineBar bar = minuteByTime.get(expected);
                if (bar == null || !bar.complete()) {
                    if (!aggregatedBars.isEmpty()) {
                        klineRepository.upsert(aggregatedBars);
                    }
                    return !aggregatedBars.isEmpty();
                }
                windowBars.add(bar);
            }
            aggregatedBars.add(aggregateWindow(windowBars, targetPeriod));
        }
        if (aggregatedBars.isEmpty()) {
            return false;
        }
        klineRepository.upsert(aggregatedBars);
        log.info("K-line today aggregation completed, symbol={}, targetPeriod={}, written={}",
                symbol, targetPeriod.getCode(), aggregatedBars.size());
        return true;
    }

    private List<List<Instant>> buildWindows(List<Instant> expectedMinutes, KlinePeriod targetPeriod) {
        if (targetPeriod == KlinePeriod.DAY_1) {
            Map<LocalDate, List<Instant>> byDate = new HashMap<>();
            for (Instant expected : expectedMinutes) {
                LocalDate date = LocalDateTime.ofInstant(expected, MARKET_ZONE).toLocalDate();
                byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(expected);
            }
            return byDate.values().stream()
                    .peek(values -> values.sort(Comparator.naturalOrder()))
                    .sorted(Comparator.comparing(values -> values.get(0)))
                    .map(List::copyOf)
                    .toList();
        }
        int size = Math.toIntExact(targetPeriod.getDuration().toMinutes());
        List<List<Instant>> windows = new ArrayList<>();
        for (int index = 0; index + size <= expectedMinutes.size(); index += size) {
            windows.add(expectedMinutes.subList(index, index + size));
        }
        return windows;
    }

    private List<Instant> expectedMinutes(StockInfoEntity stock, Instant cursor, Instant latestMinute) {
        return tradeMinuteWindowService.expectedMinuteInstants(
                exchangeForCompleteness(stock),
                LocalDateTime.ofInstant(cursor, MARKET_ZONE).toLocalDate(),
                LocalDateTime.ofInstant(latestMinute, MARKET_ZONE).toLocalDate()
        ).stream()
                .filter(time -> !time.isBefore(cursor) && !time.isAfter(latestMinute))
                .sorted()
                .toList();
    }

    private KlineBar aggregateWindow(List<KlineBar> minuteBars, KlinePeriod targetPeriod) {
        List<KlineBar> sorted = minuteBars.stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
        KlineBar first = sorted.get(0);
        KlineBar last = sorted.get(sorted.size() - 1);
        BigDecimal high = sorted.stream().map(KlineBar::high).max(Comparator.naturalOrder()).orElse(first.high());
        BigDecimal low = sorted.stream().map(KlineBar::low).min(Comparator.naturalOrder()).orElse(first.low());
        BigDecimal volume = sorted.stream().map(KlineBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = sorted.stream().map(KlineBar::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new KlineBar(
                first.symbol(),
                targetPeriod,
                first.time(),
                first.open(),
                high,
                low,
                last.close(),
                volume,
                amount,
                true,
                "aggregated"
        );
    }

    private void persistState(
            StockSyncStateEntity state,
            StockInfoEntity stock,
            String dataType,
            Instant cursor,
            Instant targetSyncTime,
            boolean success,
            String error) {
        if (state.getId() == null) {
            state.setSymbol(stock.getSymbol());
            state.setDataType(dataType);
            state.setStartTime(resolveInitialStartTime(stock));
            state.setRetryCount(0);
            state.setDataSource("influxdb");
        }
        LocalDateTime cursorTime = LocalDateTime.ofInstant(cursor, MARKET_ZONE);
        state.setCursorTime(cursorTime);
        state.setLatestSyncTime(cursorTime);
        state.setLastError(error);
        if (success) {
            state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
            state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        } else {
            state.setSyncStatus(SyncStatus.PENDING.getCode());
        }
        if (state.getId() == null) {
            stockSyncStateRepository.create(state);
        } else {
            stockSyncStateRepository.update(state);
        }
    }

    private void markCompleteIfCovered(StockSyncStateEntity state, Instant targetSyncTime) {
        if (state.getId() == null) {
            return;
        }
        LocalDateTime cursorTime = state.getCursorTime();
        if (cursorTime == null || cursorTime.atZone(MARKET_ZONE).toInstant().isBefore(targetSyncTime)) {
            return;
        }
        state.setLatestSyncTime(cursorTime);
        state.setLastSuccessTime(LocalDateTime.now(MARKET_ZONE));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        state.setLastError(null);
        stockSyncStateRepository.update(state);
    }

    private StockSyncStateEntity newState(StockInfoEntity stock, String dataType) {
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol(stock.getSymbol());
        state.setDataType(dataType);
        state.setStartTime(resolveInitialStartTime(stock));
        state.setRetryCount(0);
        state.setSyncStatus(SyncStatus.PENDING.getCode());
        state.setDataSource("influxdb");
        return state;
    }

    private Instant cursorInstant(StockSyncStateEntity state, StockInfoEntity stock) {
        LocalDateTime cursor = state.getCursorTime() != null
                ? state.getCursorTime()
                : state.getLatestSyncTime() != null
                ? state.getLatestSyncTime()
                : state.getStartTime() != null ? state.getStartTime() : resolveInitialStartTime(stock);
        LocalDate listDate = stock == null ? null : stock.getListDate();
        if (listDate != null && cursor.toLocalDate().isBefore(listDate)) {
            cursor = listDate.atStartOfDay();
        }
        return cursor.atZone(MARKET_ZONE).toInstant();
    }

    private Instant sourceDataFloor(String symbol) {
        return stockSyncStateRepository.findBySymbolAndDataType(symbol, SyncDataType.KLINE_1M.getCode())
                .map(StockSyncStateEntity::getStartTime)
                .map(startTime -> startTime.atZone(MARKET_ZONE).toInstant())
                .orElse(null);
    }

    private Instant resolveTargetSyncTime(String exchange, LocalDate today) {
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
        return expectedMinutes.getLast();
    }

    private LocalDateTime resolveInitialStartTime(StockInfoEntity stock) {
        LocalDate startDate = defaultStartDate;
        if (stock != null && stock.getListDate() != null && stock.getListDate().isAfter(startDate)) {
            startDate = stock.getListDate();
        }
        return startDate.atStartOfDay();
    }

    private SyncDataType dataType(KlinePeriod period) {
        return switch (period) {
            case MINUTE_5 -> SyncDataType.KLINE_5M;
            case MINUTE_15 -> SyncDataType.KLINE_15M;
            case MINUTE_30 -> SyncDataType.KLINE_30M;
            case HOUR_1 -> SyncDataType.KLINE_1H;
            case DAY_1 -> SyncDataType.KLINE_1D;
            case MINUTE_1 -> throw new IllegalArgumentException("1m is source period and cannot be aggregated");
        };
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

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    private void awaitTermination(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("{} stop timed out", name);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
