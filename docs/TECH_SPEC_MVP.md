# 澄远 CRM 技术说明（阶段① + 阶段③）

读者：后端 Java 与前端 Vue。这一版交付认证角色、联系人保险库，以及 M2 消息骨架。阶段②⑤仍是文档和纯函数。阶段④⑥的 HTTP 骨架会校验并返回 501 或空列表，不对外发送，也不解密保险库。接线说明在 `docs/M2_MESSAGING_SKELETON.md`。

## 1. 范围

已实现：

- 注册、登录、当前用户。角色只有业务员 `SALESPERSON`、经理 `MANAGER`。
- 新建客户、JSON 导入、列表、详情。
- 联系方式 AES-256-GCM 加密入库，列表用预先算好的脱敏列，不解密。
- 经理对单个客户揭盲，默认 15 分钟；结束或过期后详情恢复脱敏。
- 审计查询仅经理可看。

明确不做：

- 不提供导出、批量揭盲、阶段升迁、公海、沟通原文。
- 不对外发送 WhatsApp 或邮件。M2 只有服务端骨架，见第 12 节。
- 不在浏览器里保存联系方式明文。录入请求本身必须带一次明文，这是加密前的唯一入口，见第 6 节。发送请求不能再带一次明文目的地。

## 2. 架构

```text
Vue (43123) -- /api --> Spring Boot (18443)
                           |-- Spring Security + JWT
                           |-- LeadService：校验、去重、只回脱敏视图
                           |-- ContactCryptoService：加密 / HMAC
                           |-- UnmaskService：会话与 TTL
                           |-- MessagingService：按客户编号校验，不解密、不外发
                           |-- audit_event
                           +-- H2（dev）或 PostgreSQL（postgres 档案）
```

默认档案是 `dev`（内存 H2 + 演示数据 + 公开占位密钥）。`postgres` 档案要求环境变量提供两个 32 字节密钥和 JWT 密钥，缺了就启动失败。

包：

| 包 | 责任 |
| --- | --- |
| `com.ftcrm.web` | HTTP、当前用户、业务员响应守卫 |
| `com.ftcrm.service` | 用例。创建响应永远不带明文 |
| `com.ftcrm.vault` | 规范化、脱敏、加密 |
| `com.ftcrm.security` | JWT 与登录主体。主体里没有密码哈希 |
| `com.ftcrm.roadmap` | 后续阶段的纯函数和会失败的网关 |
| `com.ftcrm.messaging` | M2 发送与存档骨架。客户端不解密 |

## 3. 数据表

Flyway：`backend/src/main/resources/db/migration/V1__init.sql`。

### app_user

| 列 | 说明 |
| --- | --- |
| username | 小写，唯一 |
| password_hash | BCrypt |
| role | `SALESPERSON` 或 `MANAGER` |

### lead_record

公司、国家、联系人姓名、阶段、意向、来源、备注、负责人、时间。没有电话和邮箱列。新客户阶段固定为 `LEAD`（线索）。

### contact_vault

一行对应一个客户。

| 列 | 说明 |
| --- | --- |
| phone_cipher / email_cipher / whatsapp_cipher | Base64 的 AES-GCM 密文 |
| *_iv | 每字段随机 12 字节 IV |
| *_hash | 规范化值的 HMAC-SHA256，64 位十六进制，用于去重 |
| *_masked | 脱敏展示值。列表不解密 |

电话、邮箱、WhatsApp 三个哈希跨字段查重：新电话若等于已有 WhatsApp，也算重复。

### unmask_session

经理、客户、原因、开始、到期、结束、状态 `ACTIVE | ENDED | EXPIRED`。

### audit_event

操作者、动作、目标、原因、metadata JSON、时间。metadata 只允许 `expiresAt`、`ttlMinutes`、`companyName`、`imported`、`duplicated`、`failed`、`sessionId`。字符串里出现 `@` 或连续 8 位数字会被拒绝写入。

## 4. 加密与脱敏

- 算法：`AES/GCM/NoPadding`，256 位密钥，128 位 tag。
- AAD：`ftcrm:v1:{leadId}:{phone|email|whatsapp}`。密文不能挪到别的客户或别的字段。
- 查重哈希使用另一把密钥，不能从哈希反推原文。
- 电话规范化：保留开头的 `+`，去掉分隔符，只留 6 到 20 位数字。
- 邮箱：trim 后小写。
- 脱敏电话：`+` 与前 3 位、后 4 位，中间 `****`。例：`+4900001112299` → `+490****2299`。
- 脱敏邮箱：本地部分只留首字符。例：`sentinel.hans@acme-export.test` → `s***@acme-export.test`。域名保留，方便认出公司邮箱，但不是完整地址。

