# 城市社区净水机滤芯寿命报修换芯收费服务

面向城市社区公共净水机的后端服务：设备持续上报遥测，居民扫码取水扣费，滤芯寿命动态评估，
预警自动派单，师傅换芯履约，维护费按楼栋分摊，发票开具与更正，监管抽查留痕。
设备、滤芯、取水、报修、换芯、收费、发票、复检全部沉淀在**同一设备履约链路**中。

- 技术栈：Java 17 · Spring Boot 3.2 · Spring Data JPA · PostgreSQL 15 · Kafka（KRaft）· springdoc-openapi（Swagger UI）
- 项目类型：**后端服务**（无前端页面），提供 OpenAPI 3.x 文档与 Swagger UI

## 核心业务规则

**滤芯寿命并非固定日期**，由以下因素动态决定（`FilterLifeService`）：

| 因素 | 规则 |
|---|---|
| 取水量 | 有效消耗 = 出水量增量 × 水质压力系数 |
| 水质 TDS | TDS 每高于基准 50，消耗加速 25%（封顶 2 倍） |
| 投诉 | 近 24 小时每起投诉额外扣 2% 寿命 |
| 维护频率 | 超过 90 天未维护，每 30 天额外扣 3% |

**自动联动**：

- 滤芯寿命 ≤ 15% → 预警 + 自动创建换芯工单并派单；≤ 1% → 滤芯耗尽、暂停售水
- TDS > 100 或余氯超出 0.05–2.0 → 暂停售水 + 通知物业 + 复检工单
- 24 小时内投诉 ≥ 3 起 → 投诉聚集预警 + 暂停售水 + 通知物业
- 师傅 4 小时未到场 → 未到场预警 + 自动改派其他师傅（调度器每 30 秒巡检）
- 换芯后 7 天内异味投诉 → 专项预警 + **免费**复检工单
- 换芯完成（复检合格）→ 恢复售水 + 维护费 120 元按楼栋居民均摊扣费
- 余额不足 → 扣费失败独立留痕（不因取水事务回滚而丢失）+ 通知居民

**换芯防作假**：师傅必须扫码确认旧滤芯编号（与设备在用滤芯一致）、新滤芯批次（编号不得复用）、
安装照片、冲洗时间（≥10 分钟）、复检 TDS/余氯，全部合格才能完成履约。

## 一键启动（Docker Compose）

```bash
cd water-purifier-service
docker compose up --build
```

启动后：

- 服务地址：http://localhost:8080
- **Swagger UI**：http://localhost:8080/swagger-ui.html
- OpenAPI 3.x 文档：http://localhost:8080/v3/api-docs

Compose 包含 `postgres`（5432）、`kafka`（9092，KRaft 单节点）、`app`（8080）三个服务，
应用启动时自动建表并播种演示数据。

## 本地开发

```bash
# 依赖：JDK 17、Maven 3.8+、本地 PostgreSQL（建库 waterdb/用户 water/密码 water123）、本地 Kafka（9092）
cd water-purifier-service
mvn spring-boot:run
# 或用环境变量覆盖连接信息
DB_HOST=localhost DB_PORT=5432 DB_NAME=waterdb DB_USER=water DB_PASSWORD=water123 \
KAFKA_BOOTSTRAP=localhost:9092 mvn spring-boot:run
```

## 测试账号（启动自动播种）

**设备**（设备号 / 小区 / 楼栋 / 在用滤芯 / 初始寿命）：

| 设备号 | 小区 | 楼栋 | 滤芯 | 寿命 |
|---|---|---|---|---|
| DEV-001 | 阳光花园 | 3栋 | FLT-001-A | 100% |
| DEV-002 | 阳光花园 | 5栋 | FLT-002-A | 100% |
| DEV-003 | 滨江新城 | 1栋 | FLT-003-A | **12%（接近阈值，便于演示换芯）** |

**居民账户**（账户号 / 姓名 / 楼栋 / 余额）：ACC-1001 陈小明（阳光花园3栋，200元）、
ACC-1002 刘芳（阳光花园3栋，50元）、ACC-1003 王强（阳光花园5栋，**0.5元，演示扣费失败**）、
ACC-1004 赵丽（滨江新城1栋，300元）、ACC-1005 孙杰（滨江新城1栋，80元）

