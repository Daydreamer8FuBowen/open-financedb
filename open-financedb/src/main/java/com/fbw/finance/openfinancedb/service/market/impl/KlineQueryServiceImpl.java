package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
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
import com.fbw.finance.openfinancedb.service.data.StockInfoService;
import com.fbw.finance.openfinancedb.service.market.KlineForwardAdjustmentService;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KlineQueryServiceImpl implements KlineQueryService {

    private static final Logger log = LoggerFactory.getLogger(KlineQueryServiceImpl.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LISTED_STATUS = "LISTED";

    private final KlineRepository klineRepository;
    private final KlineForwardAdjustmentService forwardAdjustmentService;
    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final TushareKlineDataSource tushareKlineDataSource;
    private final StockInfoService stockInfoService;
    private final LocalDate defaultStartDate;
    private final Clock clock;

    public KlineQueryServiceImpl(
            KlineRepository klineRepository,
            KlineForwardAdjustmentService forwardAdjustmentService,
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository,
            TradeMinuteWindowService tradeMinuteWindowService,
            TushareKlineDataSource tushareKlineDataSource,
            StockInfoService stockInfoService,
            @Value("${finance.history-sync.default-start-date:2015-01-01}") LocalDate defaultStartDate,
            Clock clock) {
        this.klineRepository = klineRepository;
        this.forwardAdjustmentService = forwardAdjustmentService;
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.tushareKlineDataSource = tushareKlineDataSource;
        this.stockInfoService = stockInfoService;
        this.defaultStartDate = defaultStartDate;
        this.clock = clock;
    }

    @Override
    public List<KlineBar> query(KlineQuery query) {
        return queryResult(query).list();
    }

    @Override
    public KlineQueryResult queryResult(KlineQuery query) {
        // 查询入口统一编排：
        // 1) 校验股票存在且必须为上市状态（LISTED）
        // 2) 归一化 startTime（取 query.startTime / listDate / sync_state.start_time / defaultStartDate 的最大值）
        // 3) 先判断“是否开启历史同步/是否完成历史同步”，再决定是否走 Influx
        // 4) Influx 缺失或不完整时回退 Tushare（本次查询不写 Influx），并可回拨 1m 同步游标触发重同步
        StockInfoEntity stock = loadListedStock(query.symbol());
        Optional<StockSyncStateEntity> minuteState = loadMinuteState(query.symbol());
        KlineQuery normalizedQuery = resolveNormalizedQuery(query, stock, minuteState.orElse(null));
        List<Instant> expectedBarTimes = expectedBarTimes(stock, normalizedQuery);
        KlineCompleteness expectedCompleteness = new KlineCompleteness(false, expectedBarTimes.size(), 0);

        if (!Boolean.TRUE.equals(stock.getIsRealtimeSyncEnabled())) {
            // 未开启历史同步：先打开同步开关，让后台 worker 后续纳入扫描范围；本次请求直接回退 Tushare
            stockInfoService.enableRealtimeSync(stock.getSymbol());
            return fallbackResult(normalizedQuery, expectedBarTimes);
        }

        if (!isMinuteHistoryCompleted(minuteState.orElse(null))) {
            // 已开启但历史同步未完成：不重复开开关，直接回退 Tushare
            return fallbackResult(normalizedQuery, expectedBarTimes);
        }

        // 已开启且 1m 历史同步已完成：优先查 Influx，再用交易窗口推导的 expectedBarTimes 做完整性判定
        InfluxQueryOutcome influx = queryInfluxAndCheckCompleteness(normalizedQuery, expectedBarTimes);
        if (influx.completeness().complete()) {
            List<KlineBar> bars = applyAdjustmentIfNeeded(normalizedQuery, influx.bars());
            return new KlineQueryResult(bars, influx.completeness(), normalizedQuery.adjusted());
        }

        // Influx 有缺口：按约定必须回退 Tushare 并返回，同时回拨 1m 同步游标（cursor_time）触发后台重同步
        log.error("K-line query detected Influx gap, symbol={}, period={}, start={}, end={}, expectedCount={}, actualCount={}",
                normalizedQuery.symbol(),
                normalizedQuery.period().getCode(),
                normalizedQuery.startTime(),
                normalizedQuery.endTime(),
                influx.completeness().expectedCount(),
                influx.completeness().actualCount());
        resetMinuteSyncStateForResync(minuteState.orElse(null), normalizedQuery);
        return fallbackResult(normalizedQuery, expectedBarTimes);
    }

    private StockInfoEntity loadListedStock(String symbol) {
        // 查询必须基于 stock_info 主数据；不存在/非上市均按业务错误返回
        StockInfoEntity stock = stockInfoRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.STOCK_INFO_NOT_FOUND, "stock info not found"));
        if (!LISTED_STATUS.equals(stock.getStatus())) {
            throw new ServiceException(
                    ErrorCodeConstants.KLINE_STOCK_NOT_LISTED,
                    "stock must be listed for kline query: " + symbol
            );
        }
        return stock;
    }

    private Optional<StockSyncStateEntity> loadMinuteState(String symbol) {
        return stockSyncStateRepository.findBySymbolAndDataType(symbol, SyncDataType.KLINE_1M.getCode());
    }

    private KlineQuery resolveNormalizedQuery(KlineQuery query, StockInfoEntity stock, StockSyncStateEntity minuteState) {
        // 归一化 startTime：
        // - query.startTime：请求起点
        // - defaultStartDate：历史同步的全局最小起点
        // - stock.listDate：上市日期之前不应被查询
        // - minuteState.startTime：该股票分钟线同步流的实际起点（用于约束查询范围）
        Instant normalizedStart = query.startTime();
        normalizedStart = maxInstant(normalizedStart, defaultStartDate.atStartOfDay(MARKET_ZONE).toInstant());
        if (stock.getListDate() != null) {
            normalizedStart = maxInstant(normalizedStart, stock.getListDate().atStartOfDay(MARKET_ZONE).toInstant());
        }
        if (minuteState != null && minuteState.getStartTime() != null) {
            normalizedStart = maxInstant(normalizedStart, minuteState.getStartTime().atZone(MARKET_ZONE).toInstant());
        }
        if (!query.endTime().isAfter(normalizedStart)
                || !query.endTime().isAfter(normalizedStart.plus(query.period().getDuration()))) {
            // 归一化后若有效区间不足 1 个周期，则直接按业务错误报错（不返回空结果）
            throw new ServiceException(ErrorCodeConstants.KLINE_TIME_RANGE_INVALID, "invalid kline time range");
        }
        return new KlineQuery(
                query.symbol(),
                query.period(),
                normalizedStart,
                query.endTime(),
                query.adjusted()
        );
    }

    private Instant maxInstant(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private boolean isMinuteHistoryCompleted(StockSyncStateEntity state) {
        return state != null && SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus());
    }

    private InfluxQueryOutcome queryInfluxAndCheckCompleteness(KlineQuery query, List<Instant> expectedBarTimes) {
        List<KlineBar> bars = klineRepository.query(query.symbol(), query.period(), query.startTime(), query.endTime()).stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
        KlineCompleteness completeness = klineRepository.checkCompleteness(
                query.symbol(),
                query.period(),
                query.startTime(),
                query.endTime(),
                expectedBarTimes
        );
        return new InfluxQueryOutcome(bars, completeness);
    }

    private KlineQueryResult fallbackResult(KlineQuery query, List<Instant> expectedBarTimes) {
        // 回退只用于本次响应：不写 Influx、不推进同步游标
        List<KlineBar> bars = fetchFallbackBarsFromTushare(query);
        KlineCompleteness completeness = calculateCompleteness(bars, expectedBarTimes);
        bars = applyAdjustmentIfNeeded(query, bars);
        return new KlineQueryResult(bars, completeness, query.adjusted());
    }

    private List<KlineBar> fetchFallbackBarsFromTushare(KlineQuery query) {
        if (query.period() == KlinePeriod.DAY_1) {
            LocalDate startDate = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE).toLocalDate();
            LocalDate endDate = LocalDateTime.ofInstant(query.endTime().minusNanos(1), MARKET_ZONE).toLocalDate();
            return tushareKlineDataSource.fetchDailyBars(query.symbol(), startDate, endDate).stream()
                    .filter(bar -> !bar.time().isBefore(query.startTime()) && bar.time().isBefore(query.endTime()))
                    .sorted(Comparator.comparing(KlineBar::time))
                    .toList();
        }
        LocalDateTime startTime = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE);
        LocalDateTime endTime = LocalDateTime.ofInstant(query.endTime(), MARKET_ZONE);
        return tushareKlineDataSource.fetchMinuteBars(query.symbol(), startTime, endTime, query.period()).stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
    }

    private void resetMinuteSyncStateForResync(StockSyncStateEntity state, KlineQuery normalizedQuery) {
        if (state == null || state.getId() == null) {
            return;
        }
        // 当“历史已完成但 Influx 缺失”时，回拨分钟线同步游标，让后台 worker 重新从归一化起点补齐
        LocalDateTime cursorTime = LocalDateTime.ofInstant(normalizedQuery.startTime(), MARKET_ZONE);
        if (state.getStartTime() == null || cursorTime.isBefore(state.getStartTime())) {
            state.setStartTime(cursorTime);
        }
        state.setCursorTime(cursorTime);
        state.setSyncStatus(SyncStatus.PENDING.getCode());
        state.setLastFailedTime(LocalDateTime.now(clock.withZone(MARKET_ZONE)));
        state.setRetryCount(state.getRetryCount() == null ? 1 : state.getRetryCount() + 1);
        state.setLastError("query detected influx gap for period=" + normalizedQuery.period().getCode() + ", fallback to tushare");
        stockSyncStateRepository.update(state);
    }

    private List<KlineBar> applyAdjustmentIfNeeded(KlineQuery query, List<KlineBar> bars) {
        if (!query.adjusted()) {
            return bars;
        }
        return forwardAdjustmentService.forwardAdjust(query, bars);
    }

    private KlineCompleteness calculateCompleteness(List<KlineBar> bars, List<Instant> expectedBarTimes) {
        if (expectedBarTimes == null || expectedBarTimes.isEmpty()) {
            return new KlineCompleteness(false, 0, 0);
        }
        Set<Instant> expected = new HashSet<>(expectedBarTimes);
        long actual = bars.stream()
                .filter(KlineBar::complete)
                .map(KlineBar::time)
                .filter(expected::contains)
                .distinct()
                .count();
        return new KlineCompleteness(actual == expectedBarTimes.size(), expectedBarTimes.size(), actual);
    }

    private List<Instant> expectedBarTimes(StockInfoEntity stock, KlineQuery query) {
        // expectedBarTimes 用于“完整性”判定：
        // - 先根据交易日历计算该区间所有预期 1m 分钟点
        // - 再按请求周期（1m/5m/15m/30m/1h/1d）映射到该周期的“预期 bar 起点”
        LocalDate startDate = LocalDateTime.ofInstant(query.startTime(), MARKET_ZONE).toLocalDate();
        LocalDate endDate = LocalDateTime.ofInstant(query.endTime().minusNanos(1), MARKET_ZONE).toLocalDate();
        List<Instant> expectedMinutes = tradeMinuteWindowService.expectedMinuteInstants(stock.getExchange(), startDate, endDate).stream()
                .sorted()
                .toList();
        List<Instant> expectedBarTimes = switch (query.period()) {
            case MINUTE_1 -> expectedMinutes.stream()
                    .filter(time -> !time.isBefore(query.startTime()) && time.isBefore(query.endTime()))
                    .toList();
            case DAY_1 -> buildWindows(expectedMinutes, query.period()).stream()
                    .filter(window -> !window.isEmpty())
                    .filter(window -> window.getFirst().compareTo(query.startTime()) >= 0)
                    .filter(window -> window.getLast().plusSeconds(60).compareTo(query.endTime()) <= 0)
                    .map(List::getFirst)
                    .toList();
            default -> buildWindows(expectedMinutes, query.period()).stream()
                    .filter(window -> !window.isEmpty())
                    .filter(window -> window.getFirst().compareTo(query.startTime()) >= 0)
                    .filter(window -> window.getLast().plusSeconds(60).compareTo(query.endTime()) <= 0)
                    .map(List::getFirst)
                    .toList();
        };
        if (expectedBarTimes.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.KLINE_TIME_RANGE_INVALID, "invalid kline time range");
        }
        return expectedBarTimes;
    }

    private List<List<Instant>> buildWindows(List<Instant> expectedMinutes, KlinePeriod period) {
        if (period == KlinePeriod.DAY_1) {
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
        int size = Math.toIntExact(period.getDuration().toMinutes());
        List<List<Instant>> windows = new ArrayList<>();
        for (int index = 0; index + size <= expectedMinutes.size(); index += size) {
            windows.add(List.copyOf(expectedMinutes.subList(index, index + size)));
        }
        return windows;
    }

    private record InfluxQueryOutcome(List<KlineBar> bars, KlineCompleteness completeness) {
    }
}
