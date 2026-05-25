package com.fbw.finance.openfinancedb.service.data.convert;

import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.SyncLogRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;

public final class SyncLogConvert {

    private SyncLogConvert() {
    }

    public static SyncLogEntity toEntity(SyncLogCreateReqVO reqVO) {
        SyncLogEntity entity = new SyncLogEntity();
        copy(reqVO, entity);
        return entity;
    }

    public static void copy(SyncLogUpdateReqVO reqVO, SyncLogEntity entity) {
        entity.setLogId(reqVO.getLogId());
        entity.setTaskId(reqVO.getTaskId());
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setStartTime(reqVO.getStartTime());
        entity.setEndTime(reqVO.getEndTime());
        entity.setFetchLatencyMs(reqVO.getFetchLatencyMs());
        entity.setCleanLatencyMs(reqVO.getCleanLatencyMs());
        entity.setWriteLatencyMs(reqVO.getWriteLatencyMs());
        entity.setTotalLatencyMs(reqVO.getTotalLatencyMs());
        entity.setFetchedCount(reqVO.getFetchedCount());
        entity.setCleanedCount(reqVO.getCleanedCount());
        entity.setWrittenCount(reqVO.getWrittenCount());
        entity.setSuccess(reqVO.getSuccess());
        entity.setErrorType(reqVO.getErrorType());
        entity.setErrorMessage(reqVO.getErrorMessage());
    }

    public static SyncLogRespVO toRespVO(SyncLogEntity entity) {
        SyncLogRespVO respVO = new SyncLogRespVO();
        respVO.setId(entity.getId());
        respVO.setLogId(entity.getLogId());
        respVO.setTaskId(entity.getTaskId());
        respVO.setSymbol(entity.getSymbol());
        respVO.setDataType(entity.getDataType());
        respVO.setDataSource(entity.getDataSource());
        respVO.setStartTime(entity.getStartTime());
        respVO.setEndTime(entity.getEndTime());
        respVO.setFetchLatencyMs(entity.getFetchLatencyMs());
        respVO.setCleanLatencyMs(entity.getCleanLatencyMs());
        respVO.setWriteLatencyMs(entity.getWriteLatencyMs());
        respVO.setTotalLatencyMs(entity.getTotalLatencyMs());
        respVO.setFetchedCount(entity.getFetchedCount());
        respVO.setCleanedCount(entity.getCleanedCount());
        respVO.setWrittenCount(entity.getWrittenCount());
        respVO.setSuccess(entity.getSuccess());
        respVO.setErrorType(entity.getErrorType());
        respVO.setErrorMessage(entity.getErrorMessage());
        respVO.setCreatedAt(entity.getCreatedAt());
        return respVO;
    }

    private static void copy(SyncLogCreateReqVO reqVO, SyncLogEntity entity) {
        entity.setLogId(reqVO.getLogId());
        entity.setTaskId(reqVO.getTaskId());
        entity.setSymbol(reqVO.getSymbol());
        entity.setDataType(reqVO.getDataType());
        entity.setDataSource(reqVO.getDataSource());
        entity.setStartTime(reqVO.getStartTime());
        entity.setEndTime(reqVO.getEndTime());
        entity.setFetchLatencyMs(reqVO.getFetchLatencyMs());
        entity.setCleanLatencyMs(reqVO.getCleanLatencyMs());
        entity.setWriteLatencyMs(reqVO.getWriteLatencyMs());
        entity.setTotalLatencyMs(reqVO.getTotalLatencyMs());
        entity.setFetchedCount(reqVO.getFetchedCount());
        entity.setCleanedCount(reqVO.getCleanedCount());
        entity.setWrittenCount(reqVO.getWrittenCount());
        entity.setSuccess(reqVO.getSuccess());
        entity.setErrorType(reqVO.getErrorType());
        entity.setErrorMessage(reqVO.getErrorMessage());
    }
}
