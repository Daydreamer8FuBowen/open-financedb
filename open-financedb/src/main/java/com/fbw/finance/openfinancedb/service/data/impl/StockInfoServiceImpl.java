package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.service.data.StockInfoService;
import com.fbw.finance.openfinancedb.service.data.convert.StockInfoConvert;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockInfoServiceImpl implements StockInfoService {

    private final StockInfoRepository stockInfoRepository;

    public StockInfoServiceImpl(StockInfoRepository stockInfoRepository) {
        this.stockInfoRepository = stockInfoRepository;
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
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional
    public int batchUpdateSyncEnabled(StockInfoBatchSyncReqVO reqVO) {
        return stockInfoRepository.batchUpdateSyncEnabled(reqVO.getIds(), reqVO.getEnabled());
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
