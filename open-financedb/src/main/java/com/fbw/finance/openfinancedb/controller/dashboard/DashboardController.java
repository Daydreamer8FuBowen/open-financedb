package com.fbw.finance.openfinancedb.controller.dashboard;

import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.ApiUsageSummaryRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public CommonResult<DashboardSummaryRespVO> getSummary() {
        return CommonResult.success(dashboardService.getSummary());
    }

    @GetMapping("/api-usage")
    public CommonResult<ApiUsageSummaryRespVO> getApiUsage() {
        return CommonResult.success(dashboardService.getApiUsageSummary());
    }
}
