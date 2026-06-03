package com.fbw.finance.openfinancedb.service.data.convert;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockSyncStateRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;

public final class StockSyncStateConvert {

    private StockSyncStateConvert() {
    }

    public static StockSyncStateEntity toEntity(StockSyncStateCreateReqVO reqVO) {
        StockSyncStateEntity entity = new StockSyncStateEntity();
        copy(reqVO, entity);
        return entity;
    }

    public static void copy(StockSyncStateUpdateReqVO reqVO, StockSyncStateEntity entity) {
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setStartTime(reqVO.getStartTime());
        entity.setLatestSyncTime(reqVO.getLatestSyncTime());
        entity.setLastSuccessTime(reqVO.getLastSuccessTime());
        entity.setLastFailedTime(reqVO.getLastFailedTime());
        entity.setSyncStatus(reqVO.getSyncStatus());
        entity.setRetryCount(reqVO.getRetryCount());
        entity.setDataSource(reqVO.getDataSource());
        entity.setLastError(reqVO.getLastError());
    }

    public static StockSyncStateRespVO toRespVO(StockSyncStateEntity entity) {
        StockSyncStateRespVO respVO = new StockSyncStateRespVO();
        respVO.setId(entity.getId());
        respVO.setSymbol(entity.getSymbol());
        respVO.setDataType(entity.getDataType());
        respVO.setStartTime(entity.getStartTime());
        respVO.setLatestSyncTime(entity.getLatestSyncTime());
        respVO.setLastSuccessTime(entity.getLastSuccessTime());
        respVO.setLastFailedTime(entity.getLastFailedTime());
        respVO.setSyncStatus(entity.getSyncStatus());
        respVO.setRetryCount(entity.getRetryCount());
        respVO.setDataSource(entity.getDataSource());
        respVO.setLastError(entity.getLastError());
        respVO.setCreatedAt(entity.getCreatedAt());
        respVO.setUpdatedAt(entity.getUpdatedAt());
        return respVO;
    }

    private static void copy(StockSyncStateCreateReqVO reqVO, StockSyncStateEntity entity) {
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setStartTime(reqVO.getStartTime());
        entity.setLatestSyncTime(reqVO.getLatestSyncTime());
        entity.setLastSuccessTime(reqVO.getLastSuccessTime());
        entity.setLastFailedTime(reqVO.getLastFailedTime());
        entity.setSyncStatus(reqVO.getSyncStatus());
        entity.setRetryCount(reqVO.getRetryCount());
        entity.setDataSource(reqVO.getDataSource());
        entity.setLastError(reqVO.getLastError());
    }
}
