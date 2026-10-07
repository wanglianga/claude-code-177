# 城市社区净水机滤芯寿命报修换芯收费服务

面向社区公共直饮净水机的履约后端：把 **设备 → 滤芯 → 取水 → 投诉 → 报修 → 派单 → 换芯 → 复检 → 收费 → 发票**
收敛到同一条「设备履约链路（Fulfillment Case）」，把**公共饮水安全、设备维护、居民收费解释**结合起来。

技术栈：**Java 17、Spring Boot 3、Spring Data JPA、PostgreSQL、Kafka（事务发件箱 Outbox）、springdoc OpenAPI 3**。

---

## 一、它解决什么问题

- **滤芯寿命不是固定日期**：服务综合 ①设备上报寿命 ②按累计出水量折算的容量寿命 ③近期异味投诉 ④近期维护频率 ⑤实时水质（TDS/余氯/故障码），
  计算「有效剩余寿命」与换芯紧迫度；运营可按设备健康/投诉**动态调整每台设备的阈值**。
- **水质异常先保安全**：TDS/余氯超标或出现故障码 → **立即暂停售水** → 通知物业和居民（解释"为什么暂停"）→ 自动报修。
- **换芯必须真实留痕**：师傅扫码确认**旧滤芯编号、新滤芯批次、安装照片、冲洗时间**，冲洗不足禁止复检；
  **复检合格才恢复售水**，避免"设备继续出水但水质责任不清"。
- **收费可解释**：居民看到单价×取水量的扣费依据；物业可按**楼栋均摊维护费**；
  扣费失败有告警、可充值补缴；**发票抬头错误冲红重开、全程留痕**。
- **统一履约链路**：同一设备在闭环前发生的多次异常挂入同一条 Case，接口可一次性看到全过程与时间线，
  满足居民知情、物业解释、运营监管、监管抽查的不同视角。

### 覆盖的异常情形（均有自动化测试）

| # | 情形 | 系统行为 |
|---|------|---------|
| 1 | 滤芯寿命提前耗尽 | 有效寿命归零但标称容量富余 → 标记 `earlyExhausted`、预警、报修 |
| 2 | 水质投诉集中 | 24h 内达阈值（默认 3 起）→ 暂停售水、预警、报修 |
| 3 | 师傅未按时到场 | 派单 SLA（默认 24h）超时 → 工单升级 `TECHNICIAN_LATE`、催办通知 |
| 4 | 换芯后仍有异味 | 换芯 48h 内再投诉即判定；复检不合格继续停机并在同一链路重新挂单 |
| 5 | 居民账户扣费失败 | 落 FAILED 账单+预警，不吞错；充值后可单笔/一键重试，成功自动关告警并开票 |
| 6 | 物业按楼栋分摊维护费 | 总额按楼栋住户均摊（尾差并入末户），逐户扣款开票，欠费阻断链路闭环 |
| 7 | 发票抬头错误 | 原发票冲红 `VOIDED`，重开 `REISSUED` 并互相关联，保留更正轨迹 |
| 8 | 监管抽查水质记录 | 独立 `REGULATORY` 复检留痕；不合格立即停机并纳入履约链路 |

---

## 二、架构与数据流

```
净水机 ──遥测──▶ telemetry.v1 ──▶ TelemetryConsumer ──▶ TelemetryService
REST  /api/ops/telemetry ─────────┘                    │ 出水量累计 / 动态策略评估
居民扫码 ─▶ dispense.v1（取水、扣费、异味标记）          ├─ 寿命预警 / 提前耗尽
                                                      ├─ 水质异常 → 暂停售水 + 报修 + 通知
投诉聚集 / 迟到扫描 / 监管抽查 ────────────────────────┘
                              所有领域事件先与业务数据同事务写入 outbox_events
OutboxPublisher（定时）──▶ device-events.v1（设备暂停/恢复、工单、换芯、复检、收费、发票、链路闭环）
```

- **事务发件箱（Transactional Outbox）**：事件与业务数据同事务落库，再由发布器投递 Kafka，
  保证"库已提交但事件丢失"不会发生；Kafka 暂不可用时业务不受影响，事件待补发。
- **设备履约链路**：`fulfillment_cases` 为主线，设备/滤芯/工单/换芯/复检/收费/发票/投诉/预警均带 `case_id`。
  闭环三条件：**安全关**（停机过的必须复检通过）+ **费用结清** + **发票有效**，由定时对账自动闭环。
- **三端 + 公开解释接口**：
  - 居民端 `/api/residents/**`：取水、投诉、充值、补缴、收费解释、设备解释
  - 师傅端 `/api/technician/**`：我的工单、到场、扫码换芯、提交复检
  - 物业端 `/api/property/**`：小区看板、代投诉、人工报修、楼栋分摊、通知与费用解释
  - 运营端 `/api/ops/**`：遥测、设备健康/换芯策略、派单、监管抽查、发票更正、全局链路与预警
  - 公开 `/api/public/**`：居民扫码即可看"为什么暂停/何时恢复/如何收费"，无需登录

