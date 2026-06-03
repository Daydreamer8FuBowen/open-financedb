package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.KlineAggregationService;
import com.fbw.finance.openfinancedb.service.market.KlineCompletionService;
import com.fbw.finance.openfinancedb.service.market.KlineForwardAdjustmentService;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KlineQueryServiceImpl implements KlineQueryService {

    private final KlineRepository klineRepository;
    private final KlineAggregationService aggregationService;
    private final KlineCompletionService completionService;
    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final TushareKlineDataSource tushareKlineDataSource;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final KlineForwardAdjustmentService forwardAdjustmentService;
    private final Clock clock;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    public KlineQueryServiceImpl(
            KlineRepository klineRepository,
            KlineAggregationService aggregationService,
            KlineCompletionService completionService,
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            KlineForwardAdjustmentService forwardAdjustmentService) {
        this(klineRepository, aggregationService, completionService, stockInfoRepository, stockSyncStateRepository,
                tushareKlineDataSource, tradeMinuteWindowService, forwardAdjustmentService, Clock.systemUTC());
    }

    public KlineQueryServiceImpl(
            KlineRepository klineRepository,
            KlineAggregationService aggregationService,
            KlineCompletionService completionService,
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            Clock clock) {
        this(klineRepository, aggregationService, completionService, stockInfoRepository, stockSyncStateRepository,
                tushareKlineDataSource, tradeMinuteWindowService, new NoopKlineForwardAdjustmentService(), clock);
    }

    public KlineQueryServiceImpl(
            KlineRepository klineRepository,
            KlineAggregationService aggregationService,
            KlineCompletionService completionService,
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            TushareKlineDataSource tushareKlineDataSource,
            TradeMinuteWindowService tradeMinuteWindowService,
            KlineForwardAdjustmentService forwardAdjustmentService,
            Clock clock) {
        this.klineRepository = klineRepository;
        this.aggregationService = aggregationService;
        this.completionService = completionService;
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.forwardAdjustmentService = forwardAdjustmentService;
        this.clock = clock;
    }

    @Override
    public List<KlineBar> query(KlineQuery query) {
        return queryResult(query).list();
    }

    @Override
    public KlineQueryResult queryResult(KlineQuery query) {
        StockInfoEntity stock = ensureHistorySyncEnabled(query.symbol());
        QueryPlan queryPlan = planQueryAgainstRecordedStartTime(query);
        query = queryPlan.query();
        List<KlineBar> bars;
        if (!query.endTime().isAfter(query.startTime())) {
            bars = List.of();
        } else if (queryPlan.localOnly()) {
            bars = klineRepository.query(query.symbol(), query.period(), query.startTime(), query.endTime());
            if (query.period() != KlinePeriod.MINUTE_1 && bars.isEmpty()) {
                List<KlineBar> minuteBars = klineRepository.query(
                        query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
                bars = aggregationService.aggregate(minuteBars, query.period());
                klineRepository.upsert(bars);
            }
        } else if (containsToday(query)) {
            bars = queryTodayAware(query, stock);
        } else {
            bars = queryLocalWithCompletion(query, stock);
        }
        if (query.adjusted()) {
            bars = forwardAdjustmentService.forwardAdjust(query, bars);
        }
        return new KlineQueryResult(bars, calculateResultCompleteness(query, stock, bars), query.adjusted());
    }

    private QueryPlan planQueryAgainstRecordedStartTime(KlineQuery query) {
        Optional<StockSyncStateEntity> state = stockSyncStateRepository.findBySymbolAndDataType(
                query.symbol(), SyncDataType.KLINE_1M.getCode());
        if (state.isEmpty() || state.get().getStartTime() == null) {
            return new QueryPlan(query, false);
        }
        Instant recordedStart = state.get().getStartTime().atZone(MARKET_ZONE).toInstant();
        if (!query.startTime().isBefore(recordedStart)) {
            return new QueryPlan(query, false);
        }
        KlineQuery clipped = new KlineQuery(
                query.symbol(),
                query.period(),
                recordedStart,
                query.endTime().isAfter(recordedStart) ? query.endTime() : recordedStart,
                query.adjusted());
        return new QueryPlan(clipped, true);
    }

    private List<KlineBar> queryLocalWithCompletion(KlineQuery query, StockInfoEntity stock) {
        // Read priority: target period first, then derive from 1m, then trigger 1m completion.
        String exchange = exchangeForCompleteness(query.symbol(), stock);
        List<KlineBar> periodBars = klineRepository.query(query.symbol(), query.period(), query.startTime(), query.endTime());
        KlineCompleteness periodCompleteness = klineRepository.checkCompleteness(
                query.symbol(),
                query.period(),
                query.startTime(),
                query.endTime(),
                expectedQueryTimes(exchange, query.period(), query.startTime(), query.endTime()));
        if (periodCompleteness.complete()) {
            return periodBars;
        }

        if (query.period() == KlinePeriod.MINUTE_1) {
            // A 1m miss cannot be aggregated from a smaller period, so completion goes directly
            // to the datasource-backed path.
            completionService.completeMinuteData(query);
            return klineRepository.query(query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
        }

        KlineCompleteness minuteCompleteness = klineRepository.checkCompleteness(
                query.symbol(),
                KlinePeriod.MINUTE_1,
                query.startTime(),
                query.endTime(),
                expectedQueryTimes(exchange, KlinePeriod.MINUTE_1, query.startTime(), query.endTime()));
        if (!minuteCompleteness.complete()) {
            completionService.completeMinuteData(new KlineQuery(
                    query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime()));
        }

        List<KlineBar> minuteBars = klineRepository.query(query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
        List<KlineBar> aggregated = aggregationService.aggregate(minuteBars, query.period());
        // Cache the derived period back into Influx so the next query can hit the target period path.
        klineRepository.upsert(aggregated);
        return aggregated;
    }

    private List<KlineBar> queryTodayAware(KlineQuery query, StockInfoEntity stock) {
        if (query.period() == KlinePeriod.DAY_1) {
            return queryLocalWithCompletion(query, stock);
        }

        if (stock == null || !Boolean.TRUE.equals(stock.getIsRealtimeSyncEnabled()) || !historySyncCompleted(query.symbol())) {
            return mergeBars(fetchRemoteHistoricalBars(query), fetchRealtimeDailyBars(query));
        }

        List<KlineBar> localBars = klineRepository.query(query.symbol(), query.period(), query.startTime(), query.endTime());
        if (isTodayLocalComplete(query, stock, localBars)) {
            return localBars;
        }
        return mergeBars(localBars, fetchRealtimeDailyBars(query));
    }

    private List<KlineBar> fetchRemoteHistoricalBars(KlineQuery query) {
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        LocalDateTime historyStart = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE);
        LocalDateTime historyEnd = today.atStartOfDay();
        if (!historyStart.isBefore(historyEnd)) {
            return List.of();
        }
        return tushareKlineDataSource.fetchMinuteBars(query.symbol(), historyStart, historyEnd, query.period());
    }

    private List<KlineBar> fetchRealtimeDailyBars(KlineQuery query) {
        return tushareKlineDataSource.fetchRealtimeDailyMinuteBars(query.symbol(), query.period()).stream()
                .filter(bar -> !bar.time().isBefore(query.startTime()) && bar.time().isBefore(query.endTime()))
                .toList();
    }

    private boolean isTodayLocalComplete(KlineQuery query, StockInfoEntity stock, List<KlineBar> localBars) {
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        Instant expectedStart = query.startTime();
        Instant todayStart = today.atStartOfDay(MARKET_ZONE).toInstant();
        if (expectedStart.isBefore(todayStart)) {
            expectedStart = todayStart;
        }
        List<Instant> expectedMinutes = expectedQueryTimes(
                exchangeForCompleteness(query.symbol(), stock),
                query.period(),
                expectedStart,
                query.endTime());
        if (expectedMinutes.isEmpty()) {
            return !localBars.isEmpty();
        }
        java.util.Set<Instant> localTimes = localBars.stream()
                .map(KlineBar::time)
                .collect(java.util.stream.Collectors.toSet());
        return localTimes.containsAll(expectedMinutes);
    }

    private boolean alignsToPeriod(Instant time, KlinePeriod period) {
        if (period == KlinePeriod.MINUTE_1) {
            return true;
        }
        LocalDateTime localTime = LocalDateTime.ofInstant(time, MARKET_ZONE);
        long minutes = localTime.getHour() * 60L + localTime.getMinute();
        return minutes % period.getDuration().toMinutes() == 0;
    }

    private KlineCompleteness calculateResultCompleteness(KlineQuery query, StockInfoEntity stock, List<KlineBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return new KlineCompleteness(false, 0, 0);
        }
        if (query.period() == KlinePeriod.DAY_1) {
            long actual = bars.stream().filter(KlineBar::complete).count();
            return new KlineCompleteness(actual == bars.size(), bars.size(), actual);
        }

        Instant latestReturnedTime = bars.stream()
                .map(KlineBar::time)
                .max(Comparator.naturalOrder())
                .orElse(query.startTime());
        LocalDate startDate = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE).toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        if (containsToday(query)) {
            startDate = today;
        }
        Instant expectedStart = query.startTime();
        Instant boundedStart = startDate.atStartOfDay(MARKET_ZONE).toInstant();
        if (expectedStart.isBefore(boundedStart)) {
            expectedStart = boundedStart;
        }
        List<Instant> expected = expectedQueryTimes(
                exchangeForCompleteness(query.symbol(), stock),
                query.period(),
                expectedStart,
                latestReturnedTime.plusNanos(1));
        if (expected.isEmpty()) {
            long actual = bars.stream().filter(KlineBar::complete).count();
            return new KlineCompleteness(actual == bars.size(), bars.size(), actual);
        }
        Set<Instant> returnedTimes = bars.stream()
                .map(KlineBar::time)
                .collect(Collectors.toSet());
        long actual = expected.stream().filter(returnedTimes::contains).count();
        boolean allReturnedBarsComplete = bars.stream().allMatch(KlineBar::complete);
        return new KlineCompleteness(actual == expected.size() && allReturnedBarsComplete, expected.size(), actual);
    }

    private List<Instant> expectedQueryTimes(
            String exchange,
            KlinePeriod period,
            Instant startTime,
            Instant endTimeExclusive) {
        if (exchange == null || !endTimeExclusive.isAfter(startTime)) {
            return List.of();
        }
        LocalDate startDate = LocalDateTime.ofInstant(startTime, MARKET_ZONE).toLocalDate();
        LocalDate endDate = LocalDateTime.ofInstant(endTimeExclusive.minusNanos(1), MARKET_ZONE).toLocalDate();
        return tradeMinuteWindowService.expectedMinuteInstants(exchange, startDate, endDate).stream()
                .filter(time -> !time.isBefore(startTime) && time.isBefore(endTimeExclusive))
                .filter(time -> alignsToPeriod(time, period))
                .toList();
    }

    private boolean containsToday(KlineQuery query) {
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        Instant todayStart = today.atStartOfDay(MARKET_ZONE).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant();
        return query.startTime().isBefore(tomorrowStart) && query.endTime().isAfter(todayStart);
    }

    private boolean historySyncCompleted(String symbol) {
        return stockSyncStateRepository.findBySymbolAndDataType(symbol, SyncDataType.KLINE_1M.getCode())
                .filter(state -> SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus()))
                .isPresent();
    }

    private StockInfoEntity ensureHistorySyncEnabled(String symbol) {
        return stockInfoRepository.findBySymbol(symbol)
                .map(stock -> {
                    if (!Boolean.TRUE.equals(stock.getIsRealtimeSyncEnabled())
                            && stock.getId() != null) {
                        stockInfoRepository.batchUpdateSyncEnabled(List.of(stock.getId()), true);
                    }
                    return stock;
                })
                .orElse(null);
    }

    private List<KlineBar> mergeBars(List<KlineBar> left, List<KlineBar> right) {
        Map<Instant, KlineBar> byTime = new LinkedHashMap<>();
        left.stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .forEach(bar -> byTime.put(bar.time(), bar));
        right.stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .forEach(bar -> byTime.put(bar.time(), bar));
        return byTime.values().stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
    }

    private String exchangeForCompleteness(String symbolHint, StockInfoEntity stock) {
        if (stock != null && stock.getExchange() != null && !stock.getExchange().isBlank()) {
            return stock.getExchange();
        }
        String symbol = stock != null && stock.getSymbol() != null ? stock.getSymbol() : symbolHint;
        if (symbol != null && symbol.endsWith(".SZ")) {
            return "SZSE";
        }
        return "SSE";
    }

    private static final class NoopKlineForwardAdjustmentService implements KlineForwardAdjustmentService {

        @Override
        public List<KlineBar> forwardAdjust(KlineQuery query, List<KlineBar> bars) {
            return bars;
        }
    }

    private record QueryPlan(KlineQuery query, boolean localOnly) {
    }
}
