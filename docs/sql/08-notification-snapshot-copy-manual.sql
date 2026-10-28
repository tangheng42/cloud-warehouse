-- MANUAL ONLY. Never mounted into application/container automatic initialization.
-- Same MySQL server only; review server/schema names before running.
-- Preconditions: old notification consumer fully stopped, no in-flight writes;
-- target notification table EMPTY; source/target charset and collation verified;
-- backups/export verified; new notification consumer still disabled.
-- This single InnoDB INSERT is atomic. No IGNORE/REPLACE/upsert: conflicts must fail visibly.
-- Copies all IDs and timestamps so paging/history remain stable.
INSERT INTO yuncang_notification_dev.notification
    (id,event_id,warehouse_id,sku_id,alert_id,type,title,content,status,created_at)
SELECT id,event_id,warehouse_id,sku_id,alert_id,type,title,content,status,created_at
FROM yuncang.notification;
-- After successful import, manually compare counts, max(id), event IDs, and every field.
-- If schemas are on different servers, export these ten columns and import into the empty target.
-- Do not re-run blindly after the new consumer has started; do not delete existing rows.
