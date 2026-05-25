package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import lombok.Data;
import java.util.List;

@Data
public class DashboardSummaryRespVO {
    private Long totalStocks;
    private Long listedStocks;
    private Long realtimeSyncEnabled;
    private Long todaySyncCount;
    private Double tushareSuccessRate;
    private Long todayFailures;
    private List<DailySyncTrendRespVO> dailySyncTrend;
}
