package com.fbw.finance.openfinancedb.framework.validation;

public final class ValidationPatterns {

    public static final String SYMBOL = "^[A-Z0-9][A-Z0-9._-]{0,31}$";
    public static final String UPPER_CODE = "^[A-Z][A-Z0-9_]{0,31}$";
    public static final String LOWER_CODE = "^[a-z][a-z0-9_]{0,63}$";
    public static final String IDENTIFIER = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$";

    private ValidationPatterns() {
    }
}