密钥：

- `CONTACT_VAULT_KEY`：标准 Base64，解码后 32 字节。
- `CONTACT_VAULT_HASH_KEY`：同样 32 字节。
- `JWT_SECRET`：至少 32 字节，HS256，有效期 1 到 24 小时，默认 12。

启动时校验长度，日志不打印密钥。

## 5. 角色与揭盲

- 业务员只能看 `owner_id` 是自己的客户。别人的客户返回 404，文案与不存在相同。
- 经理看全部。
- 注册忽略客户端传来的角色，一律业务员。
- 揭盲：`POST /api/leads/{id}/unmask`，原因 8 到 200 字，且不能含 `@` 或连续 8 位数字。
- TTL 来自 `ftcrm.unmask-ttl-minutes`，只允许 1 到 15，默认 15。已有未过期会话时再次申请不会延长。
- 明文只出现在揭盲期间的**详情**响应，字段为 `revealed`。列表即使经理正在揭盲也不带 `revealed`。
- 过期在下次读取时落成 `EXPIRED` 并记 `UNMASK_EXPIRE`。
- `POST /api/leads/{id}/unmask/end` 记 `UNMASK_END`。

业务员响应还有一道 `PlaintextLeakGuard`：JSON 里若出现 `phone`、`email`、`whatsapp`、`revealed`、`password`、密文或哈希等键，响应改成 500，正文改为「响应被安全策略拦截」，不把原正文写出。脱敏键是 `phoneMasked` 等，不在禁名单里。

## 6. 明文从哪里进来、不能去哪里

创建和导入的**请求体**含有明文。这是服务端加密前无法避免的一次传输。要求：

- 只走 HTTPS（本机开发除外）。
- 应用日志不记请求体。`logback-spring.xml` 会把邮箱和连续 8 位以上数字打成占位符。Hibernate 参数绑定日志被关掉。
- 校验错误不回显被拒绝的值。
- 公司、国家、联系人、意向、来源、备注、姓名、揭盲原因走 `SideChannelGuard`，防止用这些回显字段夹带号码。
- 查询字符串和表单参数里出现 `@` 或连续 8 位数字直接 400。
- `/api/**` 响应 `Cache-Control: no-store`。
- 不存在 `/api/leads/export`。客户 id 路径只匹配数字，`export` 不是详情。

审计接口不返回 `metadata_json` 原文，只返回动作、人和原因。

## 7. 接口

除健康检查、注册、登录外，都要 `Authorization: Bearer <jwt>`。

错误体：`{ "error": "CODE", "message": "中文说明" }`。

### POST /api/auth/register

```json
{ "username": "liwei", "password": "Sales@123", "displayName": "李伟" }
```

密码至少 8 位，且同时有字母和数字。用户名 `^[a-zA-Z0-9._-]{3,32}$`，入库前转小写。

响应：`{ "token", "user": { "id", "username", "displayName", "role", "roleLabel" } }`。`role` 恒为 `SALESPERSON`。

### POST /api/auth/login

失败一律「用户名或密码错误」，并记一条不含密码的 `LOGIN_FAILED`。

### GET /api/auth/me

### GET /api/health

`{ "status": "ok" }`

### POST /api/leads

```json
{
  "companyName": "汉堡穆勒阀门有限公司",
  "country": "德国",
  "contactName": "汉斯·穆勒",
  "phone": "+4900001112233",
  "email": "hans.mueller@example.com",
  "whatsapp": "+4900001112233",
  "productInterest": "不锈钢球阀 DN50",
  "source": "广交会",
  "notes": "展会名片"
}
```

电话、邮箱、WhatsApp 至少一项。成功响应是下面的详情形状，且 `revealed` 不出现。重复联系方式返回 409 `DUPLICATE_CONTACT`，消息不含原文。

### POST /api/leads/import

```json
{ "leads": [ { "companyName": "示例公司", "country": "德国", "contactName": "汉斯", "email": "buyer@example.com" } ] }
```

最多 100 条。响应：

```json
{ "imported": 1, "duplicated": 0, "failed": 0, "leads": [], "errors": [] }
```

`leads` 同样只有脱敏视图。单行失败不影响其它行。

### GET /api/leads

最多 200 条，按更新时间倒序。业务员只看自己的。元素结构与详情相同，但没有 `revealed`。

### GET /api/leads/{id}

