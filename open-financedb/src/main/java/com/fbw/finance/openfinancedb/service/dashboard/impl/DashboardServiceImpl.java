package com.fbw.finance.openfinancedb.service.dashboard.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.ApiUsageBreakdownRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.ApiUsageSummaryRespVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DailySyncTrendRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiUsageLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiUsageLogMapper;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockInfoMapper;
import com.fbw.finance.openfinancedb.repository.data.mapper.SyncLogMapper;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Set<String> KLINE_DATA_TYPES = Set.of(
            SyncDataType.DAILY_KLINE.getCode(),
            SyncDataType.KLINE_1M.getCode(),
            SyncDataType.KLINE_5M.getCode(),
            SyncDataType.KLINE_15M.getCode(),
            SyncDataType.KLINE_30M.getCode(),
            SyncDataType.KLINE_1H.getCode(),
            SyncDataType.KLINE_1D.getCode()
    );

    private final StockInfoMapper stockInfoMapper;
    private final SyncLogMapper syncLogMapper;
    private final ApiUsageLogMapper apiUsageLogMapper;

    public DashboardServiceImpl(StockInfoMapper stockInfoMapper,
                                SyncLogMapper syncLogMapper,
                                ApiUsageLogMapper apiUsageLogMapper) {
        this.stockInfoMapper = stockInfoMapper;
        this.syncLogMapper = syncLogMapper;
        this.apiUsageLogMapper = apiUsageLogMapper;
    }

    @Override
    public DashboardSummaryRespVO getSummary() {
        DashboardSummaryRespVO vo = new DashboardSummaryRespVO();

        vo.setTotalStocks(stockInfoMapper.selectCount(null));

        LambdaQueryWrapper<StockInfoEntity> listedWrapper = new LambdaQueryWrapper<>();
        listedWrapper.eq(StockInfoEntity::getStatus, "LISTED");
        vo.setListedStocks(stockInfoMapper.selectCount(listedWrapper));

        LambdaQueryWrapper<StockInfoEntity> syncWrapper = new LambdaQueryWrapper<>();
        syncWrapper.eq(StockInfoEntity::getIsRealtimeSyncEnabled, true);
        vo.setRealtimeSyncEnabled(stockInfoMapper.selectCount(syncWrapper));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        LambdaQueryWrapper<SyncLogEntity> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(SyncLogEntity::getCreatedAt, todayStart)
                   .lt(SyncLogEntity::getCreatedAt, todayEnd);

        vo.setTodaySyncCount(sumKlineWrittenCount(todayStart, todayEnd));

        LambdaQueryWrapper<SyncLogEntity> failWrapper = new LambdaQueryWrapper<>();
        failWrapper.ge(SyncLogEntity::getCreatedAt, todayStart)
                  .lt(SyncLogEntity::getCreatedAt, todayEnd)
                  .eq(SyncLogEntity::getSuccess, false);
        long todayFailures = syncLogMapper.selectCount(failWrapper);
        vo.setTodayFailures(todayFailures);

        long todayTotalOps = syncLogMapper.selectCount(todayWrapper);
        if (todayTotalOps > 0) {
            vo.setTushareSuccessRate(
                Math.round((1.0 - (double) todayFailures / todayTotalOps) * 10000.0) / 100.0);
        } else {
            vo.setTushareSuccessRate(100.0);
        }

        List<DailySyncTrendRespVO> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            trend.add(new DailySyncTrendRespVO(day.format(fmt), sumKlineWrittenCount(dayStart, dayEnd)));
        }
        vo.setDailySyncTrend(trend);

        return vo;
    }

    @Override
    public ApiUsageSummaryRespVO getApiUsageSummary() {
        ApiUsageSummaryRespVO vo = new ApiUsageSummaryRespVO();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        long todayCalls = countApiUsage(todayStart, todayEnd, null);
        long todayFailures = countApiUsage(todayStart, todayEnd, false);
        vo.setTodayCalls(todayCalls);
        vo.setTodayFailures(todayFailures);
        vo.setSuccessRate(todayCalls == 0 ? 100.0
                : Math.round((1.0 - (double) todayFailures / todayCalls) * 10000.0) / 100.0);
        vo.setAvgLatencyMs(avgLatency(todayStart, todayEnd));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        List<DailySyncTrendRespVO> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            trend.add(new DailySyncTrendRespVO(
                    day.format(fmt),
                    countApiUsage(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), null)
            ));
        }
        vo.setDailyTrend(trend);
        vo.setPathBreakdown(breakdown("path", todayStart, todayEnd));
        vo.setKeyBreakdown(breakdown("COALESCE(CAST(api_key_id AS CHAR), 'anonymous')", todayStart, todayEnd));
        return vo;
    }

    private long sumKlineWrittenCount(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        LambdaQueryWrapper<SyncLogEntity> sumWrapper = new LambdaQueryWrapper<>();
        sumWrapper.select(SyncLogEntity::getWrittenCount)
                .in(SyncLogEntity::getDataType, KLINE_DATA_TYPES)
                .ge(SyncLogEntity::getCreatedAt, startInclusive)
                .lt(SyncLogEntity::getCreatedAt, endExclusive);
        return syncLogMapper.selectObjs(sumWrapper).stream()
                .filter(Objects::nonNull)
                .mapToLong(o -> ((Number) o).longValue())
                .sum();
    }

    private long countApiUsage(LocalDateTime startInclusive, LocalDateTime endExclusive, Boolean success) {
        LambdaQueryWrapper<ApiUsageLogEntity> wrapper = new LambdaQueryWrapper<ApiUsageLogEntity>()
                .ge(ApiUsageLogEntity::getCreatedAt, startInclusive)
                .lt(ApiUsageLogEntity::getCreatedAt, endExclusive);
        if (success != null) {
            wrapper.eq(ApiUsageLogEntity::getSuccess, success);
        }
        return apiUsageLogMapper.selectCount(wrapper);
    }

    private Double avgLatency(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        QueryWrapper<ApiUsageLogEntity> wrapper = new QueryWrapper<ApiUsageLogEntity>()
                .select("AVG(latency_ms) AS avg_latency")
                .ge("created_at", startInclusive)
                .lt("created_at", endExclusive);
        Object value = apiUsageLogMapper.selectMaps(wrapper).stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(map -> map.get("avg_latency"))
                .orElse(null);
        return roundDouble(value);
    }

    private List<ApiUsageBreakdownRespVO> breakdown(String groupExpr,
                                                   LocalDateTime startInclusive,
                                                   LocalDateTime endExclusive) {
        QueryWrapper<ApiUsageLogEntity> wrapper = new QueryWrapper<ApiUsageLogEntity>()
                .select(groupExpr + " AS name",
                        "COUNT(*) AS count",
                        "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failures",
                        "AVG(latency_ms) AS avg_latency")
                .ge("created_at", startInclusive)
                .lt("created_at", endExclusive)
                .groupBy(groupExpr)
                .orderByDesc("count")
                .last("LIMIT 8");
        List<Map<String, Object>> rows = apiUsageLogMapper.selectMaps(wrapper);
        return rows.stream()
                .map(row -> new ApiUsageBreakdownRespVO(
                        Objects.toString(row.get("name"), "-"),
                        toLong(row.get("count")),
                        toLong(row.get("failures")),
                        roundDouble(row.get("avg_latency"))
                ))
                .toList();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Double roundDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        double raw;
        if (value instanceof BigDecimal decimal) {
            raw = decimal.doubleValue();
        } else if (value instanceof Number number) {
            raw = number.doubleValue();
        } else {
            raw = Double.parseDouble(value.toString());
        }
        return Math.round(raw * 100.0) / 100.0;
    }

}

