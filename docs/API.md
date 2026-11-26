# dingtodo 接口文档

面向接入方的 HTTP 接口说明。服务部署后的调用地址为 `http://{host}:8080`（端口可在 `application.yml` 调整）。

## 基础说明

### 统一返回体

所有接口返回相同结构：

```json
{ "code": 0, "message": "success", "data": ... }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | `0` 成功；非 0 失败（400 参数错误 / 500 业务或钉钉调用错误） |
| message | string | 成功为 `success`；失败时包含原因，钉钉错误会带官方错误码（如 `60121: 找不到该用户`） |
| data | 任意 | 业务数据，失败时为 `null` |

失败时 HTTP 状态码仍为 200，以 `code` 字段为准。

### 核心概念

| 概念 | 说明 |
|---|---|
| **userId** | 钉钉用户标识（员工维度）。接口入参中的执行人、通知接收人均传 userId。可通过 `GET /auth/userid-by-mobile` 获取 |
| **unionId** | 钉钉跨应用用户标识。由服务内部自动转换，接入方无需关心 |
| **bizId** | 业务键（如单据号、审批实例 id）。用于「按业务键撤回」：一个 bizId 下所有已推送待办可一次性撤回。同一 bizId 重复发送会产生多条待办，重发前建议先撤回 |
| **taskId / msgTaskId** | 钉钉侧标识：taskId 是待办任务 id，msgTaskId 是工作通知 id（撤回通知用） |
| **时间戳** | 均为毫秒级时间戳（如 `1795684883000`） |

---

## 一、待办接口

### 1.1 发待办（同步）

`POST /todo/send`

为每个执行人创建一条钉钉待办。适合单据审批等少量执行人场景；大批量（数百条以上）请用 1.2 批量接口。

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| bizId | string | 是 | 业务键，按业务键撤回时依赖 |
| subject | string | 是 | 待办标题（钉钉列表中展示） |
| description | string | 否 | 待办描述 |
| appUrl | string | 否 | 移动端点击跳转地址 |
| pcUrl | string | 否 | PC 端点击跳转地址 |
| executorIds | string[] | 是 | 执行人（待办责任人）钉钉 userId 列表 |
| participantIds | string[] | 否 | 参与人钉钉 userId 列表 |
| sendNotify | boolean | 否 | `true` 时同时发一条工作通知提醒（需传 appUrl 作为跳转按钮；注意钉钉限制：相同内容通知每人每天仅一条，见 2.1） |
| notifyTitle | string | 否 | 通知标题，缺省用 subject |
| notifyContent | string | 否 | 通知内容（markdown），缺省用 description |

**请求示例：**

```bash
curl -X POST http://127.0.0.1:8080/todo/send -H "Content-Type: application/json" -d '{
  "bizId": "BILL-20261126-001",
  "subject": "张三提交的报销单请处理",
  "description": "报销单 BILL-20261126-001 待审批",
  "appUrl": "https://app.example.com/detail?bizId=BILL-20261126-001",
  "pcUrl": "https://app.example.com/detail?bizId=BILL-20261126-001",
  "executorIds": ["user001"],
  "sendNotify": true
}'
```

**响应（data 为每个执行人一条）：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "userId": "user001",
      "unionId": "union-example-001",
      "taskId": "taska1ef7d9790b243ef3ff6d04de138b656",
      "requestId": "01A21699-94A3-7711-8E73-F9EA8EC44E00",
      "msgTaskId": 123456789
    }
  ]
}
```

> `msgTaskId` 仅在 `sendNotify=true` 且成功发出通知时返回，可用于撤回配套通知。

---

### 1.2 批量发待办（异步）

`POST /todo/batch/send`

提交后**立即返回 batchId**，后台有界线程池逐条异步执行（每条独立成功/失败，互不影响），每条结果落台账。适合大范围推送（如按月给数千项目负责人发填报通知）。

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| items | object[] | 是 | 每项结构与 1.1 请求体完全一致；单次不超过 `dingtodo.batch-queue-capacity`（默认 20000）条 |