---

## 三、快速启动

### 方式 A：Docker Compose 一键启动（PostgreSQL + Kafka + 应用，推荐）

```bash
docker compose up --build
# 应用：http://localhost:8080
# Swagger UI：http://localhost:8080/swagger-ui.html
# OpenAPI JSON：http://localhost:8080/v3/api-docs
```

### 方式 B：本地无 Docker 快速体验（内存 H2，无需安装 PG/Kafka）

需要 JDK 17、Maven 3.8+：

```bash
mvn package -DskipTests
java -jar target/water-filter-service.jar --spring.profiles.active=local
```

该 profile 下事件仍可靠写入 outbox（Kafka 不可用仅产生少量连接重试日志）；
如需完整 Kafka 链路，用方式 A，或本地启动 Kafka 后使用默认 profile。

### 方式 C：本地开发（自行提供 PostgreSQL/Kafka）

```bash
# 默认读取环境变量 SPRING_DATASOURCE_URL/USERNAME/PASSWORD、KAFKA_BOOTSTRAP
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/actuator/health`

---

## 四、测试账号与鉴权方式

本服务为演示采用**请求头鉴权**（Swagger 同样在右上角「Authorize」中填请求头）：

| 头 | 取值 |
|----|------|
| `X-Role` | `RESIDENT` / `TECHNICIAN` / `PROPERTY` / `OPERATOR` |
| `X-User-Id` | 居民填账户号；师傅填数字 ID |
| `X-Community-Id` | 物业、居民按小区隔离时填写（演示小区 `1`） |

启动时自动写入种子数据（社区「阳光花园小区」，id=1）：

| 类型 | 数据 |
|------|------|
| 设备 | `WQ-1001`（3号楼大堂）、`WQ-1002`（5号楼架空层），初始滤芯 `FLT-OLD-3-0001`、`FLT-OLD-5-0001` |
| 师傅 | id=1 王师傅、id=2 李师傅 |
| 居民账户 | `A3-101` 张三 余额50；`A3-102` 李四 余额2；`A5-201` 王五 余额30 |
| 水质默认限值 | TDS ≤ 100 mg/L，余氯 ≤ 2.0 mg/L；水价 0.30 元/升；寿命预警阈值 20% |

---

## 五、本题验证方式

### 5.1 自动化测试（已实际执行，21 个全部通过）

```bash
mvn test
```

- `ServiceFlowIntegrationTest`（10 个）：水质停机/迟到/扫码换芯/复检恢复闭环、寿命预警、提前耗尽、
  扣费失败补缴、投诉聚集、换芯后异味+复检失败、楼栋分摊+欠费+发票更正、监管抽查、动态阈值、同设备异常同链路。
- `WebApiIntegrationTest`（10 个）：三端鉴权、公开解释、取水成功/失败、运营遥测触发预警、物业看板、Swagger 可访问。
- `KafkaEventFlowIntegrationTest`（1 个，EmbeddedKafka）：发遥测消息到 `telemetry.v1`
  → 消费后停机 → outbox 事件可靠投递到 `device-events.v1`。

### 5.2 手工端到端验证（curl）

