package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordStatusReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockKlineMissingRecordRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;
import com.fbw.finance.openfinancedb.model.enums.MissingRecordStatus;
import com.fbw.finance.openfinancedb.repository.data.StockKlineMissingRecordRepository;
import com.fbw.finance.openfinancedb.service.data.StockKlineMissingRecordService;
import com.fbw.finance.openfinancedb.service.data.convert.StockKlineMissingRecordConvert;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StockKlineMissingRecordServiceImpl implements StockKlineMissingRecordService {

    private final StockKlineMissingRecordRepository repository;

    public StockKlineMissingRecordServiceImpl(StockKlineMissingRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Long create(StockKlineMissingRecordCreateReqVO reqVO) {
        validateStatusOrDefault(reqVO.getStatus());
        validateUnique(reqVO.getSymbol(), reqVO.getDataType(), reqVO.getDataSource(), reqVO.getMissingDate(), null);
        StockKlineMissingRecordEntity entity = StockKlineMissingRecordConvert.toEntity(reqVO);
        applyDefaults(entity);
        Long id = repository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create stock kline missing record");
        }
        return id;
    }

    @Override
    @Transactional
    public void update(Long id, StockKlineMissingRecordUpdateReqVO reqVO) {
        validateStatus(reqVO.getStatus());
        StockKlineMissingRecordEntity entity = getEntity(id);
        validateUnique(reqVO.getSymbol(), reqVO.getDataType(), reqVO.getDataSource(), reqVO.getMissingDate(), id);
        StockKlineMissingRecordConvert.copy(reqVO, entity);
        entity.setId(id);
        normalizeStatusTimes(entity, reqVO.getRepairedAt());
        if (!repository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update stock kline missing record");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        repository.deleteById(id);
    }

    @Override
    public StockKlineMissingRecordRespVO get(Long id) {
        return StockKlineMissingRecordConvert.toRespVO(getEntity(id));
    }

    @Override
    public PageResult<StockKlineMissingRecordRespVO> page(StockKlineMissingRecordPageReqVO reqVO) {
        PageResult<StockKlineMissingRecordEntity> pageResult = repository.page(reqVO);
        List<StockKlineMissingRecordRespVO> list = pageResult.getList().stream()
                .map(StockKlineMissingRecordConvert::toRespVO)
                .toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, StockKlineMissingRecordStatusReqVO reqVO) {
        validateStatus(reqVO.getStatus());
        StockKlineMissingRecordEntity entity = getEntity(id);
        entity.setStatus(reqVO.getStatus());
        entity.setRemark(reqVO.getRemark());
        normalizeStatusTimes(entity, reqVO.getRepairedAt());
        if (!repository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to change stock kline missing record status");
        }
    }

    @Override
    @Transactional
    public void changeStatus(String symbol, String dataType, String dataSource, LocalDate missingDate, String status, String remark) {
        validateStatus(status);
        StockKlineMissingRecordEntity entity = repository
                .findBySymbolAndDataTypeAndDataSourceAndMissingDate(symbol, dataType, dataSource, missingDate)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.STOCK_KLINE_MISSING_RECORD_NOT_FOUND,
                        "stock kline missing record not found"));
        entity.setStatus(status);
        entity.setRemark(remark);
        normalizeStatusTimes(entity, null);
        if (!repository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to change stock kline missing record status");
        }
    }

    private StockKlineMissingRecordEntity getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.STOCK_KLINE_MISSING_RECORD_NOT_FOUND,
                        "stock kline missing record not found"));
    }

    private void validateUnique(String symbol, String dataType, String dataSource, LocalDate missingDate, Long currentId) {
        repository.findBySymbolAndDataTypeAndDataSourceAndMissingDate(symbol, dataType, dataSource, missingDate)
                .ifPresent(existing -> {
                    if (currentId == null || !existing.getId().equals(currentId)) {
                        throw new ServiceException(ErrorCodeConstants.STOCK_KLINE_MISSING_RECORD_UNIQUE_DUPLICATE,
                                "stock kline missing record already exists");
                    }
                });
    }

    private void applyDefaults(StockKlineMissingRecordEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(MissingRecordStatus.OPEN.getCode());
        }
        if (entity.getDetectedAt() == null) {
            entity.setDetectedAt(LocalDateTime.now());
        }
        normalizeStatusTimes(entity, entity.getRepairedAt());
    }

    private void normalizeStatusTimes(StockKlineMissingRecordEntity entity, LocalDateTime repairedAt) {
        if (MissingRecordStatus.REPAIRED.getCode().equals(entity.getStatus())) {
            entity.setRepairedAt(repairedAt == null ? LocalDateTime.now() : repairedAt);
        } else {
            entity.setRepairedAt(null);
        }
    }

    private void validateStatusOrDefault(String status) {
        if (StringUtils.hasText(status)) {
            validateStatus(status);
        }
    }

    private void validateStatus(String status) {
        if (!MissingRecordStatus.isValid(status)) {
            throw new ServiceException(ErrorCodeConstants.BAD_REQUEST, "missing record status is invalid");
        }
    }
}
