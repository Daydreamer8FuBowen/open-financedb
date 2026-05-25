package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockSyncStateRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.service.data.StockSyncStateService;
import com.fbw.finance.openfinancedb.service.data.convert.StockSyncStateConvert;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockSyncStateServiceImpl implements StockSyncStateService {

    private final StockSyncStateRepository stockSyncStateRepository;

    public StockSyncStateServiceImpl(StockSyncStateRepository stockSyncStateRepository) {
        this.stockSyncStateRepository = stockSyncStateRepository;
    }

    @Override
    @Transactional
    public Long create(StockSyncStateCreateReqVO reqVO) {
        validateUnique(reqVO.getSymbol(), reqVO.getDataType(), null);
        StockSyncStateEntity entity = StockSyncStateConvert.toEntity(reqVO);
        Long id = stockSyncStateRepository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create stock sync state");
        }
        return id;
    }

    @Override
    @Transactional
    public void update(Long id, StockSyncStateUpdateReqVO reqVO) {
        StockSyncStateEntity entity = getEntity(id);
        validateUnique(reqVO.getSymbol(), reqVO.getDataType(), id);
        StockSyncStateConvert.copy(reqVO, entity);
        entity.setId(id);
        if (!stockSyncStateRepository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update stock sync state");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        stockSyncStateRepository.deleteById(id);
    }

    @Override
    public StockSyncStateRespVO get(Long id) {
        return StockSyncStateConvert.toRespVO(getEntity(id));
    }

    @Override
    public PageResult<StockSyncStateRespVO> page(StockSyncStatePageReqVO reqVO) {
        PageResult<StockSyncStateEntity> pageResult = stockSyncStateRepository.page(reqVO);
        List<StockSyncStateRespVO> list = pageResult.getList().stream().map(StockSyncStateConvert::toRespVO).toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    private StockSyncStateEntity getEntity(Long id) {
        return stockSyncStateRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.STOCK_SYNC_STATE_NOT_FOUND, "stock sync state not found"));
    }

    private void validateUnique(String symbol, String dataType, Long currentId) {
        stockSyncStateRepository.findBySymbolAndDataType(symbol, dataType).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ServiceException(ErrorCodeConstants.STOCK_SYNC_STATE_UNIQUE_DUPLICATE, "stock sync state already exists");
            }
        });
    }
}

