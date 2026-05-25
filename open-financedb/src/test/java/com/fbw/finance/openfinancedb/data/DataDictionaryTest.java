package com.fbw.finance.openfinancedb.data;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fbw.finance.openfinancedb.model.enums.ExchangeCode;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DataDictionaryTest {

    @Test
    void shouldExposeDictionaryValues() {
        assertFalse(Arrays.asList(ExchangeCode.values()).isEmpty());
        assertFalse(Arrays.asList(SyncStatus.values()).isEmpty());
    }
}
