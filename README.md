# dingtodo

钉钉待办 / 工作通知 公共服务插件。

本项目从 `jzmobile-gateway` 中抽取与钉钉相关的**通用能力**，剥离其中与公司业务系统（NC、ct-zjjh 等）耦合的代码，形成一个只负责「钉钉待办与通知」的独立服务，供各业务系统以统一接口接入。

## 背景

`jzmobile-gateway` 原本同时承担了三类职责：

1. 钉钉通用能力（发待办、撤待办、查待办、发工作通知、撤回通知、免登换 userId）；
2. NC 业务耦合（NC 审批单据推送/撤回、查 NC 待办、发 NC 通知、NC 用户映射、NC 服务器透传代理）；
3. ct-zjjh 预算审批业务（深链拼装、差量推送等）。

后两类属于具体业务，导致公共能力难以复用。本项目只保留第 1 类，并对原实现中的已知问题做重构。

## 功能特性

### 待办（DingTalk Todo，新版 v1.0 REST API）

- **发待办**：创建钉钉待办任务（subject / description / 详情跳转 appUrl+pcUrl / 提醒配置 / 执行人、参与人），同时可选发一条工作通知提醒
- **撤回待办**：
  - 按 `taskId + userId` 直接撤回
  - 按 **业务键 bizId**（原项目单据号 `vbillcode` 的通用化）撤回，自动查台账找到最近推送的 taskId
- **查待办**：分页查询某用户组织内的全部待办卡片
- **本地待办台账**：记录每次推送（taskId / requestId / 业务键 / 状态），支撑「按业务键撤回」与推送情况排查

### 工作通知（企业会话消息）

- **发通知**：支持 action_card（带标题 / markdown / 跳转按钮）与纯文本两种消息类型
- **撤回通知**：按 msg taskId 撤回已发的工作通知

### 用户身份

- **userId → unionId** 转换（调用钉钉 v2/user/get）
- **免登**：钉钉授权 code 换 userId
- 业务侧用户体系（原 NC sourceId 之类）通过可插拔的 `UserIdResolver` 接口由接入方自己实现，插件不绑定任何具体业务系统

### 基础能力

- access_token 获取与**带过期时间的缓存**（原实现每次实时请求，无缓存）
- 所有钉钉 API 调用**自动留痕**（在 JsonHttp 统一埋点，一次覆盖全部调用）：url / 参数 / 响应 / 操作名 / 成败 / 耗时。安全增强：URL 中 access_token 脱敏、不落明文 token、超长截断、留痕失败不影响业务调用
- 统一返回体与异常处理

## 技术栈

| 项 | 说明 |
|---|---|
| JDK | 17 |
| 构建 | Maven |
| 框架 | Spring Boot 3.x |
| 钉钉 API | 旧版 oapi + 新版 v1.0 REST，统一通过 OkHttp3 + Jackson 直调（未引入旧版 taobao 风格 SDK，全部接口可本地 Mock 测试） |
| HTTP 客户端 | 统一使用 OkHttp3（原项目 OkHttp2/3 并存，已统一） |
| 数据库 | MySQL（Spring Data JPA，台账持久化；默认内存模式可零配置启动） |

## 快速开始

### 配置

配置见 `src/main/resources/application.yml`，凭证通过环境变量注入：

```yaml
dingding:
  appkey: ${DINGTALK_APPKEY:}
  appsecret: ${DINGTALK_APPSECRET:}
  agent-id: ${DINGTALK_AGENT_ID:}
  # 一般无需修改（测试时可指向 MockWebServer）
  oapi-base-url: https://oapi.dingtalk.com
  api-base-url: https://api.dingtalk.com
  token-cache-seconds: 7000

dingtodo:
  record-enabled: true        # 本地台账开关，按业务键撤回依赖台账
  union-id-cache-seconds: 3600
```

> **安全提示**：AppSecret 不允许提交到仓库，请通过环境变量或配置中心注入（原 `jzmobile-gateway` 曾将真实凭证提交入库，本项目明确禁止）。

### 表结构

建表脚本见 `src/main/resources/schema-mysql.sql`：

- `ding_user_todo` 台账表：沿用原项目设计，业务键字段通用化（`vbillcode` → `biz_id`），含 `biz_id+status` / `task_id` 索引
- `ding_todo_api` 调用留痕表：自动记录所有钉钉调用（url / 参数 / 响应 / 操作名 / 成败 / 耗时）。与原表的差异：不存明文 access_token（url 脱敏），新增 `method` / `success` / `err_msg` / `elapsed_ms`

### 台账存储模式

`dingtodo.record-store` 支持两种模式：

| 模式 | 启用方式 | 说明 |
|---|---|---|
| `memory`（默认） | `mvn spring-boot:run` 即可 | 零配置启动，台账存内存，重启丢失 |
| `mysql` | `--spring.profiles.active=mysql` + 环境变量 | JPA 持久化到 MySQL，支持按业务键撤回跨重启生效 |

MySQL 模式环境变量：

