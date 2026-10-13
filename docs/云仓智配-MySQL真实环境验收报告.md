# 云仓智配 MySQL 真实环境验收报告

验收日期：2026-09-29。本轮只验证 MySQL 8/InnoDB 的库存事务与并发，不变更 V1 OpenAPI，不接入 Redis、RabbitMQ 或其他基础设施。验收前阅读了 OpenAPI、V1 设计、正式 SQL、库存相关 Service/Mapper/XML 和已有测试。本轮没有发现需要修改业务实现的正确性 Bug。

## 1. 测试环境

| 项目 | 实测值 |
|---|---|
| MySQL 版本 | MySQL Community Server 8.0.45 |
| 表引擎 | 15/15 张表均为 InnoDB |
| Spring Boot 版本 | 3.5.16 |
| Java 版本 | 17.0.18 |
| 事务隔离级别 | REPEATABLE-READ |
| 数据库 | 独立临时库 `yuncang_acceptance_20260929_*`；测试结束后删除 |
| 初始化 | 依次执行正式 `01-schema.sql`、`03-v1-business-schema.sql`、`02-demo-data.sql` |
| 测试调用 | Spring Bean Service/真实 MyBatis XML/真实 JDBC；测试方法本身无 `@Transactional`，每个业务动作独立提交 |
| Redis/MQ | 禁用 Redis 缓存；RabbitMQ 未使用 |

`MySqlInnoDbAcceptanceTest` 在运行前检查数据库名必须为 `yuncang_acceptance_*`，MySQL 主版本必须为 8，且 15 张表必须全部是 InnoDB。验收脚本每次生成独立库并在 `finally` 中删除；测试用例通过随机业务编码相互隔离，没有清理或改写开发库。

## 2. 测试总览

| 范围 | 测试数量 | PASS | FAIL | BLOCKED |
|---|---:|---:|---:|---:|
| 本轮真实 MySQL 专项 | 13 | 13 | 0 | 0 |
| 完整 Maven 回归（含原有测试） | 28 | 28 | 0 | 0 |

