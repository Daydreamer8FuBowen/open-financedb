package com.fbw.finance.openfinancedb.model.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissingRecordStatus implements DictEnum {
    OPEN("OPEN", "Open"),
    REPAIRED("REPAIRED", "Repaired"),
    IGNORED("IGNORED", "Ignored");

    private final String code;
    private final String label;

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(status -> status.code.equals(code));
    }
}
