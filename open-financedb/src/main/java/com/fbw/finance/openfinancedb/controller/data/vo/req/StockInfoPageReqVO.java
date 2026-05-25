package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StockInfoPageReqVO {

    @Min(value = 1, message = "pageNo must be >= 1")
    @Max(value = 100000, message = "pageNo must be <= 100000")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 200, message = "pageSize must be <= 200")
    private Integer pageSize = 20;

    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @Size(max = 128, message = "name length must be <= 128")
    private String name;

    @Size(max = 32, message = "exchange length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "exchange format is invalid")
    private String exchange;

    @Size(max = 32, message = "market length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "market format is invalid")
    private String market;

    @Size(max = 32, message = "type length must be <= 32")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "type format is invalid")
    private String type;

    @Size(max = 32, message = "status length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "status format is invalid")
    private String status;

    private Boolean isRealtimeSyncEnabled;
}