```json
{
  "id": 1,
  "companyName": "汉堡穆勒阀门有限公司",
  "country": "德国",
  "contactName": "汉斯·穆勒",
  "stage": "LEAD",
  "stageLabel": "线索",
  "productInterest": "不锈钢球阀 DN50",
  "source": "广交会",
  "notes": "展会名片",
  "ownerId": 2,
  "ownerName": "林业务",
  "createdAt": "2026-10-23T08:00:00Z",
  "updatedAt": "2026-10-23T08:00:00Z",
  "contact": {
    "phoneMasked": "+490****2233",
    "emailMasked": "h***@example.com",
    "whatsappMasked": "+490****2233"
  },
  "unmask": { "active": false }
}
```

经理揭盲有效时多一个对象，并且 `unmask.active` 为 true：

```json
{
  "unmask": { "active": true, "sessionId": 9, "expiresAt": "2026-10-23T08:15:00Z" },
  "revealed": {
    "phone": "+4900001112233",
    "email": "hans.mueller@example.com",
    "whatsapp": "+4900001112233"
  }
}
```

### POST /api/leads/{id}/unmask

仅经理。`{ "reason": "客户争议到港数量，需核对原话" }`。响应同详情，带 `revealed`。

### POST /api/leads/{id}/unmask/end

仅经理。响应恢复为无 `revealed`。

### GET /api/audit

仅经理。最近 200 条。字段：`id`、`actorName`、`actorUsername`、`action`、`actionLabel`、`targetType`、`targetId`、`reason`、`createdAt`。

动作：`LOGIN`、`LOGIN_FAILED`、`REGISTER`、`LEAD_CREATE`、`LEAD_IMPORT`、`UNMASK_START`、`UNMASK_END`、`UNMASK_EXPIRE`、`MESSAGE_SEND`、`MESSAGE_ARCHIVE`。后两个的形状见第 12 节。审计接口仍然不返回 metadata 原文。

## 8. 前端约定

- 技术：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus。开发端口 43123。
- 登录页、客户列表、客户详情、经理审计。令牌放 `sessionStorage`，关标签页就丢。
- 列表和详情用 `sanitizeLead`：业务员响应里若出现 `revealed`，丢掉并提示。经理也只有 `unmask.active` 时才保留。
- `.shield` 禁选、禁复制、禁剪切、禁拖拽、禁右键。`main.ts` 在捕获阶段再拦一次 copy/cut。
- 打印时隐藏联系方式。
- 水印是当前姓名、用户名和「禁止外传」，盖在页面上，不挡点击。
- 不提供导出按钮，也不要加会把 `contact` 或 `revealed` 写进 CSV 的功能。
- 新建和导入成功后清空输入框。不要把明文放进 Pinia。

界面禁复制是阻拦，不是安全边界。业务员的保证来自接口根本没有明文。经理揭盲时明文必须出现在 DOM 里才能看见，因此截屏无法从技术上禁止；水印用来追到操作者。

## 9. 生命周期（文档 + 纯函数，未接 API）

| 代码 | 中文 |
| --- | --- |
| LEAD | 线索 |
| INQUIRY | 询盘 |
| NEGOTIATION | 谈判 |
| WON | 成交 |
| SHIPPED | 发货 |
| AFTERSALES | 售后 |
| LOST | 流失 |

正向升迁由 `StagePromotionPolicy.forward` 计算，调用方不能指定目标阶段：

- 仅「已联系」、没有有效询盘：不能从线索前进。
- 线索 → 询盘：收到询盘，且品名、数量、目的港至少有一项。
- 询盘 → 谈判：已经报价，并且客户回复了。
- 谈判 → 成交：有订单号或合同号。
- 成交 → 发货：有货运单证号。
- 发货 → 售后：有签收或反馈。

流失由 `evaluateLoss`（以及兼容用的 `shouldMarkLost`）单独判断：回复命中写死的拒绝文案，例如 `we decided not to proceed`，或距离上次有效互动不少于 30 天。不要和正向升迁写进同一次更新。仅「已联系」或只打招呼不算有效互动。

A/B/C/D 由 `CustomerGradePolicy` 按证据计算，优先级是 A、D、B、C。样例在 `docs/M3_STAGE_ABCD_SAMPLES.md`。

流失后 `WakeSequencePlanner.planForLostLead` 返回第 0 / 3 / 7 天的计划，并且不允许发送。`dispatch` 仍会失败。

## 10. 验收

自动化（`mvn test`）：

