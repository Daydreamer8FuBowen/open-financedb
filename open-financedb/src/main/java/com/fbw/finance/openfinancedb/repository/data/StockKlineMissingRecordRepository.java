package com.fbw.finance.openfinancedb.repository.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockKlineMissingRecordRepository {

    Long create(StockKlineMissingRecordEntity entity);

    boolean update(StockKlineMissingRecordEntity entity);

    boolean upsertMissingDate(StockKlineMissingRecordEntity entity);

    boolean deleteById(Long id);

    Optional<StockKlineMissingRecordEntity> findById(Long id);

    Optional<StockKlineMissingRecordEntity> findBySymbolAndDataTypeAndDataSourceAndMissingDate(
            String symbol,
            String dataType,
            String dataSource,
            LocalDate missingDate);

    PageResult<StockKlineMissingRecordEntity> page(StockKlineMissingRecordPageReqVO reqVO);

    List<LocalDate> findOpenMissingDates(String symbol, String dataType, LocalDate startDate, LocalDate endDate);
}
