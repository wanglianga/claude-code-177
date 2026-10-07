#!/bin/bash
# 端到端验证脚本：真实 PostgreSQL + Kafka 环境
BASE=${BASE:-http://127.0.0.1:8080}
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad() { FAIL=$((FAIL+1)); echo "  ❌ $1"; }
check() { # $1=描述 $2=实际值 $3=期望包含
  if echo "$2" | grep -q "$3"; then ok "$1"; else bad "$1 -> 期望包含[$3]，实际: $(echo "$2" | head -c 200)"; fi
}
J() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }

echo "== 1. 种子数据：设备列表 =="
DEVICES=$(curl -s $BASE/api/devices)
check "3 台设备已播种" "$(echo "$DEVICES" | grep -o deviceNo | wc -l)" "3"

echo "== 2. 遥测上报 → 滤芯寿命动态消耗（Kafka 异步） =="
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-001","totalOutputLiters":0,"tds":40,"residualChlorine":0.5,"flowRate":1.5}' > /dev/null
sleep 3
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-001","totalOutputLiters":100,"tds":90,"residualChlorine":0.5,"flowRate":1.5}' > /dev/null
sleep 3
LIFE=$(curl -s $BASE/api/devices/DEV-001 | J "['activeFilter']['lifePercent']")
check "DEV-001 滤芯寿命=98.8（100L×TDS系数1.2）" "$LIFE" "98.8"

echo "== 3. 滤芯寿命接近阈值 → 预警 + 自动派单 =="
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-002","totalOutputLiters":0,"tds":40,"residualChlorine":0.5}' > /dev/null
sleep 3
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-002","totalOutputLiters":8600,"tds":40,"residualChlorine":0.5}' > /dev/null
sleep 3
ORDERS=$(curl -s "$BASE/api/work-orders?deviceNo=DEV-002")
check "DEV-002 生成换芯工单" "$ORDERS" "FILTER_REPLACEMENT"
check "工单已派师傅" "$ORDERS" "techNo"
check "工单含到场时限" "$ORDERS" "deadlineAt"

echo "== 4. 水质异常 → 暂停售水 + 通知物业 =="
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-001","totalOutputLiters":120,"tds":150,"residualChlorine":0.5}' > /dev/null
sleep 3
DEV1=$(curl -s $BASE/api/devices/DEV-001)
check "DEV-001 状态 PAUSED" "$DEV1" "PAUSED"
check "暂停原因含水质异常" "$DEV1" "水质异常"
PROP=$(curl -s -G "$BASE/api/property/notifications" --data-urlencode "community=阳光花园" --data-urlencode "building=3栋")
check "物业收到水质异常通知" "$PROP" "WATER_QUALITY_ABNORMAL"

