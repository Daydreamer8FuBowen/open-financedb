package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.model.financial.IncomeStatementPoint;
import java.time.LocalDate;
import java.util.List;

public interface TushareFinancialDataSource {

    List<IncomeStatementPoint> fetchIncome(String symbol, LocalDate startDate, LocalDate endDate);
}
