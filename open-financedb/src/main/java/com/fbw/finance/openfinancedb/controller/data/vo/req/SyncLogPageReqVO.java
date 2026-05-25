package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SyncLogPageReqVO {

    @Min(value = 1, message = "pageNo must be >= 1")
    @Max(value = 100000, message = "pageNo must be <= 100000")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 200, message = "pageSize must be <= 200")
    private Integer pageSize = 20;

    @Size(max = 64, message = "logId length must be <= 64")
    @Pattern(regexp = ValidationPatterns.IDENTIFIER, message = "logId format is invalid")
    private String logId;

    @Size(max = 64, message = "taskId length must be <= 64")
    @Pattern(regexp = ValidationPatterns.IDENTIFIER, message = "taskId format is invalid")
    private String taskId;

    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @Size(max = 64, message = "dataType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataType format is invalid")
    private String dataType;

    private Boolean success;
}