package com.fbw.finance.openfinancedb.service.data.impl;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.TradeCalendarRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.data.TradeCalendarService;
import com.fbw.finance.openfinancedb.service.data.convert.TradeCalendarConvert;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeCalendarServiceImpl implements TradeCalendarService {

    private final TradeCalendarRepository tradeCalendarRepository;

    public TradeCalendarServiceImpl(TradeCalendarRepository tradeCalendarRepository) {
        this.tradeCalendarRepository = tradeCalendarRepository;
    }

    @Override
    @Transactional
    public Long create(TradeCalendarCreateReqVO reqVO) {
        validateUnique(reqVO.getExchange(), reqVO.getTradeDate(), null);
        TradeCalendarEntity entity = TradeCalendarConvert.toEntity(reqVO);
        Long id = tradeCalendarRepository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create trade calendar");
        }
        return id;
    }

    @Override
    @Transactional
    public void update(Long id, TradeCalendarUpdateReqVO reqVO) {
        TradeCalendarEntity entity = getEntity(id);
        validateUnique(reqVO.getExchange(), reqVO.getTradeDate(), id);
        TradeCalendarConvert.copy(reqVO, entity);
        entity.setId(id);
        if (!tradeCalendarRepository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update trade calendar");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        tradeCalendarRepository.deleteById(id);
    }

    @Override
    public TradeCalendarRespVO get(Long id) {
        return TradeCalendarConvert.toRespVO(getEntity(id));
    }

    @Override
    public PageResult<TradeCalendarRespVO> page(TradeCalendarPageReqVO reqVO) {
        PageResult<TradeCalendarEntity> pageResult = tradeCalendarRepository.page(reqVO);
        List<TradeCalendarRespVO> list = pageResult.getList().stream().map(TradeCalendarConvert::toRespVO).toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    private TradeCalendarEntity getEntity(Long id) {
        return tradeCalendarRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.TRADE_CALENDAR_NOT_FOUND, "trade calendar not found"));
    }

    private void validateUnique(String exchange, java.time.LocalDate tradeDate, Long currentId) {
        tradeCalendarRepository.findByExchangeAndTradeDate(exchange, tradeDate).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ServiceException(ErrorCodeConstants.TRADE_CALENDAR_UNIQUE_DUPLICATE, "trade calendar already exists");
            }
        });
    }
}
