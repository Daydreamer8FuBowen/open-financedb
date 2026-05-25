package com.fbw.finance.openfinancedb.service.market;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface TradeMinuteWindowService {

    List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate);
}
