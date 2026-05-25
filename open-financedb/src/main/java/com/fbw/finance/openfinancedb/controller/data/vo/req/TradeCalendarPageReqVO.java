package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TradeCalendarPageReqVO {

    @Min(value = 1, message = "pageNo must be >= 1")
    @Max(value = 100000, message = "pageNo must be <= 100000")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 200, message = "pageSize must be <= 200")
    private Integer pageSize = 20;

    @Size(max = 32, message = "exchange length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "exchange format is invalid")
    private String exchange;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate tradeDate;

    private Boolean isOpen;
}