package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SyncLogCreateReqVO {

    @NotBlank(message = "logId cannot be blank")
    @Size(max = 64, message = "logId length must be <= 64")
    @Pattern(regexp = ValidationPatterns.IDENTIFIER, message = "logId format is invalid")
    private String logId;

    @Size(max = 64, message = "taskId length must be <= 64")
    @Pattern(regexp = ValidationPatterns.IDENTIFIER, message = "taskId format is invalid")
    private String taskId;

    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @NotBlank(message = "dataType cannot be blank")
    @Size(max = 64, message = "dataType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataType format is invalid")
    private String dataType;

    @Size(max = 64, message = "dataSource length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataSource format is invalid")
    private String dataSource;

    @NotNull(message = "startTime cannot be null")
    private LocalDateTime startTime;

    @NotNull(message = "endTime cannot be null")
    private LocalDateTime endTime;

    @Min(value = 0, message = "fetchLatencyMs must be >= 0")
    @Max(value = 86400000L, message = "fetchLatencyMs must be <= 86400000")
    private Long fetchLatencyMs;

    @Min(value = 0, message = "cleanLatencyMs must be >= 0")
    @Max(value = 86400000L, message = "cleanLatencyMs must be <= 86400000")
    private Long cleanLatencyMs;

    @Min(value = 0, message = "writeLatencyMs must be >= 0")
    @Max(value = 86400000L, message = "writeLatencyMs must be <= 86400000")
    private Long writeLatencyMs;

    @Min(value = 0, message = "totalLatencyMs must be >= 0")
    @Max(value = 86400000L, message = "totalLatencyMs must be <= 86400000")
    private Long totalLatencyMs;

    @Min(value = 0, message = "fetchedCount must be >= 0")
    @Max(value = 100000000, message = "fetchedCount must be <= 100000000")
    private Integer fetchedCount;

    @Min(value = 0, message = "cleanedCount must be >= 0")
    @Max(value = 100000000, message = "cleanedCount must be <= 100000000")
    private Integer cleanedCount;

    @Min(value = 0, message = "writtenCount must be >= 0")
    @Max(value = 100000000, message = "writtenCount must be <= 100000000")
    private Integer writtenCount;

    @NotNull(message = "success cannot be null")
    private Boolean success;

    @Size(max = 128, message = "errorType length must be <= 128")
    private String errorType;

    @Size(max = 5000, message = "errorMessage length must be <= 5000")
    private String errorMessage;
}