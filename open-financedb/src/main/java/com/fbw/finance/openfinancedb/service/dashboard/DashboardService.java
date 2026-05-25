package com.fbw.finance.openfinancedb.service.dashboard;

import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;

public interface DashboardService {
    DashboardSummaryRespVO getSummary();
}
