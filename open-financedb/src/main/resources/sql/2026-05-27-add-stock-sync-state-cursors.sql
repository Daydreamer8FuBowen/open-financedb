ALTER TABLE stock_sync_state
    ADD COLUMN cursor_time DATETIME DEFAULT NULL COMMENT 'Next required processing cursor time' AFTER latest_sync_time,
    ADD KEY idx_cursor_time (cursor_time);
