package com.fbw.finance.openfinancedb.service.dashboard.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DailySyncTrendRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockInfoMapper;
import com.fbw.finance.openfinancedb.repository.data.mapper.SyncLogMapper;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    public DashboardServiceImpl(StockInfoMapper stockInfoMapper,
                                SyncLogMapper syncLogMapper) {
        this.stockInfoMapper = stockInfoMapper;
        this.syncLogMapper = syncLogMapper;
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
}