```bash
MYSQL_HOST=localhost     # 可省略，默认 localhost
MYSQL_PORT=3306          # 可省略，默认 3306
MYSQL_DB=dingtodo        # 可省略，默认 dingtodo
MYSQL_USER=root
MYSQL_PASSWORD=...
```

开发期默认 `ddl-auto=update` 自动建表；生产建议执行 `schema-mysql.sql` 并将 `spring.jpa.hibernate.ddl-auto` 改为 `none`。接入方也可注册自己的 `TodoRecordStore` bean 替换内置实现。

### 留痕表增长治理

`ding_todo_api` 随调用单调增长。留痕一条约 1–2KB，审批待办类业务一天数百到数千次调用，一年约 1–2GB，短期不清理也有余量；长期建议开启内置的定期清理（**默认关闭**，是否清理、保留多久属于审计/合规决策）：

```yaml
dingtodo:
  api-log-retention-days: 90        # 保留 90 天，0 = 不清理（默认）
  api-log-cleanup-cron: "0 30 3 * * ?"  # 可选，默认每天 03:30 执行
```

仅 mysql 模式生效（内存模式天然限量最近 500 条）。数据量特别大时可进一步改用 MySQL 按时间分区 + DROP PARTITION 清理，本项目暂未内置。

## 接口规划

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/todo/send` | 发待办（业务键 + 标题 + 描述 + 跳转地址 + 执行人列表，可选附带工作通知） |
| POST | `/todo/withdraw/by-biz` | 按业务键撤回（撤该 bizId 全部已推送待办及配套通知） |
| POST | `/todo/withdraw/by-task` | 按 taskId + userId 直接撤回 |
| GET | `/todo/users/{userId}?fetchAll=` | 查询用户组织内全部待办（fetchAll 自动翻页聚合） |
| POST | `/notify/send` | 发工作通知（action_card / text） |
| POST | `/notify/withdraw` | 按 msgTaskId 撤回工作通知 |
| GET | `/auth/userid?code=` | 免登：钉钉 code 换 userId |

发待办请求示例：

```json
{
  "bizId": "BILL-20260101-001",
  "subject": "XX提交的报销单请处理",
  "description": "报销单 BILL-20260101-001 待审批",
  "appUrl": "https://app.example/detail?bizId=BILL-20260101-001",
  "pcUrl": "https://pc.example/detail?bizId=BILL-20260101-001",
  "executorIds": ["user001"],
  "sendNotify": true
}
```

统一返回体：`{"code": 0, "message": "success", "data": ...}`，`code != 0` 表示失败。

入参中「接收人」统一传钉钉 userId，插件内部负责 userId → unionId / openId 转换；业务系统自己的用户映射关系不要传进来。

## 与原项目的差异

### 剔除（公司业务）

- NC 相关：`NCServerTool`、NC 单据推送/撤回入口（`/dingmsg_rejection`）、查 NC 待办防误撤逻辑、NC 通知入口（`/sendNotifyByUser`）、`ding_user` 表 NC sourceId 映射
- NC / ct 透传代理：`ProxyController`、`/proxy/**`、`/sync/user(s)`
- ct-zjjh 整个 `ct/` 包（其深链拼装与差量推/撤的设计思路作为参考保留）
- 其它内部系统支撑代码（报表用户、PDF、API 日志切面等）

### 重构改进

- 780 行的 `DingTodoService` 拆分为：
  - `DingTalkTodoClient`：发 / 撤 / 查待办（v1.0 REST）
  - `DingTalkNotifyClient`：发 / 撤工作通知（oapi SDK）
  - `TokenManager`：access_token 缓存
  - `UserIdResolver`：业务用户 → 钉钉用户，可插拔
- 待办状态魔法值（原 `SP` / `SH` / `TZ` 字符串）改为枚举
- 移除 `HttpClientUtil` 中硬编码的 appkey / appsecret
- 待办请求体字段沿用原设计：`subject / description / detailUrl{appUrl, pcUrl} / remindNotifyConfigs / executorIds / participantIds`

## 开发计划

- [x] 骨架搭建（Spring Boot 3.x + 统一返回体 + 异常处理）
- [x] TokenManager（token 获取 + TTL 缓存）
- [x] 待办：发 / 按 taskId 撤 / 按业务键撤 / 查
- [x] 通知：发（action_card / text）/ 撤
- [x] 用户：userId → unionId、免登换 userId
- [x] 台账（接口 + 内存默认实现，可注入自己的持久化实现替换）
- [x] 单元测试（MockWebServer / Mockito，不依赖真实环境）
- [x] 台账 MySQL 持久化实现（ding_user_todo 表，profile 切换 memory/mysql）
- [x] 调用留痕（JsonHttp 统一埋点落 ding_todo_api 表，token 脱敏，跟随 record-store 双模式）
- [ ] 联调真实钉钉环境

## 来源

参考项目：`jzmobile-gateway`（内网仓库）。公共能力以其 `service/DingTodoService` 为蓝本抽取，具体耦合类清单见上文「与原项目的差异」。
