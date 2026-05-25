package com.fbw.finance.openfinancedb.controller.data.vo.resp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TradeCalendarRespVO {

    private Long id;
    private String exchange;
    private LocalDate tradeDate;
    private Boolean isOpen;
    private LocalDate preTradeDate;
    private LocalDate nextTradeDate;
    private LocalDateTime createdAt;
}
