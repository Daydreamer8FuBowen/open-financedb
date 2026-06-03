package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SyncStatus implements DictEnum {
    PENDING("PENDING", "Pending"),
    RUNNING("RUNNING", "Running"),
    SUCCESS("SUCCESS", "Success"),
    FAILED("FAILED", "Failed"),
    INCOMPLETE("INCOMPLETE", "Incomplete"),
    RETRYING("RETRYING", "Retrying"),
    PAUSED("PAUSED", "Paused");

    private final String code;
    private final String label;
}
