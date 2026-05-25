package com.fbw.finance.openfinancedb.repository.financial;

import com.fbw.finance.openfinancedb.model.entity.financial.StockIncomeStatementEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockIncomeStatementRepository {

    Long create(StockIncomeStatementEntity entity);

    boolean update(StockIncomeStatementEntity entity);

    boolean upsertByUniqueKey(StockIncomeStatementEntity entity);

    Optional<StockIncomeStatementEntity> findByUniqueKey(
            String symbol,
            LocalDate endDate,
            String reportType,
            String compType
    );

    List<StockIncomeStatementEntity> findBySymbolAndEndDateBetween(
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    );
}
