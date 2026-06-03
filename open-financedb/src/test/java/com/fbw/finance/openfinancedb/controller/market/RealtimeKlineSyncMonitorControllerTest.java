package com.fbw.finance.openfinancedb.controller.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeKlineSyncMonitorControllerTest {

    @Test
    void shouldReturnCommonResultStatusPayload() {
        RealtimeKlineSyncMonitorController controller = new RealtimeKlineSyncMonitorController(
                new FakeMonitorService()
        );

        var result = controller.getStatus();

        assertEquals(0, result.getCode());
        assertTrue(result.getData().enabled());
        assertEquals("IDLE", result.getData().schedulerState());
        assertEquals("2026-05-27T02:00:00Z", result.getData().snapshotTime());
    }

    private static final class FakeMonitorService implements RealtimeKlineSyncMonitorService {
        @Override
        public RealtimeKlineSyncStatusSnapshot getStatus() {
            return new RealtimeKlineSyncStatusSnapshot(
                    true,
                    false,
                    RealtimeKlineSyncSchedulerState.IDLE,
                    Instant.parse("2026-05-27T02:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }
    }
}
