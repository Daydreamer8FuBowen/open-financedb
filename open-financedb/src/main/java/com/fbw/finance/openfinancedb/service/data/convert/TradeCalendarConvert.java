package com.fbw.finance.openfinancedb.service.data.convert;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.TradeCalendarRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;

public final class TradeCalendarConvert {

    private TradeCalendarConvert() {
    }

    public static TradeCalendarEntity toEntity(TradeCalendarCreateReqVO reqVO) {
        TradeCalendarEntity entity = new TradeCalendarEntity();
        copy(reqVO, entity);
        return entity;
    }

    public static void copy(TradeCalendarUpdateReqVO reqVO, TradeCalendarEntity entity) {
        entity.setExchange(reqVO.getExchange());
        entity.setTradeDate(reqVO.getTradeDate());
        entity.setIsOpen(reqVO.getIsOpen());
        entity.setPreTradeDate(reqVO.getPreTradeDate());
        entity.setNextTradeDate(reqVO.getNextTradeDate());
    }

    public static TradeCalendarRespVO toRespVO(TradeCalendarEntity entity) {
        TradeCalendarRespVO respVO = new TradeCalendarRespVO();
        respVO.setId(entity.getId());
        respVO.setExchange(entity.getExchange());
        respVO.setTradeDate(entity.getTradeDate());
        respVO.setIsOpen(entity.getIsOpen());
        respVO.setPreTradeDate(entity.getPreTradeDate());
        respVO.setNextTradeDate(entity.getNextTradeDate());
        respVO.setCreatedAt(entity.getCreatedAt());
        return respVO;
    }

    private static void copy(TradeCalendarCreateReqVO reqVO, TradeCalendarEntity entity) {
        entity.setExchange(reqVO.getExchange());
        entity.setTradeDate(reqVO.getTradeDate());
        entity.setIsOpen(reqVO.getIsOpen());
        entity.setPreTradeDate(reqVO.getPreTradeDate());
        entity.setNextTradeDate(reqVO.getNextTradeDate());
    }
}
