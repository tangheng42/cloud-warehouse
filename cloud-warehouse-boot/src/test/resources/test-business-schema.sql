CREATE TABLE inbound_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE,
  warehouse_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL, remark VARCHAR(500), created_by BIGINT NOT NULL,
  confirmed_by BIGINT, confirmed_at TIMESTAMP, cancelled_by BIGINT, cancelled_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE inbound_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE (order_id, sku_id)
);
CREATE TABLE outbound_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE,
  warehouse_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL, remark VARCHAR(500), created_by BIGINT NOT NULL,
  reserved_by BIGINT, reserved_at TIMESTAMP, released_by BIGINT, released_at TIMESTAMP,
  confirmed_by BIGINT, confirmed_at TIMESTAMP, cancelled_by BIGINT, cancelled_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE outbound_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE (order_id, sku_id)
);
CREATE TABLE transfer_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE,
  source_warehouse_id BIGINT NOT NULL, target_warehouse_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL, remark VARCHAR(500), created_by BIGINT NOT NULL,
  reserved_by BIGINT, reserved_at TIMESTAMP, released_by BIGINT, released_at TIMESTAMP,
  dispatched_by BIGINT, dispatched_at TIMESTAMP, received_by BIGINT, received_at TIMESTAMP,
  cancelled_by BIGINT, cancelled_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE transfer_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE (order_id, sku_id)
);
CREATE TABLE stocktake_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE,
  warehouse_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL, remark VARCHAR(500), created_by BIGINT NOT NULL,
  confirmed_by BIGINT, confirmed_at TIMESTAMP, cancelled_by BIGINT, cancelled_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE stocktake_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  snapshot_physical BIGINT NOT NULL, snapshot_available BIGINT NOT NULL,
  snapshot_locked BIGINT NOT NULL, counted_physical BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (order_id, sku_id)
);
CREATE TABLE stock_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, warehouse_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  biz_type VARCHAR(16) NOT NULL, biz_id BIGINT NOT NULL, flow_type VARCHAR(32) NOT NULL,
  delta_physical BIGINT NOT NULL, delta_available BIGINT NOT NULL, delta_locked BIGINT NOT NULL,
  before_physical BIGINT NOT NULL, before_available BIGINT NOT NULL, before_locked BIGINT NOT NULL,
  after_physical BIGINT NOT NULL, after_available BIGINT NOT NULL, after_locked BIGINT NOT NULL,
  operator_id BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE stock_alert (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, warehouse_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  available_stock BIGINT NOT NULL, safety_stock BIGINT NOT NULL, status VARCHAR(16) NOT NULL,
  triggered_at TIMESTAMP NOT NULL, resolved_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (warehouse_id, sku_id)
);
