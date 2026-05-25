package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TradeCalendarUpdateReqVO {

    @NotBlank(message = "exchange cannot be blank")
    @Size(max = 32, message = "exchange length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "exchange format is invalid")
    private String exchange;

    @NotNull(message = "tradeDate cannot be null")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate tradeDate;

    @NotNull(message = "isOpen cannot be null")
    private Boolean isOpen;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate preTradeDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate nextTradeDate;
}