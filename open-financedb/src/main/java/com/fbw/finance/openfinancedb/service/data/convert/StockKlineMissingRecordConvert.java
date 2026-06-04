package com.fbw.finance.openfinancedb.service.data.convert;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockKlineMissingRecordRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;

public final class StockKlineMissingRecordConvert {

    private StockKlineMissingRecordConvert() {
    }

    public static StockKlineMissingRecordEntity toEntity(StockKlineMissingRecordCreateReqVO reqVO) {
        StockKlineMissingRecordEntity entity = new StockKlineMissingRecordEntity();
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setMissingDate(reqVO.getMissingDate());
        entity.setStatus(reqVO.getStatus());
        entity.setDetectedAt(reqVO.getDetectedAt());
        entity.setRepairedAt(reqVO.getRepairedAt());
        entity.setRemark(reqVO.getRemark());
        return entity;
    }

    public static void copy(StockKlineMissingRecordUpdateReqVO reqVO, StockKlineMissingRecordEntity entity) {
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setMissingDate(reqVO.getMissingDate());
        entity.setStatus(reqVO.getStatus());
        entity.setDetectedAt(reqVO.getDetectedAt());
        entity.setRepairedAt(reqVO.getRepairedAt());
        entity.setRemark(reqVO.getRemark());
    }

    public static StockKlineMissingRecordRespVO toRespVO(StockKlineMissingRecordEntity entity) {
        StockKlineMissingRecordRespVO respVO = new StockKlineMissingRecordRespVO();
        respVO.setId(entity.getId());
        respVO.setSymbol(entity.getSymbol());
        respVO.setDataType(entity.getDataType());
        respVO.setDataSource(entity.getDataSource());
        respVO.setMissingDate(entity.getMissingDate());
        respVO.setStatus(entity.getStatus());
        respVO.setDetectedAt(entity.getDetectedAt());
        respVO.setRepairedAt(entity.getRepairedAt());
        respVO.setRemark(entity.getRemark());
        respVO.setCreatedAt(entity.getCreatedAt());
        respVO.setUpdatedAt(entity.getUpdatedAt());
        return respVO;
    }
}
