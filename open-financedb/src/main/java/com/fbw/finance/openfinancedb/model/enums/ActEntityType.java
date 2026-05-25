package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActEntityType implements DictEnum {
    CENTRAL_SOE("CENTRAL_SOE", "Central SOE"),
    STATE_OWNED("STATE_OWNED", "State-owned"),
    PRIVATE("PRIVATE", "Private"),
    FOREIGN_FUNDED("FOREIGN_FUNDED", "Foreign-funded");

    private final String code;
    private final String label;
}