```bash
B=http://localhost:8080
# 1) 居民扫码：设备正常、按 0.30 元/升收费
curl -s $B/api/public/devices/WQ-1001/explain
# 2) 设备上报异常水质（TDS 180 + 故障码 E21）
curl -s -X POST $B/api/ops/telemetry -H "X-Role: OPERATOR" -H "X-User-Id: ops" -H "Content-Type: application/json" \
  -d '{"deviceCode":"WQ-1001","filterLifePercent":80,"waterOutputLiters":5.0,"tds":180.0,"chlorine":0.6,"flowRate":2.0,"faultCode":"E21"}'
# 3) 居民看到"为什么暂停"（状态 SUSPENDED、原因、恢复条件、工单号）
curl -s $B/api/public/devices/WQ-1001/explain
# 4) 暂停期间取水被拒（HTTP 409）
curl -i -X POST $B/api/residents/dispenses -H "X-Role: RESIDENT" -H "X-User-Id: A3-101" \
  -H "Content-Type: application/json" -d '{"deviceCode":"WQ-1001","accountNo":"A3-101","liters":2.0}'
# 5) 运营派单 → 6) 师傅到场 → 7) 扫码换芯（旧芯+新批次+照片+冲洗15分钟）
T=<上一步返回的 latestTicketNo>
curl -s -X POST $B/api/ops/tickets/$T/assign -H "X-Role: OPERATOR" -H "X-User-Id: ops" \
  -H "Content-Type: application/json" -d '{"technicianId":1}'
curl -s -X POST $B/api/technician/tickets/$T/arrive -H "X-Role: TECHNICIAN" -H "X-User-Id: 1" \
  -H "Content-Type: application/json" -d '{"note":"到场"}'
curl -s -X POST $B/api/technician/replacements -H "X-Role: TECHNICIAN" -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"ticketNo":"'$T'","oldFilterSerialNo":"FLT-OLD-3-0001","newFilterSerialNo":"FLT-NEW-1","newFilterBatchNo":"BATCH-2026-10","installPhotoUrl":"https://oss.example.com/new.jpg","flushMinutes":15,"newFilterCapacityLiters":12000.0}'
# 8) 复检合格 → 自动恢复售水
curl -s -X POST $B/api/technician/retests -H "X-Role: TECHNICIAN" -H "X-User-Id: 1" \
  -H "Content-Type: application/json" -d '{"ticketNo":"'$T'","tds":35.0,"chlorine":0.4,"flowRate":2.0,"note":"合格"}'
# 9) 查看整条履约链路（时间线含预警/报修/换芯/复检/闭环，证明换芯真实完成）
curl -s $B/api/ops/devices/WQ-1001/cases/latest -H "X-Role: OPERATOR" -H "X-User-Id: ops"
# 10) 收费解释、发票冲红重开、监管抽查、楼栋分摊
curl -s "$B/api/residents/charges/<chargeNo>/explain" -H "X-Role: RESIDENT" -H "X-User-Id: A3-101"
curl -s -X POST $B/api/ops/invoices/correct-title -H "X-Role: OPERATOR" -H "X-User-Id: ops" \
  -H "Content-Type: application/json" -d '{"invoiceNo":"<invoiceNo>","newTitle":"正确抬头","newTaxNo":"91370000XXXXXXXXX1"}'
curl -s -X POST $B/api/ops/regulatory-audits -H "X-Role: OPERATOR" -H "X-User-Id: ops" \
  -H "Content-Type: application/json" -d '{"deviceCode":"WQ-1002","tds":120.0,"chlorine":0.4,"flowRate":2.0,"note":"抽查","inspector":"监管员-赵"}'
curl -s -X POST $B/api/property/maintenance-share -H "X-Role: PROPERTY" -H "X-User-Id: pm" -H "X-Community-Id: 1" \
  -H "Content-Type: application/json" -d '{"deviceCode":"WQ-1001","totalAmount":10.00,"reason":"换芯维护费"}'
```

### 5.3 已实际执行的验证结论

- `mvn test`：**21/21 通过**（含 EmbeddedKafka 事件链路）。
- 本地以 `--spring.profiles.active=local` 实际启动：`/actuator/health` 200；
  手工跑通「异常遥测 → 暂停 → 拒绝取水 → 派单 → 到场 → 扫码换芯 → 复检合格 → 恢复售水 → 链路 CLOSED → 取水扣费」全流程；
  `/swagger-ui/index.html` 200，`/v3/api-docs` 返回 OpenAPI 3.0.1（40 个路径）。
- 未在本机执行 `docker compose up`（环境无 Docker daemon）；`Dockerfile` 采用多阶段 Maven 构建，
  `docker-compose.yml` 已通过 YAML 解析校验并配置 PG/Kafka 健康检查与启动依赖。

---

## 六、关键业务规则

- **暂停**只由水质异常、投诉聚集、换芯后异味、监管抽查不合格触发；**恢复只由复检合格触发**，无人工绕过入口。
- 换芯校验：工单必须指给当前师傅且已到场；旧芯扫码编号必须与设备当前在用滤芯一致；
  新芯编号不能重复安装；冲洗时间不足（默认 < 10 分钟）直接 400。
- 复检判定：`TDS ≤ 设备限值 && 余氯 ≤ 设备限值 && 流量 > 0`。失败时设备继续 SUSPENDED，
  本次工单留痕完成、同一 Case 下自动生成复查工单并进入待复检。
- 寿命预警（不停机）与提前耗尽（按策略判定）分别处理；遥测上报的出水量统一累计到滤芯与设备，避免与按次取水重复计算。
- 闭环由定时对账完成；发生过停机的链路必须 `retestPassed=true`，且无 FAILED 收费、每张成功收费都有有效发票。
  已闭环链路若事后产生楼栋分摊欠费会自动重新打开，结清后再闭环。

## 七、主要配置项（`application.yml`，均可经环境变量覆盖）

| 配置 | 默认 | 说明 |
|------|------|------|
| `app.defaults.life-threshold-percent` | 20 | 寿命预警阈值，运营可按设备覆盖 |
| `app.defaults.tds-limit` / `chlorine-limit` | 100 / 2.0 | 水质限值，可按设备覆盖 |
| `app.defaults.price-per-liter` | 0.30 | 取水单价，可按设备覆盖 |
| `app.defaults.arrival-sla-hours` | 24 | 派单到场时限 |
| `app.defaults.complaint-window-hours` / `complaint-cluster-size` | 24 / 3 | 投诉聚集窗口与阈值 |
| `app.defaults.flush-min-minutes` | 10 | 换芯最低冲洗时间 |
| `SPRING_DATASOURCE_URL` 等 | localhost:5432/water | PostgreSQL 连接 |
| `KAFKA_BOOTSTRAP` | localhost:9092 | Kafka 地址 |
