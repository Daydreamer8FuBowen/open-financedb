package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TradeMinuteWindowServiceImpl implements TradeMinuteWindowService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime MORNING_START = LocalTime.of(9, 31);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 1);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);
    private static final List<String> DEFAULT_EXCHANGES = List.of("SSE", "SZSE");

    private final TradeCalendarRepository tradeCalendarRepository;

    public TradeMinuteWindowServiceImpl(TradeCalendarRepository tradeCalendarRepository) {
        this.tradeCalendarRepository = tradeCalendarRepository;
    }

    @Override
    public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
        List<TradeCalendarEntity> openDays = tradeCalendarRepository.findOpenDays(exchange, startDate, endDate);
        List<Instant> result = new ArrayList<>(openDays.size() * 240);
        for (TradeCalendarEntity openDay : openDays) {
            addSessionMinutes(result, openDay.getTradeDate(), MORNING_START, MORNING_END);
            addSessionMinutes(result, openDay.getTradeDate(), AFTERNOON_START, AFTERNOON_END);
        }
        return result;
    }

    @Override
    public boolean isTradingTime(Instant instant) {
        ZonedDateTime marketTime = instant.atZone(MARKET_ZONE);
        LocalTime time = marketTime.toLocalTime().withSecond(0).withNano(0);
        if (!isInTradingSession(time)) {
            return false;
        }

        LocalDate tradeDate = marketTime.toLocalDate();
        for (String exchange : DEFAULT_EXCHANGES) {
            if (!tradeCalendarRepository.findOpenDays(exchange, tradeDate, tradeDate).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void addSessionMinutes(List<Instant> result, LocalDate tradeDate, LocalTime start, LocalTime end) {
        LocalTime cursor = start;
        while (!cursor.isAfter(end)) {
            result.add(tradeDate.atTime(cursor).atZone(MARKET_ZONE).toInstant());
            cursor = cursor.plusMinutes(1);
        }
    }

    private boolean isInTradingSession(LocalTime time) {
        return (!time.isBefore(MORNING_START) && !time.isAfter(MORNING_END))
                || (!time.isBefore(AFTERNOON_START) && !time.isAfter(AFTERNOON_END));
    }
}