- 业务员创建、列表、详情的 JSON 不含完整电话、邮箱、WhatsApp，也不含 `revealed` / `phone` / `email` / `whatsapp` 这些键。
- 库里的密文列不含原文，脱敏列不含邮箱本地部分。
- 创建过程的日志不含这些哨兵值。
- 业务员揭盲和审计为 403。`/api/leads/export` 不是 200。
- 经理揭盲详情含原文；同时列表和业务员详情仍不含原文。
- 结束后不含原文。时间拨过 16 分钟后再读详情，不含原文，并出现 `UNMASK_EXPIRE`。
- 审计的 reason 和 metadata 不含原文。
- 导入响应不含原文；相同联系方式第二次计为重复。
- 备注夹带电话被拒绝，错误体不含该电话。
- 注册时声称自己是经理，结果仍是业务员。
- 测试探针若返回 `phone` 字段，业务员得到 500，且正文没有探针值。
- 查询参数里的 8 位数字被拒绝且不回显。
- 业务员对自己的客户调用 WhatsApp / 邮件发送得到 501，响应、日志和审计 metadata 都不含明文目的地或供应商密钥。
- 请求里夹带 `to`、`email`、`phone` 等目的地字段得到 400，错误体不回显这些值。
- 业务员不能替别人的客户发送或列出沟通；经理可以，同样不返回明文目的地。
- 存档接口不回显密文，沟通列表为空。

手工：

- 业务员账号打开列表和详情，选不中联系方式，Ctrl/Cmd+C 剪贴板不变。
- 页面没有导出。打印预览里联系方式被替换成「联系方式不可打印」。
- 水印能看见自己的姓名。
- 经理填写短于 8 字的原因会被拒绝；合法原因后出现倒计时和明文，结束或等待过期后明文消失。
- 审计页能看到开始和结束，看不到完整号码。

## 11. 接手时不要破坏的点

- 不要给 `LeadDetailResponse` 增加未经守卫审查的联系方式字段。新增回显字段要先过 `SideChannelGuard`。
- 不要在 `toString`、异常消息、审计 metadata 里拼原始联系方式。
- 不要把 `spring.jpa.show-sql` 或 Hibernate bind 日志打开。
- 生产不要激活 `dev` 档案。占位密钥写在 `application-dev.yml`，谁拿到包都能解开本机演示库，演示库也只有虚构数据。
- 阶段④发送时，明文只允许出现在官方 API 客户端的内存里，调用完即丢。M2 骨架还没有这段解密，不要为了先跑通而在控制器里打开保险库。
- 不要给发送响应增加目的地、正文、密文或密钥字段。新增回显字段仍要先过 `PlaintextLeakGuard` 和 `SideChannelGuard`。
- 供应商密钥只来自环境变量。仓库里不要提交真实密钥；测试只能使用明显的假哨兵值。

## 12. M2 服务端消息骨架

试点前不接真实供应商。没有可用密钥时应用仍能启动。发送和存档返回 **501**，列表返回空的 200。`delivered` 和 `stored` 在骨架里恒为 false。

业务员用客户编号触发发送。服务器只在保险库里确认对应密文列非空，**不解密**。明文目的地将来只允许出现在 `WhatsAppClient` 或 `EmailClient` 的真实实现里。

### 12.1 谁可以调用

- 业务员：只能发送、存档、列出自己负责的客户。
- 经理：可以操作任意客户。
- 看不到的客户返回 404，文案与客户不存在相同。
- 未登录返回 401。

### 12.2 目的地不能出现在请求或响应里

允许的发送字段只有客户编号、模板编码、可选语言、可选正文，邮件再加主题。下面这些键，不论大小写或用 `-` / `_` 拆开，都返回 400 `DESTINATION_NOT_ACCEPTED`，消息固定为「不能提交联系方式，服务器只按客户编号发送」：

`phone`、`email`、`whatsapp`、`to`、`cc`、`bcc`、`recipient`、`destination`、`from`、`replyTo`、`waId`、`phoneNumber`、`address`、`mailto`、`e164`、`msisdn`、`contact`、`revealed`、`plaintext`。

错误体不包含被拒绝的值。其它未知字段返回 400 `VALIDATION`「请求含有不允许的字段」，同样不回显值。

正文、主题、语言沿用 `SideChannelGuard`。出现 `@` 或连续 8 位数字则 400 `PLAINTEXT_SIDE_CHANNEL`，不回显。语言只允许 `zh` 或 `en`，省略时按 `zh`。

响应继续受业务员 `PlaintextLeakGuard` 约束。骨架响应使用 `channel: WHATSAPP | EMAIL`，不使用 `phone`、`email`、`whatsapp` 这些键，也不返回脱敏号码。

