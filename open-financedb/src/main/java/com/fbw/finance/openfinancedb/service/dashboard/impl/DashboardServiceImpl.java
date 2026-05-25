package com.fbw.finance.openfinancedb.service.dashboard.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DailySyncTrendRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockInfoMapper;
import com.fbw.finance.openfinancedb.repository.data.mapper.SyncLogMapper;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

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
        vo.setTodaySyncCount(syncLogMapper.selectCount(todayWrapper));

        LambdaQueryWrapper<SyncLogEntity> failWrapper = new LambdaQueryWrapper<>();
        failWrapper.ge(SyncLogEntity::getCreatedAt, todayStart)
                  .lt(SyncLogEntity::getCreatedAt, todayEnd)
                  .eq(SyncLogEntity::getSuccess, false);
        long todayFailures = syncLogMapper.selectCount(failWrapper);
        vo.setTodayFailures(todayFailures);

        long todayTotal = vo.getTodaySyncCount();
        if (todayTotal > 0) {
            vo.setTushareSuccessRate(
                Math.round((1.0 - (double) todayFailures / todayTotal) * 10000.0) / 100.0);
        } else {
            vo.setTushareSuccessRate(100.0);
        }

        List<DailySyncTrendRespVO> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            LambdaQueryWrapper<SyncLogEntity> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.ge(SyncLogEntity::getCreatedAt, dayStart)
                     .lt(SyncLogEntity::getCreatedAt, dayEnd);
            long count = syncLogMapper.selectCount(dayWrapper);

            trend.add(new DailySyncTrendRespVO(day.format(fmt), count));
        }
        vo.setDailySyncTrend(trend);

        return vo;
    }
}
