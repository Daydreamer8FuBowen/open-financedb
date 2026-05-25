package com.fbw.finance.openfinancedb.repository.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import java.util.List;
import java.util.Optional;

public interface StockInfoRepository {

    Long create(StockInfoEntity entity);

    boolean update(StockInfoEntity entity);

    boolean upsertPreservingRealtimeFlag(StockInfoEntity entity);

    boolean deleteById(Long id);

    Optional<StockInfoEntity> findById(Long id);

    Optional<StockInfoEntity> findBySymbol(String symbol);

    List<StockInfoEntity> findRealtimeSyncEnabled();

    PageResult<StockInfoEntity> page(StockInfoPageReqVO reqVO);

    int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled);
}
