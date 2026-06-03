package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.market.impl.TradeMinuteWindowServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TradeMinuteWindowServiceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldRecognizeTradingTimeOnlyOnOpenDaysAndTradingSessions() {
        TradeMinuteWindowService service = new TradeMinuteWindowServiceImpl(new FakeTradeCalendarRepository(true));

        assertTrue(service.isTradingTime(marketInstant(2026, 5, 27, 9, 31)));
        assertTrue(service.isTradingTime(marketInstant(2026, 5, 27, 15, 0)));
        assertFalse(service.isTradingTime(marketInstant(2026, 5, 27, 11, 31)));
        assertFalse(service.isTradingTime(marketInstant(2026, 5, 27, 15, 1)));
    }

    @Test
    void shouldRejectTradingSessionTimeWhenMarketCalendarIsClosed() {
        TradeMinuteWindowService service = new TradeMinuteWindowServiceImpl(new FakeTradeCalendarRepository(false));

        assertFalse(service.isTradingTime(marketInstant(2026, 5, 27, 9, 31)));
    }

    private static Instant marketInstant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(MARKET_ZONE).toInstant();
    }

    private static final class FakeTradeCalendarRepository implements TradeCalendarRepository {
        private final boolean open;

        private FakeTradeCalendarRepository(boolean open) {
            this.open = open;
        }

        @Override
        public Long create(TradeCalendarEntity entity) {
            return null;
        }

        @Override
        public boolean update(TradeCalendarEntity entity) {
            return false;
        }

        @Override
        public boolean upsertByExchangeAndTradeDate(TradeCalendarEntity entity) {
            return false;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<TradeCalendarEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeCalendarEntity> findByExchangeAndTradeDate(String exchange, LocalDate tradeDate) {
            return Optional.empty();
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
            if (!open || !"SSE".equals(exchange)) {
                return List.of();
            }
            TradeCalendarEntity day = new TradeCalendarEntity();
            day.setExchange(exchange);
            day.setTradeDate(startDate);
            day.setIsOpen(true);
            return List.of(day);
        }

        @Override
        public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }
}
