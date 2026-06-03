package com.fbw.finance.openfinancedb.repository.market;

import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdjFactorRepository {

    void upsert(List<AdjFactorPoint> factors);

    List<AdjFactorPoint> query(String symbol, LocalDate startDate, LocalDate endDate);

    Optional<LocalDate> findLatestTradeDate(String symbol);
}
