package com.fbw.finance.openfinancedb.controller.data.vo.resp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockInfoRespVO {

    private Long id;
    private String symbol;
    private String rawSymbol;
    private String name;
    private String exchange;
    private String market;
    private String area;
    private String industry;
    private String type;
    private LocalDate listDate;
    private LocalDate delistDate;
    private String status;
    private Boolean isRealtimeSyncEnabled;
    private String syncDataType;
    private String syncStatus;
    private LocalDateTime syncStartTime;
    private LocalDateTime syncLatestTime;
    private Integer syncProgressPercent;
    private String syncLastError;
    private String actEntType;
    private String dataSource;
    private LocalDate latestQuoteDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
