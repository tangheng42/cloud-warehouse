# Docker 独立联调验收记录

验收日期：2026-10-21（Asia/Shanghai）。

## 环境与隔离

用户确认新建独立 Docker 测试环境后，本轮初始化并启动 Compose 项目 yuncang-lab。
原本机 MySQL 3306（进程 6744）、Redis 6379（进程 5444）保持原状，未连接原业务库。

新建的八个容器均通过健康检查：
- warehouse-db：MySQL 8.4.11，仓储测试库 yuncang_lab。
- notification-db：单独的 MySQL 容器，通知库 yuncang_notification_dev。
- redis、rabbitmq、nacos。
- warehouse、notification、gateway。

仅网关映射本机 127.0.0.1:18080。两个 MySQL、Redis、RabbitMQ、Nacos、仓储服务、通知服务没有发布宿主机端口。
Docker 列表中的 3306/tcp 等是容器内部端口，不代表占用了本机 3306。

仓储账号 warehouse_app、通知账号 notification_app 分别仅获对应数据库权限。
数据库、Redis、RabbitMQ 和内部调用凭据随机生成，保存在 Git 忽略的 .local/microservices-lab.env。
本地 SQL 演示管理员仍为 admin / 123456，限本套隔离演示环境使用。
未复制或迁移旧 notification 数据。测试数据均来自新容器初始化脚本和本轮通过公开 API 创建的测试单据。

Nacos 采用 v2.5.1 单机内置存储模式，镜像标签参照 [官方 Docker 示例](https://github.com/nacos-group/nacos-docker/blob/master/README.md)。
本实验网络不发布 Nacos 端口，客户端鉴权关闭；这不是生产环境的部署配置。

## 本轮新增文件

- compose.lab.yaml：完整独立联调环境，不合并旧 Compose 配置。
- deploy/microservices/Start-Lab.ps1：生成本地凭据、校验配置、拉取基础设施镜像并启动。
- deploy/microservices/Test-Lab.ps1：校验项目标签与网关端口后，通过公开 API 创建测试数据并执行验收。
- .gitignore：忽略 .local/，防止本地凭据进入 Git。

## 已通过：14 项可重复的业务检查

Test-Lab.ps1 最后一次执行结果为 14 项全部通过，详情见本地 .local/lab-smoke-result.json：
1. 网关健康；通知查询未登录返回 401。
2. 网关不暴露 /internal/v1/notifications/page，返回 404。
3. 通过网关和 Nacos 发现链路完成登录。
4. 通知 page=0 返回 400。
5. 真实 MySQL 入库确认完成。
6. 低库存事件通过 Outbox、RabbitMQ 到达独立通知库，再经 Feign 返回原公开分页接口。
7. 补货后生成独立的 STOCK_RECOVERED 通知。
8. 同一 Idempotency-Key 创建出库单返回相同单据 ID。
9. 预占后 physical=15、available=7、locked=8。
10. 确认出库后 physical=7、available=7、locked=0。
11. 原通知响应仍为 ApiResponse 包含分页数据。
12. 两行 SKU 整单预占时，第二行不足返回 409 / INSUFFICIENT_AVAILABLE_STOCK。
13. 此失败事务同时回滚第一行库存流水与已生成的库存预警 Outbox 事件。
14. 第一行库存恢复为 available=10、locked=0，单据仍为 CREATED，之后通过取消接口结束测试单。

测试脚本每次生成唯一业务编码，不清库、不删除旧测试记录。

## 额外真实环境检查

- Nacos 服务列表包含 yuncang-gateway、cloud-warehouse、yuncang-notification。
- 新仓储库没有 notification 表，通知查询仍成功，验证查询实际来自通知服务。
- 检查时仓储 Outbox 共 6 条，均为 SENT；独立通知表 6 条，COUNT(DISTINCT event_id)=6。
- 通知表使用 InnoDB。
- 通知数据库账号权限仅限 yuncang_notification_dev，没有仓储库授权。
- 实际 Redis 认证连接返回 PONG；未对库存缓存性能或命中率作结论。
- 读取一条真实 Outbox 事件，通过 RabbitMQ 管理 API 向原通知交换机重复投递两次，routed=true。
  消费完成后，该 event_id 在通知表中仍恰好 1 条。
- 发送一条故意损坏的 JSON 测试消息，重试结束后进入死信队列。
  检查时主队列 ready=0、unacknowledged=0、consumers=1；DLQ ready=1。
  这一条死信是本轮有意保留的失败样本，不是正常通知投递失败。
- 从内部网络无凭据访问真实通知分页接口，返回 401。

## 尚未完成的验证

自动审批拒绝了临时停止并重启通知容器的故障演练命令，只返回 blocked by policy，未提供更具体原因。
该命令未执行；因此本轮不能声称已验证实际停机后的 HTTP 503 和服务恢复。
此前模拟远程失败的自动化测试已通过，但不能代替这个容器故障场景。

本轮也未执行旧库历史数据迁移、旧环境流量切换、全部 MySQL 并发验收、真实数据库断连重试，
以及管理员/仓库管理员/操作员完整角色矩阵的真实环境验证。角色控制已有隔离自动化测试记录。

## 复查与继续使用

仓库根目录为 D:\java项目\云仓智配\cloud-warehouse。

查看环境：
```powershell
docker compose --project-name yuncang-lab --env-file .local/microservices-lab.env -f compose.lab.yaml ps
```

已有镜像和本地凭据时恢复启动（保留数据，不重新建库）：
```powershell
docker compose --project-name yuncang-lab --env-file .local/microservices-lab.env -f compose.lab.yaml up -d --pull never --wait
```

再次运行验收（需要 PowerShell 7）：
```powershell
./deploy/microservices/Test-Lab.ps1
```

健康地址：http://127.0.0.1:18080/actuator/health。
网关是 API 入口，根路径不是前端页面。
如本地启动现有前端，将 VITE_PROXY_TARGET 设置为 http://127.0.0.1:18080，再运行前端开发命令。

不要删除 .local/microservices-lab.env 后重新生成密码来配合旧数据卷，这会导致账号凭据不一致。
不要将本套环境改为连接原业务数据库。现有服务镜像中无需写入数据库密码。
