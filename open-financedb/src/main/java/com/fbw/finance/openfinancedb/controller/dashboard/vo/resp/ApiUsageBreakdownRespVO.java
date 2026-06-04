package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageBreakdownRespVO {

    private String name;
    private Long count;
    private Long failures;
    private Double avgLatencyMs;
}
