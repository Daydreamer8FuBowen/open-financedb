package com.fbw.finance.openfinancedb.controller.data.vo.resp;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SyncLogRespVO {

    private Long id;
    private String logId;
    private String taskId;
    private String symbol;
    private String dataType;
    private String dataSource;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long fetchLatencyMs;
    private Long cleanLatencyMs;
    private Long writeLatencyMs;
    private Long totalLatencyMs;
    private Integer fetchedCount;
    private Integer cleanedCount;
    private Integer writtenCount;
    private Boolean success;
    private String errorType;
    private String errorMessage;
    private LocalDateTime createdAt;
}