**请求示例：**

```bash
curl -X POST http://127.0.0.1:8080/todo/batch/send -H "Content-Type: application/json" -d '{
  "items": [
    { "bizId": "FILL-P001-202611", "subject": "11月项目填报提醒", "appUrl": "https://app.example.com/fill/P001", "executorIds": ["user001"] },
    { "bizId": "FILL-P002-202611", "subject": "11月项目填报提醒", "appUrl": "https://app.example.com/fill/P002", "executorIds": ["user001"] }
  ]
}'
```

**响应：**

```json
{ "code": 0, "message": "success", "data": { "batchId": "5e8f...", "total": 2 } }
```

> **注意**：进度登记在内存中，服务重启后 batchId 查询返回「不存在」且队列中未执行的任务丢失；但已执行的每条都持久化在台账 `ding_user_todo`，对账以台账为准。

---

### 1.3 查询批量进度

`GET /todo/batch/{batchId}`

**路径参数：** batchId（1.2 返回值）

**响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "batchId": "5e8f...",
    "total": 6000,
    "done": 5980,
    "success": 5975,
    "failed": 5,
    "failedItems": ["FILL-P123-202611: 60121: 找不到该用户"],
    "finished": false
  }
}
```

> `failedItems` 为 `bizId: 失败原因` 列表，可修数后重发（重发前建议先按 bizId 撤回，避免重复待办）。批次过多时最早的批次会被淘汰（内存保留最近 100 个）。

---

### 1.4 按 taskId 撤回待办

`POST /todo/withdraw/by-task`

撤回单条已知 taskId 的待办（1.1 的返回值里有 taskId）。

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | string | 是 | 该待办执行人的钉钉 userId |
| taskId | string | 是 | 待办任务 id |

**请求/响应示例（已实测）：**

```bash
curl -X POST http://127.0.0.1:8080/todo/withdraw/by-task -H "Content-Type: application/json" -d '{
  "taskId": "task0123456789abcdef0123456789abcdef",
  "userId": "user001"
}'
```

```json
{ "code": 0, "message": "success", "data": null }
```

---

### 1.5 按业务键撤回待办

`POST /todo/withdraw/by-biz`

撤回某 bizId 下**所有已推送待办**，并撤回其配套工作通知。依赖台账（`dingtodo.record-enabled=true`，内存或 mysql 模式均可）。

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| bizId | string | 是 | 业务键 |

**响应示例：**

```json
{ "code": 0, "message": "success", "data": { "total": 3, "success": 3, "failedUserIds": [] } }
```

> 单条撤回失败不中断整批，失败者列入 `failedUserIds`。

---

### 1.6 查询用户的组织待办

`GET /todo/users/{userId}?fetchAll=`

查询某用户在当前企业内的全部待办卡片。

**参数：**

| 参数 | 位置 | 必填 | 说明 |
|---|---|---|---|
| userId | path | 是 | 钉钉 userId |
| fetchAll | query | 否 | 默认 `false` 只返回第一页；`true` 自动翻页聚合全部 |

**响应示例（已实测）：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "taskId": "task0123456789abcdef0123456789abcdef",
      "subject": "这是一个测试标题",
      "description": "",
      "createTime": 1795684883000
    }
  ]
}
```

---

## 二、工作通知接口

### 2.1 发工作通知

`POST /notify/send`

给一批人发钉钉工作通知（企业会话消息）。

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | string | 是 | `ACTION_CARD`（卡片）/ `TEXT`（纯文本） |
| userIds | string[] | 是 | 接收人钉钉 userId 列表（多个用逗号分隔的接口限制由钉钉侧决定，建议单次 ≤ 100 人） |
| title | string | ACTION_CARD 必填 | 卡片标题 |
| markdown | string | ACTION_CARD 必填 | 卡片内容（markdown 语法） |
| singleTitle | string | 否 | 卡片按钮文字（默认「查看详情」） |
| singleUrl | string | 否 | 卡片按钮跳转地址 |
| text | string | TEXT 必填 | 纯文本内容 |
| bizId | string | 否 | 传入且启用台账时记录通知，可用于按业务键撤回配套通知 |

