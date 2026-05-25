package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockSyncStateUpdateReqVO {

    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @NotBlank(message = "dataType cannot be blank")
    @Size(max = 64, message = "dataType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataType format is invalid")
    private String dataType;

    private LocalDateTime startTime;
    private LocalDateTime latestSyncTime;
    private LocalDateTime targetSyncTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastFailedTime;

    @Size(max = 32, message = "syncStatus length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "syncStatus format is invalid")
    private String syncStatus;

    @Min(value = 0, message = "retryCount must be >= 0")
    @Max(value = 100000, message = "retryCount must be <= 100000")
    private Integer retryCount;

    @Size(max = 64, message = "dataSource length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataSource format is invalid")
    private String dataSource;

    @Size(max = 5000, message = "lastError length must be <= 5000")
    private String lastError;
}