**师傅**：T001 张建国、T002 李卫国、T003 王志强（均可用，派单按顺序分配）

## 主要 API（详见 Swagger UI）

| 模块 | 端点 |
|---|---|
| 设备遥测 | `POST /api/telemetry`（经 Kafka 异步处理） |
| 设备 | `POST/GET /api/devices`、`GET /api/devices/{deviceNo}/chain`（履约链路） |
| 取水/投诉 | `POST /api/intakes`、`POST /api/complaints` |
| 账户 | `POST /api/accounts`、`POST /api/accounts/{no}/recharge`、`GET /api/accounts/{no}/explanations`（居民解释） |
| 工单/换芯 | `GET /api/work-orders`、`POST /api/work-orders/{no}/arrive`、`POST /api/work-orders/{no}/complete-replacement` |
| 发票 | `POST /api/invoices`、`POST /api/invoices/{no}/correct-title` |
| 运营 | `GET /api/operations/replacement-strategy`（换芯策略）、`GET /api/operations/alerts/open` |
| 物业 | `GET /api/property/notifications?community=&building=` |
| 监管 | `GET /api/regulatory/water-quality/{deviceNo}`、`POST /api/regulatory/spot-check` |

## 验证方式（本题实际执行过的验证）

### 1. 自动化测试（18 个用例全部通过）

```bash
mvn test
```

- `FilterLifeServiceTest`（4 例）：水质压力系数、寿命动态消耗、投诉/维护频率折算、水质判定
- `WaterFlowIntegrationTest`（10 例，Embedded Kafka + H2）：遥测→寿命消耗、低寿命→预警派单、
  水质异常→停售通知物业、取水扣费/余额不足、投诉聚集停售、换芯全流程（含旧滤芯不符/冲洗不足拒绝）、
  换芯后异味免费复检、师傅超时改派、发票开具与抬头更正、履约链路与运营策略
- `ApiLayerTest`（4 例，MockMvc）：参数校验与错误语义、居民解释端点、履约链路与策略端点、OpenAPI 可用性

### 2. 真实环境端到端验证（42 项检查全部通过）

在真实 **PostgreSQL 15 + Kafka 3.7（KRaft）** 环境下启动应用，执行：

```bash
./scripts/verify-e2e.sh        # BASE=http://localhost:8080 可覆盖
```

覆盖：种子数据 → 遥测经 Kafka 驱动寿命消耗（100L×TDS系数1.2 → 寿命 98.8%）→ 低寿命自动派单 →
水质异常停售并通知物业 → 停售拒绝取水/正常扣费/余额不足留痕 → 投诉聚集停售 →
换芯履约（旧滤芯不符被拒、复检合格恢复售水、维护费按楼栋 2 户各摊 60 元）→
换芯后异味免费复检工单 → 发票开具与抬头更正重开 → 监管抽查留痕 →
履约链路 9 类事件齐全 → 运营策略健康分 → 居民端暂停/收费解释 → Swagger UI 可用。

另验证了**师傅未按时到场自动改派**：将工单到场时限改为过去时间，调度器 30 秒巡检后
工单自动改派给其他师傅并生成 `TECHNICIAN_NO_SHOW` 预警；并通过 `kafka-console-consumer`
确认 `water.device.telemetry` / `water.device.intake` / `water.alert` / `water.notification`
四个主题均有真实消息流动。

## 目录结构

```
water-purifier-service/
├── Dockerfile                  # 多阶段构建
├── docker-compose.yml          # app + postgres + kafka 一键启动
├── scripts/verify-e2e.sh       # 端到端验证脚本（42 项检查）
├── src/main/java/com/community/water/
│   ├── config/                 # 业务规则配置、OpenAPI、演示数据播种
│   ├── controller/             # 11 个 REST 控制器
│   ├── dto/                    # 请求/响应 DTO
│   ├── entity/                 # 15 个 JPA 实体 + 枚举
│   ├── exception/              # 业务异常与全局处理
│   ├── kafka/                  # 生产者、遥测消费者、消息体
│   ├── repository/             # Spring Data JPA
│   ├── scheduler/              # 工单超时巡检（师傅未到场改派）
│   └── service/                # 遥测/滤芯寿命/取水/工单/换芯/收费/发票/运营策略
└── src/test/                   # 单元 + 集成 + API 测试
```
