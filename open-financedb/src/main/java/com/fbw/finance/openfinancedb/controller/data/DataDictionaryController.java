package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.resp.DictItemRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.enums.ActEntityType;
import com.fbw.finance.openfinancedb.model.enums.DataSourceType;
import com.fbw.finance.openfinancedb.model.enums.DictEnum;
import com.fbw.finance.openfinancedb.model.enums.ExchangeCode;
import com.fbw.finance.openfinancedb.model.enums.MarketType;
import com.fbw.finance.openfinancedb.model.enums.SecurityType;
import com.fbw.finance.openfinancedb.model.enums.StockStatus;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data/dictionaries")
public class DataDictionaryController {

    @GetMapping("/exchanges")
    public CommonResult<List<DictItemRespVO>> exchanges() {
        return CommonResult.success(toItems(ExchangeCode.values()));
    }

    @GetMapping("/markets")
    public CommonResult<List<DictItemRespVO>> markets() {
        return CommonResult.success(toItems(MarketType.values()));
    }

    @GetMapping("/security-types")
    public CommonResult<List<DictItemRespVO>> securityTypes() {
        return CommonResult.success(toItems(SecurityType.values()));
    }

    @GetMapping("/stock-statuses")
    public CommonResult<List<DictItemRespVO>> stockStatuses() {
        return CommonResult.success(toItems(StockStatus.values()));
    }

    @GetMapping("/entity-types")
    public CommonResult<List<DictItemRespVO>> entityTypes() {
        return CommonResult.success(toItems(ActEntityType.values()));
    }

    @GetMapping("/data-sources")
    public CommonResult<List<DictItemRespVO>> dataSources() {
        return CommonResult.success(toItems(DataSourceType.values()));
    }

    @GetMapping("/sync-data-types")
    public CommonResult<List<DictItemRespVO>> syncDataTypes() {
        return CommonResult.success(toItems(SyncDataType.values()));
    }

    @GetMapping("/sync-statuses")
    public CommonResult<List<DictItemRespVO>> syncStatuses() {
        return CommonResult.success(toItems(SyncStatus.values()));
    }

    private List<DictItemRespVO> toItems(DictEnum[] values) {
        return Arrays.stream(values)
                .map(item -> new DictItemRespVO(item.getCode(), item.getLabel()))
                .toList();
    }
}

