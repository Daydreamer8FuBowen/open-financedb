package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class StockKlineMissingRecordPageReqVO {

    @Min(value = 1, message = "pageNo must be >= 1")
    @Max(value = 100000, message = "pageNo must be <= 100000")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 200, message = "pageSize must be <= 200")
    private Integer pageSize = 20;

    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @Size(max = 64, message = "dataType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataType format is invalid")
    private String dataType;

    @Size(max = 64, message = "dataSource length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataSource format is invalid")
    private String dataSource;

    @Size(max = 32, message = "status length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "status format is invalid")
    private String status;

    private LocalDate missingDate;
    private LocalDate startDate;
    private LocalDate endDate;
}
