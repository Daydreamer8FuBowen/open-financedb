package com.fbw.finance.openfinancedb.repository.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import java.util.Optional;

public interface StockSyncStateRepository {

    Long create(StockSyncStateEntity entity);

    boolean update(StockSyncStateEntity entity);

    boolean deleteById(Long id);

    Optional<StockSyncStateEntity> findById(Long id);

    Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType);

    PageResult<StockSyncStateEntity> page(StockSyncStatePageReqVO reqVO);
}
