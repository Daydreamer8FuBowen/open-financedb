package com.fbw.finance.openfinancedb.controller.data.vo.resp;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockSyncStateRespVO {

    private Long id;
    private String symbol;
    private String dataType;
    private LocalDateTime startTime;
    private LocalDateTime latestSyncTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastFailedTime;
    private String syncStatus;
    private Integer retryCount;
    private String dataSource;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
