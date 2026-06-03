package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncByQueryReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.service.data.StockInfoService;
import com.fbw.finance.openfinancedb.service.data.convert.StockInfoConvert;
import com.fbw.finance.openfinancedb.service.market.AdjFactorSyncService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockInfoServiceImpl implements StockInfoService {

    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;
    private final AdjFactorSyncService adjFactorSyncService;

    @Autowired
    public StockInfoServiceImpl(StockInfoRepository stockInfoRepository,
                                StockSyncStateRepository stockSyncStateRepository,
                                AdjFactorSyncService adjFactorSyncService) {
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
        this.adjFactorSyncService = adjFactorSyncService;
    }

    public StockInfoServiceImpl(StockInfoRepository stockInfoRepository,
                                StockSyncStateRepository stockSyncStateRepository) {
        this(stockInfoRepository, stockSyncStateRepository, null);
    }

    @Override
    @Transactional
    public Long create(StockInfoCreateReqVO reqVO) {
        validateSymbolUnique(reqVO.getSymbol(), null);
        StockInfoEntity entity = StockInfoConvert.toEntity(reqVO);
        Long id = stockInfoRepository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create stock info");
        }
        return id;
    }

    @Override
    @Transactional
    public void update(Long id, StockInfoUpdateReqVO reqVO) {
        StockInfoEntity entity = getEntity(id);
        validateSymbolUnique(reqVO.getSymbol(), id);
        StockInfoConvert.copy(reqVO, entity);
        entity.setId(id);
        if (!stockInfoRepository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update stock info");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        stockInfoRepository.deleteById(id);
    }

    @Override
    public StockInfoRespVO get(Long id) {
        return StockInfoConvert.toRespVO(getEntity(id));
    }

    @Override
    public PageResult<StockInfoRespVO> page(StockInfoPageReqVO reqVO) {
        PageResult<StockInfoEntity> pageResult = stockInfoRepository.page(reqVO);
        List<StockInfoRespVO> list = pageResult.getList().stream().map(StockInfoConvert::toRespVO).toList();
        attachMinuteSyncProgress(list);
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional
    public int batchUpdateSyncEnabled(StockInfoBatchSyncReqVO reqVO) {
        int updated = stockInfoRepository.batchUpdateSyncEnabled(reqVO.getIds(), reqVO.getEnabled());
        if (Boolean.TRUE.equals(reqVO.getEnabled())) {
            reqVO.getIds().stream()
                    .map(stockInfoRepository::findById)
                    .flatMap(java.util.Optional::stream)
                    .forEach(this::triggerAdjFactorHistorySync);
        }
        return updated;
    }

    @Override
    @Transactional
    public int batchUpdateSyncEnabledByQuery(StockInfoBatchSyncByQueryReqVO reqVO) {
        StockInfoPageReqVO query = new StockInfoPageReqVO();
        query.setSymbol(reqVO.getSymbol());
        query.setName(reqVO.getName());
        query.setExchange(reqVO.getExchange());
        query.setMarket(reqVO.getMarket());
        query.setType(reqVO.getType());
        query.setStatus(reqVO.getStatus());
        query.setIsRealtimeSyncEnabled(reqVO.getIsRealtimeSyncEnabled());
        int updated = stockInfoRepository.batchUpdateSyncEnabledByQuery(query, reqVO.getEnabled());
        if (Boolean.TRUE.equals(reqVO.getEnabled())) {
            StockInfoPageReqVO enabledQuery = new StockInfoPageReqVO();
            enabledQuery.setSymbol(query.getSymbol());
            enabledQuery.setName(query.getName());
            enabledQuery.setExchange(query.getExchange());
            enabledQuery.setMarket(query.getMarket());
            enabledQuery.setType(query.getType());
            enabledQuery.setStatus(query.getStatus());
            enabledQuery.setIsRealtimeSyncEnabled(true);
            stockInfoRepository.list(enabledQuery).forEach(this::triggerAdjFactorHistorySync);
        }
        return updated;
    }

    private void triggerAdjFactorHistorySync(StockInfoEntity stock) {
        if (adjFactorSyncService == null) {
            return;
        }
        adjFactorSyncService.syncStockHistoryAsync(stock);
    }

    private void attachMinuteSyncProgress(List<StockInfoRespVO> list) {
        List<String> symbols = list.stream()
                .map(StockInfoRespVO::getSymbol)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (symbols.isEmpty()) {
            return;
        }
        Map<String, StockSyncStateEntity> states = stockSyncStateRepository
                .findBySymbolsAndDataType(symbols, SyncDataType.KLINE_1M.getCode())
                .stream()
                .collect(Collectors.toMap(StockSyncStateEntity::getSymbol, Function.identity(), (left, right) -> left));
        for (StockInfoRespVO row : list) {
            StockSyncStateEntity state = states.get(row.getSymbol());
            if (state == null) {
                continue;
            }
            row.setSyncDataType(state.getDataType());
            row.setSyncStatus(state.getSyncStatus());
            row.setSyncStartTime(state.getStartTime());
            row.setSyncLatestTime(state.getLatestSyncTime());
            row.setSyncLastError(state.getLastError());
            row.setSyncProgressPercent(calculateProgressPercent(state));
        }
    }

    private Integer calculateProgressPercent(StockSyncStateEntity state) {
        return com.fbw.finance.openfinancedb.model.enums.SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus())
                ? 100
                : null;
    }

    private StockInfoEntity getEntity(Long id) {
        return stockInfoRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.STOCK_INFO_NOT_FOUND, "stock info not found"));
    }

    private void validateSymbolUnique(String symbol, Long currentId) {
        stockInfoRepository.findBySymbol(symbol).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ServiceException(ErrorCodeConstants.STOCK_INFO_SYMBOL_DUPLICATE, "stock symbol already exists");
            }
        });
    }
}

