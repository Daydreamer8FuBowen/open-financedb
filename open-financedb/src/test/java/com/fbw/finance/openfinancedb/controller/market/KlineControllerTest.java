package com.fbw.finance.openfinancedb.controller.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.controller.market.vo.req.KlineQueryReqVO;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class KlineControllerTest {

    @Test
    void shouldPassAdjustedParameterAndReturnQueryCompleteness() {
        RecordingKlineQueryService service = new RecordingKlineQueryService();
        KlineController controller = new KlineController(service);
        KlineQueryReqVO reqVO = new KlineQueryReqVO();
        reqVO.setSymbol("000001.SZ");
        reqVO.setPeriod("1m");
        reqVO.setStartTime(OffsetDateTime.parse("2026-05-28T09:31:00+08:00"));
        reqVO.setEndTime(OffsetDateTime.parse("2026-05-28T09:33:00+08:00"));
        reqVO.setAdjusted(true);

        var result = controller.query(reqVO);

        assertEquals(0, result.getCode());
        assertTrue(service.query.adjusted());
        assertTrue(result.getData().adjusted());
        assertTrue(result.getData().complete());
        assertEquals(2, result.getData().expectedCount());
        assertEquals(2, result.getData().actualCount());
        assertEquals(1, result.getData().list().size());
    }

    private static final class RecordingKlineQueryService implements KlineQueryService {
        private KlineQuery query;

        @Override
        public List<KlineBar> query(KlineQuery query) {
            this.query = query;
            return List.of();
        }

        @Override
        public KlineQueryResult queryResult(KlineQuery query) {
            this.query = query;
            return new KlineQueryResult(
                    List.of(new KlineBar(
                            query.symbol(),
                            query.period(),
                            Instant.parse("2026-05-28T01:31:00Z"),
                            BigDecimal.TEN,
                            BigDecimal.TEN,
                            BigDecimal.TEN,
                            BigDecimal.TEN,
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            true,
                            "tushare"
                    )),
                    new KlineCompleteness(true, 2, 2),
                    query.adjusted()
            );
        }
    }
}
