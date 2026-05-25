package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailySyncTrendRespVO {
    private String date;
    private Long count;
}
