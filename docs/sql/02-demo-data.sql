-- 仅供本地演示。执行前请先运行 01-schema.sql。
-- 演示账号 admin / 123456；密码以 BCrypt 存储，请勿用于生产环境。
INSERT INTO app_user (username, password_hash, role, enabled)
VALUES ('admin', '$2a$10$blvOKQP.YX9hZsoqJTZlOeeJt05B3OgD1PX5ahs2WJpCyJeF/UiN.', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

INSERT INTO warehouse (code, name, address) VALUES
('WH-SH', '上海仓', '上海市'), ('WH-NJ', '南京仓', '南京市'), ('WH-HZ', '杭州仓', '杭州市')
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO sku_category (code, name) VALUES
('CAT-FOOD', '食品'), ('CAT-DAILY', '日用品'), ('CAT-DIGITAL', '数码配件')
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO sku (code, name, category_id, unit, safety_stock)
SELECT 'SKU-RICE', '大米', id, '袋', 20 FROM sku_category WHERE code = 'CAT-FOOD'
ON DUPLICATE KEY UPDATE code = VALUES(code);
INSERT INTO sku (code, name, category_id, unit, safety_stock)
SELECT 'SKU-NOODLE', '面条', id, '箱', 10 FROM sku_category WHERE code = 'CAT-FOOD'
ON DUPLICATE KEY UPDATE code = VALUES(code);
INSERT INTO sku (code, name, category_id, unit, safety_stock)
SELECT 'SKU-TISSUE', '纸巾', id, '箱', 15 FROM sku_category WHERE code = 'CAT-DAILY'
ON DUPLICATE KEY UPDATE code = VALUES(code);
INSERT INTO sku (code, name, category_id, unit, safety_stock)
SELECT 'SKU-CABLE', '数据线', id, '件', 8 FROM sku_category WHERE code = 'CAT-DIGITAL'
ON DUPLICATE KEY UPDATE code = VALUES(code);
INSERT INTO sku (code, name, category_id, unit, safety_stock)
SELECT 'SKU-CHARGER', '充电器', id, '件', 5 FROM sku_category WHERE code = 'CAT-DIGITAL'
ON DUPLICATE KEY UPDATE code = VALUES(code);

-- 演示用期初库存；正式业务由入库确认/调拨收货创建库存行，不运行此段数据。
INSERT INTO warehouse_stock (warehouse_id, sku_id, physical_stock, available_stock, locked_stock)
SELECT w.id, s.id, 100, 100, 0 FROM warehouse w JOIN sku s
WHERE w.code = 'WH-SH' AND s.code = 'SKU-RICE'
ON DUPLICATE KEY UPDATE warehouse_id = VALUES(warehouse_id);
INSERT INTO warehouse_stock (warehouse_id, sku_id, physical_stock, available_stock, locked_stock)
SELECT w.id, s.id, 12, 12, 0 FROM warehouse w JOIN sku s
WHERE w.code = 'WH-NJ' AND s.code = 'SKU-TISSUE'
ON DUPLICATE KEY UPDATE warehouse_id = VALUES(warehouse_id);
