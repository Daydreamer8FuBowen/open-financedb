package com.fbw.finance.openfinancedb.controller.market;

import com.fbw.finance.openfinancedb.controller.market.vo.resp.RealtimeKlineSyncRoundRespVO;
import com.fbw.finance.openfinancedb.controller.market.vo.resp.RealtimeKlineSyncStatusRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundSnapshot;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/realtime-kline-sync")
public class RealtimeKlineSyncMonitorController {

    private final RealtimeKlineSyncMonitorService monitorService;

    public RealtimeKlineSyncMonitorController(RealtimeKlineSyncMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/status")
    public CommonResult<RealtimeKlineSyncStatusRespVO> getStatus() {
        return CommonResult.success(toRespVO(monitorService.getStatus()));
    }

    private RealtimeKlineSyncStatusRespVO toRespVO(RealtimeKlineSyncStatusSnapshot snapshot) {
        return new RealtimeKlineSyncStatusRespVO(
                snapshot.enabled(),
                snapshot.tradingTime(),
                snapshot.schedulerState().name(),
                format(snapshot.snapshotTime()),
                format(snapshot.lastSuccessTime()),
                format(snapshot.lastErrorTime()),
                snapshot.lastErrorMessage(),
                toRoundRespVO(snapshot.currentRound()),
                snapshot.recentRounds().stream().map(this::toRoundRespVO).toList()
        );
    }

    private RealtimeKlineSyncRoundRespVO toRoundRespVO(RealtimeKlineSyncRoundSnapshot round) {
        if (round == null) {
            return null;
        }
        return new RealtimeKlineSyncRoundRespVO(
                round.roundId(),
                round.status().name(),
                format(round.startedAt()),
                format(round.finishedAt()),
                round.durationMillis(),
                round.symbolCount(),
                round.chunkCount(),
                round.completedChunks(),
                round.failedChunks(),
                round.retryCount(),
                round.writtenBars(),
                round.poolSize(),
                round.cancelReason(),
                round.lastErrorMessage()
        );
    }

    private String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
