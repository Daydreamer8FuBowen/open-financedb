package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import java.util.List;
import lombok.Data;

@Data
public class ApiUsageSummaryRespVO {

    private Long todayCalls;
    private Long todayFailures;
    private Double successRate;
    private Double avgLatencyMs;
    private List<DailySyncTrendRespVO> dailyTrend;
    private List<ApiUsageBreakdownRespVO> pathBreakdown;
    private List<ApiUsageBreakdownRespVO> keyBreakdown;
}
