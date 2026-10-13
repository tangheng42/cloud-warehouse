# Java 后端实习简历项目描述

**云仓智配｜多仓库存与调拨协同平台**　Java 后端项目

技术栈：Java 17、Spring Boot 3.5、MyBatis、MySQL 8、Redis、RabbitMQ、JUnit 5、Vue 3。

1. 围绕多仓 SKU 设计 `physical = available + locked` 三量库存模型，覆盖入库、出库预占与释放、跨仓调拨和盘点；库存流水记录变更前后值、差值及关联单据。
2. 使用带 `available_stock >= quantity` 条件的 MySQL UPDATE 原子预占库存，结合单据行锁、状态机和事务保障动作不重复扣减；在真实 MySQL 8/InnoDB 上通过双线程同单、跨单争抢及多 SKU 整单回滚测试。
3. 为四类创建接口增加可选 `Idempotency-Key`，用用户、操作域与请求键的数据库唯一约束解决网络重试重复建单，并验证并发同键只生成一张单。
4. 对单仓单 SKU 库存详情使用 Redis Cache Aside，通过 TTL、事务提交后失效、版本键和故障回源处理缓存一致性及旧读回填，并使用真实 Redis/MySQL 联合测试验证。
5. 将预警状态变化与 Outbox 记录同事务提交，使用 RabbitMQ 异步生成可查询的站内通知；通过 Publisher Confirm/Return、Outbox 重试、死信队列和 `event_id` 唯一约束处理发送失败、异常消费与重复消息。

**面试讲述边界：** MySQL 是库存唯一真实数据源，Redis 缓存和 MQ 通知可选开启。没有空值缓存、自动死信重放或真实压测，不写 exactly once、吞吐量或性能提升百分比。逐条证据见 [简历版验收报告](云仓智配简历版最终验收报告.md)。
