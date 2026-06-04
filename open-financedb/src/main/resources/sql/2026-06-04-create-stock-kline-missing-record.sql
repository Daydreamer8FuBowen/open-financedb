CREATE TABLE IF NOT EXISTS stock_kline_missing_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    symbol VARCHAR(32) NOT NULL COMMENT 'Stock symbol, for example 000001.SZ',
    data_type VARCHAR(64) NOT NULL DEFAULT 'KLINE_1MIN' COMMENT 'Kline data type',
    data_source VARCHAR(64) NOT NULL DEFAULT 'TUSHARE' COMMENT 'Data source',
    missing_date DATE NOT NULL COMMENT 'Missing trade date',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'Missing data status',
    detected_at DATETIME DEFAULT NULL COMMENT 'Detected time',
    repaired_at DATETIME DEFAULT NULL COMMENT 'Repaired time',
    remark TEXT DEFAULT NULL COMMENT 'Remark',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    UNIQUE KEY uk_symbol_data_type_source_date (symbol, data_type, data_source, missing_date),
    KEY idx_symbol (symbol),
    KEY idx_data_type_status (data_type, status),
    KEY idx_missing_date (missing_date),
    KEY idx_symbol_missing_date (symbol, missing_date),
    KEY idx_updated_at (updated_at)
) COMMENT='Stock kline missing data record';
