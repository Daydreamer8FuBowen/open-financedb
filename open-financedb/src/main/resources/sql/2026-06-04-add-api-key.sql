CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_name VARCHAR(64) NOT NULL COMMENT 'display name',
    api_key VARCHAR(128) NOT NULL UNIQUE COMMENT 'API key',
    is_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'admin key flag',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    expires_at DATETIME NULL COMMENT 'expiration time',
    qps_limit INT DEFAULT NULL COMMENT 'QPS limit',
    daily_quota BIGINT DEFAULT NULL COMMENT 'daily call limit',
    last_used_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_api_key_status (status),
    KEY idx_api_key_admin (is_admin, status)
);

CREATE TABLE IF NOT EXISTS api_key_model_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_key_model(api_key_id, provider, model_name),
    KEY idx_api_key_model_permission_key_id (api_key_id)
);

CREATE TABLE IF NOT EXISTS api_usage_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id BIGINT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(256) NOT NULL,
    status_code INT NOT NULL,
    latency_ms BIGINT NOT NULL,
    success TINYINT(1) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_api_usage_created_at (created_at),
    KEY idx_api_usage_key_created_at (api_key_id, created_at),
    KEY idx_api_usage_path_created_at (path, created_at)
);