**请求示例：**

```bash
curl -X POST http://127.0.0.1:8080/notify/send -H "Content-Type: application/json" -d '{
  "type": "ACTION_CARD",
  "userIds": ["user001"],
  "title": "报销单待审批",
  "markdown": "张三提交的报销单 BILL-001 请处理",
  "singleTitle": "去处理",
  "singleUrl": "https://app.example.com/detail?bizId=BILL-001",
  "bizId": "BILL-001"
}'
```

**响应（data 为消息任务 id）：**

```json
{ "code": 0, "message": "success", "data": 777882345 }
```

> **钉钉侧限制（实测确认）：相同内容的通知对同一接收人一天只能发送一次，撤回也不影响去重。**
> 批量提醒类通知（如多个项目用同一模板）务必在内容中带上业务标识（项目名/单据号等），否则第二次起会被钉钉去重，接收人收不到。

### 2.2 批量发工作通知（异步）

`POST /notify/batch/send`

语义同 1.2：立即返回 batchId，后台逐条执行。

**请求体：** `{ "items": [ 2.1 请求体, ... ] }`；响应同 1.2。进度查询同 1.3。

### 2.3 撤回工作通知

`POST /notify/withdraw`

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| msgTaskId | number | 是 | 2.1 / 1.1(sendNotify) 返回的消息任务 id |

**响应：** `{ "code": 0, "message": "success", "data": null }`

> 注意：
> 1. 通知撤回的 msgTaskId 与待办 taskId 是两套 id，不能混用。
> 2. **只能撤回 24 小时内发送的通知**，超时的撤回无效。需要长期保留提醒的场景建议用待办（待办可随时撤回）而不是工作通知。

---

## 三、用户身份接口

### 3.1 手机号查 userId

`GET /auth/userid-by-mobile?mobile={手机号}`

**响应示例（已实测）：**

```json
{ "code": 0, "message": "success", "data": "user001" }
```

> 员工已离职时钉钉返回 `60121: 找不到该用户`（code=500）。实时查询不做缓存，保证换号/离职后结果准确。

### 3.2 免登换 userId

`GET /auth/userid?code={钉钉免登授权码}`

用于钉钉客户端内 H5 免登场景：前端通过钉钉 JSAPI 拿到免登 code 后，用它换取 userId。

**响应示例：**

```json
{ "code": 0, "message": "success", "data": "user001" }
```

---

## 四、接入与联调 Checklist

1. 配置凭证：`DINGTALK_APPKEY` / `DINGTALK_APPSECRET` / `DINGTALK_AGENT_ID` 环境变量，或本地 `application-local.yml`（gitignored，激活 `--spring.profiles.active=local`）
2. 按 3.1 获取测试用户的 userId
3. 按 1.1 发一条测试待办 → 钉钉客户端确认可见
4. 按 1.6 查待办列表确认在列 → 按 1.4 撤回 → 再查确认消失
5. 按 2.1 发通知 → 2.3 撤回
6. 用带 bizId 的待办验证 1.5 按业务键撤回
7. 大批量场景验证 1.2 / 2.2 + 1.3 进度查询

## 五、错误码速查

| 现象 | 原因与处理 |
|---|---|
| `dingding.appkey / dingding.appsecret 未配置` | 凭证未注入，检查环境变量或 local profile |
| `60121: 找不到该用户` | 员工不存在或已离职（手机号查询 / 发待办场景） |
| `IP 不在白名单`（errcode 60020） | 服务器出口 IP 未加入钉钉应用的服务器出口 IP 配置 |
| 批量进度查询返回「批量任务不存在或已被淘汰」 | batchId 错误、服务重启、或批次超出内存保留数（100） |
| 一次提交 N 条超过队列容量 | 分批提交，单次上限为 `dingtodo.batch-queue-capacity`（默认 20000） |

更多配置（台账存储 memory/mysql 切换、留痕保留期、批量并发数等）见项目根目录 [README.md](../README.md)。
