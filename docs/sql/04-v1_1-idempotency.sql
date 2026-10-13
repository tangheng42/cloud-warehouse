-- Optional V1.1 create-request idempotency. Apply after the V1 schema.
CREATE TABLE IF NOT EXISTS create_request_idempotency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idem_key VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    biz_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_create_request (user_id, operation, idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