真实 MySQL 专项通过人工建库运行、自动建库并清理脚本运行，以及完整回归运行。最终命令为 `mvn clean verify -q`，当次提供 `MYSQL_TEST_URL` 等变量，28 个测试全部执行且无跳过。专项脚本运行结果：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`。现有 H2 测试仍保留作普通回归，不被用作本报告的 InnoDB 并发证据。

### 库存修改动作清单

| Service 动作 | 库存三量变化（physical / available / locked） | 流水 |
|---|---|---|
| `InboundService.confirm` | +q / +q / 0 | INBOUND |
| `OutboundService.reserve` | 0 / -q / +q | OUTBOUND_LOCK |
| `OutboundService.release` | 0 / +q / -q | OUTBOUND_RELEASE |
| `OutboundService.confirm` | -q / 0 / -q | OUTBOUND |
| `OutboundService.cancel` | CREATED：不变；RESERVED：0 / +q / -q | RESERVED 时 OUTBOUND_RELEASE |
| `TransferService.reserve` | 源仓 0 / -q / +q | TRANSFER_LOCK |
| `TransferService.release` | 源仓 0 / +q / -q | TRANSFER_RELEASE |
| `TransferService.dispatch` | 源仓 -q / 0 / -q | TRANSFER_OUT |
| `TransferService.receive` | 目标仓 +q / +q / 0 | TRANSFER_IN |
| `TransferService.cancel` | CREATED：不变；RESERVED：源仓 0 / +q / -q | RESERVED 时 TRANSFER_RELEASE |
| `StocktakeService.confirm` | delta / delta / 0；delta=0 时不变 | delta 非零时 STOCKTAKE_ADJUST |

## 3. 入库

| 场景 | 结果 | 数据库最终状态 |
|---|---|---|
| 无库存行首次 confirm 10 | PASS | 仅一条库存行；10/10/0；订单 COMPLETED；一条 INBOUND；安全库存 15 时预警 ACTIVE |
| 重复 confirm | PASS | 第二次 `ORDER_STATE_CONFLICT`；仍 10/10/0；INBOUND 数量仍为 1 |
| 两张单并发首次建行，分别 +10/+20 | PASS | 两单均 COMPLETED；仓+SKU 库存行仅 1 条；30/30/0；各有一条 INBOUND |
| 不经过仓库业务行锁，两个独立事务直接对缺失库存行并发 UPSERT | PASS | 同一仓+SKU 仅 1 条；30/30/0，无丢失更新 |

最后一项是 Mapper 原子性专项，不代表完整入库业务；完整业务由上一项验证。业务 Service 会先锁仓库行，两个同仓入库动作可能在进入 UPSERT 前串行，因此保留直接 UPSERT 竞争测试作为唯一键和 SQL 行为的独立证据。

## 4. 出库

| 场景 | 结果 | 数据库最终状态 |
|---|---|---|
| 100/100/0 预占 30 | PASS | 100/70/30，RESERVED，OUTBOUND_LOCK，预警由 RESOLVED 转 ACTIVE |
| release | PASS | 100/100/0，CREATED，OUTBOUND_RELEASE，预警回 RESOLVED |
| 再 reserve 后 confirm | PASS | 70/70/0，COMPLETED，第二条 OUTBOUND_LOCK 与 OUTBOUND 各一条 |
| 重复 confirm | PASS | `ORDER_STATE_CONFLICT`；库存和流水数不变 |
| CREATED cancel | PASS | CANCELLED，库存不变，无 OUTBOUND_RELEASE |
| RESERVED cancel | PASS | CANCELLED，锁定量全部释放，OUTBOUND_RELEASE 一条；重复 cancel 无副作用 |
| 同单双线程 reserve | PASS | 1 成功、1 `ORDER_STATE_CONFLICT`；100/70/30；仅一条 OUTBOUND_LOCK |
| 两单各抢 7，初始 10 | PASS | 1 成功、1 `INSUFFICIENT_AVAILABLE_STOCK`；10/3/7；两单分别 RESERVED/CREATED；合计仅一条锁定流水 |
| 两事务直接并发条件 UPDATE，各抢 7 | PASS | 返回影响行数分别为 1 和 0；最终 10/3/7 |
| 三 SKU 第三行不足 | PASS | SKU1、SKU2 仍 100/100/0，SKU3 仍 5/5/0；订单仍 CREATED；无残留 OUTBOUND_LOCK；前两项预警仍 RESOLVED |

直接条件 UPDATE 专项绕过单据及预警服务，只验证数据库原子抢占。完整业务测试另行核对订单、流水和预警。

## 5. 调拨

| 场景 | 结果 | 数据库最终状态 |
|---|---|---|
| 源仓 100/100/0，目标仓无行，reserve 30 | PASS | 源仓 100/70/30；RESERVED；TRANSFER_LOCK |
| dispatch | PASS | 源仓 70/70/0；IN_TRANSIT；TRANSFER_OUT；目标仓仍无库存 |
| receive | PASS | 目标仓首次创建一行 30/30/0；COMPLETED；TRANSFER_IN；两仓 physical 合计仍为 100 |
| 重复 reserve/dispatch/receive | PASS | 各返回 `ORDER_STATE_CONFLICT`；库存及各阶段流水无重复 |
| RESERVED release | PASS | 源仓恢复 100/100/0；CREATED；TRANSFER_RELEASE；重复 release 冲突 |
| CREATED cancel | PASS | CANCELLED；库存不变；无流水 |
| RESERVED cancel | PASS | CANCELLED；源仓恢复 100/100/0；有 TRANSFER_RELEASE；重复 cancel 冲突 |
| IN_TRANSIT cancel | PASS | `ORDER_STATE_CONFLICT`；在途库存不被释放 |

目标仓 safety stock 为 80、收货 30 时预警 ACTIVE；源仓 reserve 后 available=70 时 ACTIVE。

## 6. 盘点

| 场景 | 结果 | 数据库最终状态 |
|---|---|---|
| 快照 100/70/30，countedPhysical=95 | PASS | 快照三项正确保存；确认后 95/65/30；一条 STOCKTAKE_ADJUST；预警 ACTIVE |
| delta=0 | PASS | 状态 COMPLETED，库存不变，不产生调整流水 |
| 建单后其他盘点将库存改为 90/60/30，再确认旧单 | PASS | 旧单返回 `STOCK_SNAPSHOT_CHANGED`，仍 DRAFT；库存保持 90/60/30；无旧单流水 |
| locked=30，countedPhysical=20 | PASS | `COUNT_BELOW_LOCKED`；订单仍 DRAFT，库存、流水、预警不变 |

## 7. 库存不变量检查

每个专项测试后都查询整个验收库，验证三量非负和 `physical = available + locked`。各关键动作后还断言对应仓+SKU 的具体三量。完整回归结束后直接查询 MySQL，违反不变量的库存行数量为 **0**。正式 SQL 还定义了非负与三量平衡 CHECK 约束；测试没有仅依赖 CHECK 判断成功。

## 8. stock_flow 一致性检查

每个被测单据按预期校验 `bizType`、`bizId`、`flowType`、`operatorId`、仓库及 SKU 引用。对每条流水验证三项 `after = before + delta`，以及 before/after 两组 `physical = available + locked`。每个测试结束后再次查询库中全部流水；完整回归后的错误流水数量为 **0**。合法的 reserve → release → reserve 可产生两条 OUTBOUND_LOCK，此时以动作次数判断，不误用业务类型唯一键。

## 9. stock_alert 一致性检查

入库首次建行、出库预占和释放、调拨源仓预占与目标仓收货、盘点调整后均检查预警状态及 available/safety 数值。第三个 SKU 失败时，前两个 SKU 已在事务中发生过预警更新，但业务异常使其同库存和流水一起回滚。每个测试结束后，全库核对 `available < safety` 与 ACTIVE/RESOLVED；完整回归后的不一致预警数量为 **0**。

## 10. 并发测试结果

| 初始状态 | 同时执行 | 成功 / 失败 | 最终数据库状态 |
|---|---|---|---|
| 无仓+SKU 库存行 | 两张入库单分别 +10/+20 | 2 / 0 | 1 条库存行，30/30/0；两单 COMPLETED、各一条 INBOUND |
| 无仓+SKU 库存行 | 两个事务直接 UPSERT +10/+20 | 2 / 0 | 1 条库存行，30/30/0 |
| 同一出库单 CREATED，库存 100/100/0 | 两线程 reserve 30 | 1 / 1 状态冲突 | 单据 RESERVED，100/70/30，仅一条 OUTBOUND_LOCK |
| 两张不同出库单，各需 7，库存 10/10/0 | 两线程 reserve | 1 / 1 库存不足 | 一单 RESERVED、一单 CREATED，10/3/7，合计一条 OUTBOUND_LOCK |
| 库存 10/10/0 | 两事务直接运行条件 UPDATE 各扣 7 | 影响行数 1 / 0 | 10/3/7，没有负数 |

所有并发案例使用固定大小线程池、`CountDownLatch` 同步起跑和独立事务；并非顺序调用后称为并发。Service 会先执行仓库行 `FOR UPDATE`，所以同仓业务动作被部分串行化；Mapper 级测试单独证明条件 UPDATE/UPSERT 在直接竞争下的结果。测试证明这些受控竞争场景正确，不等同于长期压力测试或死锁绝不会发生。

## 11. 发现的 Bug

本轮真实 MySQL 测试 **未复现业务正确性 Bug**，因此没有修改生产 Service、Mapper 或 XML。验收前已有 H2 并发测试不足以证明 InnoDB 行锁与唯一键行为；本轮通过新增 MySQL 专项测试解决的是**验证证据不足**，不是修改库存算法。

## 12. 事务设计结论

`BusinessException extends RuntimeException`。库存动作位于 Spring Bean 的公开 `@Transactional` 方法中；出库和调拨的 `cancel → release` 虽是同类内部调用，不会再经过 release 的代理，但外层 cancel 自身已有事务，实测 RESERVED cancel 的库存、流水、状态保持原子。三 SKU 第三行失败时，库存、订单状态、流水、预警均回滚。未发现吞掉业务异常导致部分提交的路径。

## 13. 并发设计结论

单据 `FOR UPDATE` 与状态条件更新防止同单重复动作；库存条件 UPDATE 防止预占扣成负数；仓+SKU 唯一约束和 UPSERT 支持首次行并发创建。所有多 SKU 的库存动作按 SKU ID 升序处理。当前同仓仓库行锁会降低并行度，但对本轮单体项目是保守且正确的并发保护；只有出现实际性能问题，才考虑缩小锁范围。

## 14. 当前仍存在的风险

| 等级 | 风险 |
|---|---|
| 高 | 本轮已测试的业务路径没有发现高风险正确性问题。 |
| 中 | 并发测试为受控双线程场景，没有长时间压力、死锁注入或不同锁顺序下的组合测试。 |
| 中 | 同仓业务动作先锁仓库行，吞吐可能受限；尚无性能测量，不宣称性能瓶颈已出现。 |
| 低 | 多次重复执行专项通过，但未在其他 MySQL 8 小版本或不同事务隔离级别上验证。 |

## 15. 本轮修改文件

仅新增 MySQL 验收专用文件：`cloud-warehouse-boot/src/test/resources/application-mysql-acceptance.yml`、`cloud-warehouse-boot/src/test/java/com/yuncang/MySqlInnoDbAcceptanceTest.java`、`scripts/run-mysql-acceptance.ps1` 及本报告。生产代码、OpenAPI、Redis、RabbitMQ、README 和简历材料未改动。

## 16. 自动化测试文件

`MySqlInnoDbAcceptanceTest` 共 13 项，包含真实 Service 并发和直接 Mapper 原子竞争。运行方式（Windows PowerShell）：

```powershell
$env:MYSQL_EXE = 'D:\study\mysql\bin\mysql.exe' # 或本机 mysql.exe 的路径
$env:MYSQL_ACCEPTANCE_PASSWORD = '<本地 MySQL 密码>'
.\scripts\run-mysql-acceptance.ps1
```

脚本使用当前正式 SQL 建立独立随机数据库，执行专项测试，然后自动删除该库。需要具备 CREATE/DROP DATABASE 权限；不要把密码写入仓库。已有普通 H2 测试文件未被改动。

## 17. Maven 测试最终结果

专项：`13 PASS / 0 FAIL / 0 BLOCKED`。完整 Maven 回归：`28 PASS / 0 FAIL / 0 BLOCKED`，`mvn clean verify -q` 退出码 0。全量回归时明确提供隔离库 JDBC 环境变量，使本轮 MySQL 类实际运行而非被跳过；验收库随后删除。未进行 Redis、RabbitMQ、请求级幂等或接口扩展工作。
