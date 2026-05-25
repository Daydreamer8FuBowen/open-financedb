package com.fbw.finance.openfinancedb.model.market;

import java.time.Duration;
import java.util.Arrays;

public enum KlinePeriod {
    MINUTE_1("1m", Duration.ofMinutes(1)),
    MINUTE_5("5m", Duration.ofMinutes(5)),
    MINUTE_15("15m", Duration.ofMinutes(15)),
    MINUTE_30("30m", Duration.ofMinutes(30)),
    HOUR_1("1h", Duration.ofHours(1)),
    DAY_1("1d", Duration.ofDays(1));

    private final String code;
    private final Duration duration;

    KlinePeriod(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    public String getCode() {
        return code;
    }

    public Duration getDuration() {
        return duration;
    }

    public static KlinePeriod fromCode(String code) {
        return Arrays.stream(values())
                .filter(period -> period.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported kline period: " + code));
    }
}
