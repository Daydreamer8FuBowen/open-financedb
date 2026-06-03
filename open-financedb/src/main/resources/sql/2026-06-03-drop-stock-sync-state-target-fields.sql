ALTER TABLE stock_sync_state
    DROP KEY idx_source_latest_time,
    DROP COLUMN target_sync_time,
    DROP COLUMN source_latest_time;
