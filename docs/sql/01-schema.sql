-- MySQL 8.0.16+；首次执行前请自行创建 yuncang 数据库并选中它。
-- 业务时间由 UTC 会话写入，JDBC 连接设置 connectionTimeZone=UTC。

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_OPERATOR')),
    KEY idx_app_user_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_warehouse_code UNIQUE (code),
    KEY idx_warehouse_enabled_created (enabled, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sku_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_sku_category_code UNIQUE (code),
    KEY idx_sku_category_enabled_created (enabled, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category_id BIGINT NOT NULL,
    unit VARCHAR(16) NOT NULL,
    safety_stock BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_sku_code UNIQUE (code),
    CONSTRAINT fk_sku_category FOREIGN KEY (category_id) REFERENCES sku_category(id),
    CONSTRAINT ck_sku_safety_stock CHECK (safety_stock >= 0),
    KEY idx_sku_category_enabled_created (category_id, enabled, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse_stock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    physical_stock BIGINT NOT NULL DEFAULT 0,
    available_stock BIGINT NOT NULL DEFAULT 0,
    locked_stock BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_warehouse_stock_warehouse_sku UNIQUE (warehouse_id, sku_id),
    CONSTRAINT fk_warehouse_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    CONSTRAINT fk_warehouse_stock_sku FOREIGN KEY (sku_id) REFERENCES sku(id),
    CONSTRAINT ck_warehouse_stock_nonnegative CHECK (physical_stock >= 0 AND available_stock >= 0 AND locked_stock >= 0),
    CONSTRAINT ck_warehouse_stock_balance CHECK (physical_stock = available_stock + locked_stock),
    KEY idx_warehouse_stock_sku (sku_id),
    KEY idx_warehouse_stock_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
