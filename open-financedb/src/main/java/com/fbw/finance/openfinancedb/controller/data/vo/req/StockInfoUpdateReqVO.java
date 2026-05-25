package com.fbw.finance.openfinancedb.controller.data.vo.req;

import com.fbw.finance.openfinancedb.framework.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class StockInfoUpdateReqVO {

    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 32, message = "symbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "symbol format is invalid")
    private String symbol;

    @Size(max = 32, message = "rawSymbol length must be <= 32")
    @Pattern(regexp = ValidationPatterns.SYMBOL, message = "rawSymbol format is invalid")
    private String rawSymbol;

    @NotBlank(message = "name cannot be blank")
    @Size(max = 128, message = "name length must be <= 128")
    private String name;

    @Size(max = 32, message = "exchange length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "exchange format is invalid")
    private String exchange;

    @Size(max = 32, message = "market length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "market format is invalid")
    private String market;

    @Size(max = 64, message = "area length must be <= 64")
    private String area;

    @Size(max = 128, message = "industry length must be <= 128")
    private String industry;

    @Size(max = 32, message = "type length must be <= 32")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "type format is invalid")
    private String type;

    private LocalDate listDate;
    private LocalDate delistDate;

    @Size(max = 32, message = "status length must be <= 32")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "status format is invalid")
    private String status;

    @NotNull(message = "isRealtimeSyncEnabled cannot be null")
    private Boolean isRealtimeSyncEnabled;

    @Size(max = 64, message = "actEntType length must be <= 64")
    @Pattern(regexp = ValidationPatterns.UPPER_CODE, message = "actEntType format is invalid")
    private String actEntType;

    @Size(max = 64, message = "dataSource length must be <= 64")
    @Pattern(regexp = ValidationPatterns.LOWER_CODE, message = "dataSource format is invalid")
    private String dataSource;

    private LocalDate latestQuoteDate;
}
