package com.fbw.finance.openfinancedb.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogCreateReqVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectInvalidPageConstraints() {
        StockInfoPageReqVO reqVO = new StockInfoPageReqVO();
        reqVO.setPageNo(0);
        reqVO.setPageSize(500);
        reqVO.setSymbol("bad symbol");

        var violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(item -> "pageNo".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "pageSize".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "symbol".equals(item.getPropertyPath().toString())));
    }

    @Test
    void shouldRequireRealtimeSyncFlagForStockInfoCreate() {
        StockInfoCreateReqVO reqVO = new StockInfoCreateReqVO();
        reqVO.setSymbol("000001.SZ");
        reqVO.setName("Ping An Bank");

        var violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(item -> "isRealtimeSyncEnabled".equals(item.getPropertyPath().toString())));
    }

    @Test
    void shouldRejectMissingRequiredSyncLogFields() {
        SyncLogCreateReqVO reqVO = new SyncLogCreateReqVO();
        reqVO.setLogId("bad log id");
        reqVO.setSuccess(null);

        var violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(item -> "logId".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "symbol".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "dataType".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "startTime".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "endTime".equals(item.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(item -> "success".equals(item.getPropertyPath().toString())));
    }
}
