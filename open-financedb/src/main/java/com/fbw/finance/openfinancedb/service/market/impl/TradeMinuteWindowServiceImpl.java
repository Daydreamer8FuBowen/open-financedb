package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private void addSessionMinutes(List<Instant> result, LocalDate tradeDate, LocalTime start, LocalTime end) {
        // A 股 1m K 线按收盘分钟记点：09:31-11:30 共 120 根，13:01-15:00 共 120 根。
        LocalTime cursor = start;
        while (!cursor.isAfter(end)) {
            result.add(tradeDate.atTime(cursor).atZone(MARKET_ZONE).toInstant());
            cursor = cursor.plusMinutes(1);
        }
    }
}
