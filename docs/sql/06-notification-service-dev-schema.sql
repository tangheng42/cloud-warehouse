-- 阶段 1A：仅供人工审核并执行；应用不会自动执行本文件。
-- 仅初始化独立通知开发库，不访问原 yuncang 库，不迁移数据。
-- IF NOT EXISTS 不能升级已存在的表；已有结构需另行核对。
CREATE DATABASE IF NOT EXISTS yuncang_notification_dev
    CHARACTER SET utf8mb4;

-- 完全限定表名，避免依赖客户端当前选中的数据库。
-- 保留原 notification 的字段、默认值、唯一键与索引，不引入跨服务外键。
CREATE TABLE IF NOT EXISTS yuncang_notification_dev.notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    alert_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_notification_event (event_id),
    KEY idx_notification_created (created_at, id),
    KEY idx_notification_warehouse (warehouse_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 阶段 1B 查询继续使用 ORDER BY created_at DESC, id DESC。
-- stock_alert_outbox 保留在仓储服务，本脚本不创建它。