echo "== 5. 居民取水：停售拒绝 / 正常扣费 / 余额不足 =="
R=$(curl -s -X POST $BASE/api/intakes -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-001","accountNo":"ACC-1001","amountLiters":5}')
check "停售设备拒绝取水" "$R" "暂停售水"
R=$(curl -s -X POST $BASE/api/intakes -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-002","accountNo":"ACC-1001","amountLiters":10}')
check "正常取水扣费 3.00 元" "$R" '"fee":3.00'
check "余额 197.00" "$R" '"balanceAfter":197.00'
R=$(curl -s -X POST $BASE/api/intakes -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-002","accountNo":"ACC-1003","amountLiters":5}')
check "余额不足拒绝" "$R" "余额不足"
CHARGES=$(curl -s $BASE/api/accounts/ACC-1003/charges)
check "扣费失败留痕" "$CHARGES" "FAILED"

echo "== 6. 投诉聚集 → 暂停售水 =="
for i in 1 2 3; do
  curl -s -X POST $BASE/api/complaints -H 'Content-Type: application/json' \
    -d '{"deviceNo":"DEV-002","accountNo":"ACC-1002","type":"ODOR","content":"水有异味"}' > /dev/null
done
sleep 1
DEV2=$(curl -s $BASE/api/devices/DEV-002)
check "DEV-002 投诉聚集后 PAUSED" "$DEV2" "PAUSED"

echo "== 7. 换芯履约全流程（DEV-003，初始寿命 12%） =="
curl -s -X POST $BASE/api/telemetry -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-003","totalOutputLiters":0,"tds":40,"residualChlorine":0.5}' > /dev/null
sleep 3
ORDER_NO=$(curl -s "$BASE/api/work-orders?deviceNo=DEV-003" | python3 -c "
import sys,json
orders=json.load(sys.stdin)
print([o['orderNo'] for o in orders if o['type']=='FILTER_REPLACEMENT'][0])")
TECH=$(curl -s "$BASE/api/work-orders/$ORDER_NO" | J "['technician']['techNo']")
echo "  工单 $ORDER_NO 派给师傅 $TECH"
R=$(curl -s -X POST $BASE/api/work-orders/$ORDER_NO/arrive -H 'Content-Type: application/json' \
  -d "{\"techNo\":\"$TECH\"}")
check "师傅到场确认" "$R" "ARRIVED"
R=$(curl -s -X POST $BASE/api/work-orders/$ORDER_NO/complete-replacement -H 'Content-Type: application/json' \
  -d "{\"techNo\":\"$TECH\",\"oldFilterNo\":\"FLT-WRONG\",\"newFilterNo\":\"FLT-003-B\",\"newBatchNo\":\"BATCH-2026-10\",\"photoUrls\":\"http://img/1.jpg\",\"flushMinutes\":15,\"recheckTds\":40,\"recheckChlorine\":0.5}")
check "旧滤芯编号不符被拒" "$R" "不一致"
R=$(curl -s -X POST $BASE/api/work-orders/$ORDER_NO/complete-replacement -H 'Content-Type: application/json' \
  -d "{\"techNo\":\"$TECH\",\"oldFilterNo\":\"FLT-003-A\",\"newFilterNo\":\"FLT-003-B\",\"newBatchNo\":\"BATCH-2026-10\",\"photoUrls\":\"http://img/1.jpg,http://img/2.jpg\",\"flushMinutes\":15,\"recheckTds\":40,\"recheckChlorine\":0.5}")
check "换芯复检合格" "$R" "PASS"
DEV3=$(curl -s $BASE/api/devices/DEV-003)
check "DEV-003 恢复售水" "$DEV3" "NORMAL"
check "新滤芯 FLT-003-B 启用" "$DEV3" "FLT-003-B"
ACC4=$(curl -s $BASE/api/accounts/ACC-1004)
check "维护费分摊扣款（300-60=240）" "$ACC4" '"balance":240.00'
SHARE=$(curl -s $BASE/api/accounts/ACC-1004/charges)
check "分摊收费记录 MAINTENANCE_SHARE" "$SHARE" "MAINTENANCE_SHARE"

echo "== 8. 换芯后仍有异味 → 免费复检工单 =="
curl -s -X POST $BASE/api/complaints -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-003","accountNo":"ACC-1004","type":"ODOR","content":"换芯后仍有异味"}' > /dev/null
sleep 1
ORDERS3=$(curl -s "$BASE/api/work-orders?deviceNo=DEV-003")
check "生成免费复检工单" "$ORDERS3" '"freeOfCharge":true'

echo "== 9. 发票开具与抬头更正 =="
CHARGE_NO=$(curl -s $BASE/api/accounts/ACC-1001/charges | python3 -c "
import sys,json
cs=json.load(sys.stdin)
print([c['chargeNo'] for c in cs if c['status']=='SUCCESS'][0])")
INV=$(curl -s -X POST $BASE/api/invoices -H 'Content-Type: application/json' \
  -d "{\"chargeNo\":\"$CHARGE_NO\",\"title\":\"错误抬头公司\",\"taxNo\":\"91310000XXXX\"}")
INV_NO=$(echo "$INV" | J "['invoiceNo']")
check "开票成功" "$INV" "ISSUED"
R=$(curl -s -X POST $BASE/api/invoices/$INV_NO/correct-title -H 'Content-Type: application/json' \
  -d '{"title":"上海阳光花园物业管理有限公司","taxNo":"91310000YYYY"}')
check "抬头更正重开" "$R" "REISSUED"
OLD=$(curl -s $BASE/api/invoices/$INV_NO)
check "原发票标记 TITLE_ERROR" "$OLD" "TITLE_ERROR"

echo "== 10. 监管抽查水质记录 =="
curl -s -X POST $BASE/api/regulatory/spot-check -H 'Content-Type: application/json' \
  -d '{"deviceNo":"DEV-003","tds":35,"residualChlorine":0.4,"inspector":"监管员-王","note":"季度抽查"}' > /dev/null
REG=$(curl -s "$BASE/api/regulatory/water-quality/DEV-003")
check "监管抽查留痕" "$REG" "REGULATORY"
check "换芯复检留痕" "$REG" "REPLACEMENT"

echo "== 11. 履约链路 / 运营策略 / 居民解释 =="
CHAIN=$(curl -s $BASE/api/devices/DEV-003/chain)
for ET in TELEMETRY ALERT ORDER_CREATED ORDER_ARRIVED REPLACEMENT RECHECK CHARGE DEVICE_RESUMED COMPLAINT; do
  check "链路含事件 $ET" "$CHAIN" "$ET"
done
STRATEGY=$(curl -s $BASE/api/operations/replacement-strategy)
check "运营策略覆盖 3 台设备" "$(echo "$STRATEGY" | grep -o deviceNo | wc -l)" "3"
check "策略含健康分" "$STRATEGY" "healthScore"
EXPLAIN=$(curl -s $BASE/api/accounts/ACC-1001/explanations)
check "居民解释含扣费说明" "$EXPLAIN" "取水"
check "居民解释含暂停原因" "$EXPLAIN" "水质异常"

echo "== 12. Swagger UI / OpenAPI =="
check "OpenAPI 文档" "$(curl -s $BASE/v3/api-docs)" "净水机"
check "Swagger UI 页面" "$(curl -s -o /dev/null -w '%{http_code}' $BASE/swagger-ui/index.html)" "200"

echo ""
echo "==================================="
echo "结果: 通过 $PASS 项, 失败 $FAIL 项"
echo "==================================="
[ $FAIL -eq 0 ]
