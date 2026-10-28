CREATE TABLE notification (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 event_id CHAR(36) NOT NULL UNIQUE,
 warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 alert_id BIGINT NOT NULL,
 type VARCHAR(32) NOT NULL,
 title VARCHAR(100) NOT NULL,
 content VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notification_created ON notification(created_at,id);
CREATE INDEX idx_notification_warehouse ON notification(warehouse_id,created_at);
