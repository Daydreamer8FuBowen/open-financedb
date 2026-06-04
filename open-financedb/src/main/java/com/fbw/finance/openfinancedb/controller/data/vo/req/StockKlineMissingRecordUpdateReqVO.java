package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockKlineMissingRecordUpdateReqVO {

    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @NotBlank(message = "dataType cannot be blank")
    @Size(max = 64, message = "dataType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataType format is invalid")
    private String dataType;

    @NotBlank(message = "dataSource cannot be blank")
    @Size(max = 64, message = "dataSource length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataSource format is invalid")
    private String dataSource;

    @NotNull(message = "missingDate cannot be null")
    private LocalDate missingDate;

    @NotBlank(message = "status cannot be blank")
    @Size(max = 32, message = "status length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "status format is invalid")
    private String status;

    private LocalDateTime detectedAt;
    private LocalDateTime repairedAt;

    @Size(max = 5000, message = "remark length must be <= 5000")
    private String remark;
}
