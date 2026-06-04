package com.fbw.finance.openfinancedb.controller.data.vo.resp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockKlineMissingRecordRespVO {

    private Long id;
    private String symbol;
    private String dataType;
    private String dataSource;
    private LocalDate missingDate;
    private String status;
    private LocalDateTime detectedAt;
    private LocalDateTime repairedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
