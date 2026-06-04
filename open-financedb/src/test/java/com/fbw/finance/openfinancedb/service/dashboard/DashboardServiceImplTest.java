package com.fbw.finance.openfinancedb.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiUsageLogMapper;
import com.fbw.finance.openfinancedb.service.dashboard.impl.DashboardServiceImpl;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardServiceImplTest {

    @Test
    void apiUsageSummaryHandlesEmptyUsageLog() {
        DashboardServiceImpl service = new DashboardServiceImpl(null, null, emptyUsageLogMapper());

        var summary = service.getApiUsageSummary();

        assertThat(summary.getTodayCalls()).isZero();
        assertThat(summary.getTodayFailures()).isZero();
        assertThat(summary.getSuccessRate()).isEqualTo(100.0);
        assertThat(summary.getAvgLatencyMs()).isZero();
        assertThat(summary.getPathBreakdown()).isEmpty();
        assertThat(summary.getKeyBreakdown()).isEmpty();
    }

    private static ApiUsageLogMapper emptyUsageLogMapper() {
        int[] selectMapsCalls = {0};
        return (ApiUsageLogMapper) Proxy.newProxyInstance(
                ApiUsageLogMapper.class.getClassLoader(),
                new Class<?>[]{ApiUsageLogMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectCount" -> 0L;
                    case "selectMaps" -> {
                        selectMapsCalls[0]++;
                        if (selectMapsCalls[0] == 1) {
                            List<Map<String, Object>> rows = new ArrayList<>();
                            rows.add(null);
                            yield rows;
                        }
                        yield List.of();
                    }
                    default -> {
                        if (method.getDeclaringClass().equals(Object.class)) {
                            yield method.invoke(proxy, args);
                        }
                        yield List.of();
                    }
                }
        );
    }
}