### 12.3 模板、限流、审计

占位目录（两个渠道都有，未列入即 400 `TEMPLATE_NOT_APPROVED`）：

| 编码 | 用途 |
| --- | --- |
| `inquiry_followup_v1` | 询盘跟进 |
| `quote_update_v1` | 报价更新 |

这不是供应商后台里的正式模板。接通 360dialog / WATI 之前，即使编码合法也不发出。WhatsApp 正式环境只能发已批准模板，不能由业务员在请求里临时指定收件号码。

每个用户每小时默认最多 30 次 WhatsApp、60 次邮件。超出返回 429 `RATE_LIMITED`。计数在进程内存里，重启清零。多实例前要换成共享存储。

发送意图写 `MESSAGE_SEND`，存档意图写 `MESSAGE_ARCHIVE`。metadata 只允许：

| 键 | 取值 |
| --- | --- |
| `channel` | `WHATSAPP` 或 `EMAIL` |
| `templateCode` | 已批准编码，仅发送 |
| `provider` | `360dialog`、`wati`、`gmail`、`resend` 或 `unconfigured`，仅发送 |
| `direction` | `INBOUND` 或 `OUTBOUND`，仅存档 |
| `outcome` | 骨架恒为 `stub` |

不写正文、主题、密文、号码或密钥。供应商变量如果不是上面四个名称，响应和审计里都写成 `unconfigured`，避免把密钥误填进供应商字段后回显。

### 12.4 POST /api/messages/whatsapp

官方 API 占位：360dialog 或 WATI。环境变量见 `.env.example`。

```json
{ "leadId": 1, "templateCode": "inquiry_followup_v1", "language": "zh", "body": "可选备注，不会发出" }
```

客户没有 WhatsApp 密文时返回 422 `NO_CONTACT`，不带回邮箱或其它明文。

501：

```json
{
  "error": "NOT_IMPLEMENTED",
  "message": "WhatsApp 发送尚未开通",
  "leadId": 1,
  "channel": "WHATSAPP",
  "provider": "unconfigured",
  "templateCode": "inquiry_followup_v1",
  "delivered": false
}
```

`WHATSAPP_PROVIDER=360dialog` 或 `wati` 时，`provider` 改成该名称，HTTP 仍是 501。

### 12.5 POST /api/messages/email

官方 API 占位：Gmail 或 Resend。

```json
{ "leadId": 1, "templateCode": "quote_update_v1", "subject": "报价跟进", "body": "可选备注，不会发出" }
```

主题必填，最长 200。501 的形状与 WhatsApp 相同，`channel` 为 `EMAIL`，文案为「邮件发送尚未开通」。没有邮箱密文时同样 422，不回显号码。

### 12.6 列出沟通

`GET /api/leads/{id}/conversations`

```json
{ "leadId": 1, "archiveStatus": "NOT_IMPLEMENTED", "threads": [] }
```

`GET /api/leads/{id}/conversations/messages`

```json
{ "leadId": 1, "archiveStatus": "NOT_IMPLEMENTED", "messages": [] }
```

将来的线程元素是 `id`、`channel`、`direction`、`updatedAt`、`summary`。消息元素用 `createdAt` 替换 `updatedAt`。摘要不能是正文原文。经理揭盲规则与联系人相同，本骨架不返回摘要。

### 12.7 POST /api/messages/archive

入站和出站共用。只接受已经密封的正文。解析器检查 Base64 形状后丢弃密文，服务层拿不到它。

```json
{
  "leadId": 1,
  "channel": "WHATSAPP",
  "direction": "OUTBOUND",
  "bodyCipher": "BASE64",
  "bodyIv": "BASE64"
}
```

`channel` 只能是 `WHATSAPP` 或 `EMAIL`。`direction` 只能是 `INBOUND` 或 `OUTBOUND`。密文和 IV 只允许 Base64 字母，长度至少 16。不合格返回「存档内容不合法」，不回显原文。

501：

```json
{
  "error": "NOT_IMPLEMENTED",
  "message": "沟通存档尚未开通",
  "leadId": 1,
  "channel": "WHATSAPP",
  "direction": "OUTBOUND",
  "stored": false
}
```

响应不含 `bodyCipher`。存档表和 `ConversationArchivePort.append` 接通前不要把密文留在 DTO 里。渠道和 AAD 规则仍以 `docs/phases/06-conversation-archive.md` 为准。

真实客户端接通后，发送成功改为 202，`delivered: true`。在那之前保持 501。
