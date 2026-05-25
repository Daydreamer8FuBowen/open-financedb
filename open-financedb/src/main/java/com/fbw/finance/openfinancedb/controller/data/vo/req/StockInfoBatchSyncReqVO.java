package com.fbw.finance.openfinancedb.controller.data.vo.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class StockInfoBatchSyncReqVO {
    @NotEmpty(message = "ids must not be empty")
    private List<Long> ids;

    @NotNull(message = "enabled must not be null")
    private Boolean enabled;
}
