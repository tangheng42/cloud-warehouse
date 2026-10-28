# 通知服务独立数据库：阶段 1A

> 本文保留阶段 1A 的实现记录。后续已增加真实查询、独立消费者和网关，当前运行、部署与数据切换说明以 [微服务运行手册](microservices-runbook.md) 为准。

## 范围

通知服务使用 MyBatis Spring Boot Starter 和 MySQL Connector/J，版本继承父工程。没有依赖 cloud-warehouse-biz，也没有迁移查询、消费或库存业务。

独立库为 `yuncang_notification_dev`，只包含 `notification`，不包含仓储侧 `stock_alert_outbox`。

## 两种启动模式

1. 未启用 `notification-mysql`：沿用原演示接口及 Nacos 注册配置，排除数据源和 MyBatis 自动配置，不需要数据库环境变量。
2. 显式启用 `notification-mysql`：创建通知专用数据源，三个环境变量均必须提供，不默认连接原 `yuncang` 库。可以在 IDE 的程序参数中添加 `--spring.profiles.active=notification-mysql`；若需要其它 profile，用逗号合并。

需要设置的环境变量：

| 变量 | 说明 |
| --- | --- |
| NOTIFICATION_DB_URL | 通知库 JDBC URL，必须指向独立通知 schema |
| NOTIFICATION_DB_USERNAME | 仅具有通知 schema 所需权限的应用账号 |
| NOTIFICATION_DB_PASSWORD | 该账号的密码，运行时注入，不提交到 Git |
| APP_INTERNAL_TOKEN | 原内部演示接口使用的服务凭据，继续沿用 |

开发 URL 示例（仅示例，不自动连接）：

```text
jdbc:mysql://127.0.0.1:3306/yuncang_notification_dev?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

不要将 NOTIFICATION_DB_URL 设置为原仓储库 `yuncang`。本阶段配置没有执行 URL 白名单校验，实际连接前必须核对 URL 和账号权限；通过账号权限限制避免跨库访问。

配置与原项目保持 UTC 会话约定。`created_at` 是 DATETIME(6)，本身不保存时区；后续查询契约要保留原时间含义。

Spring Boot 不会自动读取普通 `.env` 文件，请通过 IDE 环境变量、进程环境或显式部署配置注入。

## 人工准备步骤（本轮不执行）

1. 审核 `docs/sql/06-notification-service-dev-schema.sql`，确认目标服务器及 schema。
2. 使用合适的管理账号人工执行建库建表脚本。脚本没有 DROP、TRUNCATE、DELETE，也不导入历史数据。
3. 单独配置通知应用账号及通知 schema 权限，不让通知应用使用仓储账号。查询阶段可从只读数据权限开始；后续消费者迁移再增加必要写权限。
4. 注入三项 NOTIFICATION_DB_* 变量，显式启用 notification-mysql。
5. 在授权的隔离环境中验证连接和表结构。成功编译或启动无数据库模式，不等于 MySQL 连接成功。

`spring.sql.init.mode=never` 禁止应用自动初始化。`CREATE TABLE IF NOT EXISTS` 不会修复已有表结构，不应作为数据库升级机制。

## 与原表一致的模型

字段：id、event_id、warehouse_id、sku_id、alert_id、type、title、content、status、created_at。

- id 为 BIGINT 自增主键。
- event_id 为 CHAR(36)，有唯一索引 uk_notification_event，保留消费去重基础。
- status 默认 UNREAD；created_at 为 DATETIME(6)，默认 CURRENT_TIMESTAMP(6)。
- 保留 idx_notification_created(created_at,id) 和 idx_notification_warehouse(warehouse_id,created_at)。
- 后续分页仍按 created_at DESC,id DESC 稳定排序。
- warehouse_id、sku_id、alert_id 仅保存业务标识，不创建跨服务外键。
- 沿用原脚本的 utf8mb4 声明，没有额外指定排序规则；真实迁移前需比较源、目标表 collation，避免唯一比较语义随服务器默认值改变。

## 验证与下一步

NotificationDatabaseModeTest 使用真实应用配置加载无数据库上下文，显式关闭 Nacos discovery，不监听 HTTP 端口，也不连接数据库。

本阶段不创建 Entity、Mapper 或分页接口。阶段 1B 再实现通知模型、Mapper、分页查询及内部契约；正式联调前需人工准备隔离通知库、账号和测试数据，并验证连接。原真实通知接口和消费者仍在 boot。
