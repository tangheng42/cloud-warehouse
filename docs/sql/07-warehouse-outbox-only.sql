-- Apply after V1 schema when asynchronous low-stock notification is enabled.
CREATE TABLE IF NOT EXISTS stock_alert_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) NULL,
    UNIQUE KEY uk_stock_alert_outbox_event (event_id),
    KEY idx_stock_alert_outbox_due (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
