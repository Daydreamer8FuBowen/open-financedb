package com.fbw.finance.openfinancedb.service.data.convert;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;

public final class StockInfoConvert {

    private StockInfoConvert() {
    }

    public static StockInfoEntity toEntity(StockInfoCreateReqVO reqVO) {
        StockInfoEntity entity = new StockInfoEntity();
        copy(reqVO, entity);
        return entity;
    }

    public static void copy(StockInfoUpdateReqVO reqVO, StockInfoEntity entity) {
        entity.setSymbol(reqVO.getSymbol());
        entity.setRawSymbol(reqVO.getRawSymbol());
        entity.setName(reqVO.getName());
        entity.setExchange(reqVO.getExchange());
        entity.setMarket(reqVO.getMarket());
        entity.setArea(reqVO.getArea());
        entity.setIndustry(reqVO.getIndustry());
        entity.setType(reqVO.getType());
        entity.setListDate(reqVO.getListDate());
        entity.setDelistDate(reqVO.getDelistDate());
        entity.setStatus(reqVO.getStatus());
        entity.setIsRealtimeSyncEnabled(reqVO.getIsRealtimeSyncEnabled());
        entity.setActEntType(reqVO.getActEntType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setLatestQuoteDate(reqVO.getLatestQuoteDate());
    }

    public static StockInfoRespVO toRespVO(StockInfoEntity entity) {
        StockInfoRespVO respVO = new StockInfoRespVO();
        respVO.setId(entity.getId());
        respVO.setSymbol(entity.getSymbol());
        respVO.setRawSymbol(entity.getRawSymbol());
        respVO.setName(entity.getName());
        respVO.setExchange(entity.getExchange());
        respVO.setMarket(entity.getMarket());
        respVO.setArea(entity.getArea());
        respVO.setIndustry(entity.getIndustry());
        respVO.setType(entity.getType());
        respVO.setListDate(entity.getListDate());
        respVO.setDelistDate(entity.getDelistDate());
        respVO.setStatus(entity.getStatus());
        respVO.setIsRealtimeSyncEnabled(entity.getIsRealtimeSyncEnabled());
        respVO.setActEntType(entity.getActEntType());
        respVO.setDataSource(entity.getDataSource());
        respVO.setLatestQuoteDate(entity.getLatestQuoteDate());
        respVO.setCreatedAt(entity.getCreatedAt());
        respVO.setUpdatedAt(entity.getUpdatedAt());
        return respVO;
    }

    private static void copy(StockInfoCreateReqVO reqVO, StockInfoEntity entity) {
        entity.setSymbol(reqVO.getSymbol());
        entity.setRawSymbol(reqVO.getRawSymbol());
        entity.setName(reqVO.getName());
        entity.setExchange(reqVO.getExchange());
        entity.setMarket(reqVO.getMarket());
        entity.setArea(reqVO.getArea());
        entity.setIndustry(reqVO.getIndustry());
        entity.setType(reqVO.getType());
        entity.setListDate(reqVO.getListDate());
        entity.setDelistDate(reqVO.getDelistDate());
        entity.setStatus(reqVO.getStatus());
        entity.setIsRealtimeSyncEnabled(reqVO.getIsRealtimeSyncEnabled());
        entity.setActEntType(reqVO.getActEntType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setLatestQuoteDate(reqVO.getLatestQuoteDate());
    }
}
