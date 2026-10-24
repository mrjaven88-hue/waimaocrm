# M2 消息骨架

试点目标约 11 月 6 日。这一层只把发送和存档的服务端边界立起来，**不连接** 360dialog、WATI、Gmail 或 Resend，也**不解密**联系人保险库。M1 的揭盲、脱敏和业务员响应守卫保持原样。

接口合同以 `docs/TECH_SPEC_MVP.md` 第 12 节为准。这里只说明代码落在哪、现在故意没做什么、以后怎么接。

## 现在的行为

| 入口 | 结果 |
| --- | --- |
| `POST /api/messages/whatsapp` | 校验通过后 **501**，`delivered: false` |
| `POST /api/messages/email` | 同上 |
| `GET /api/leads/{id}/conversations` | **200**，线程列表为空，`archiveStatus: NOT_IMPLEMENTED` |
| `GET /api/leads/{id}/conversations/messages` | **200**，消息列表为空 |
| `POST /api/messages/archive` | 校验密文形状后丢弃内容，**501**，`stored: false` |

业务员只能操作自己负责的客户，经理可以操作任意客户。别人的客户返回 404。请求里出现目的地字段返回 400，响应不回显原文。

供应商名称只接受 `360dialog`、`wati`、`gmail`、`resend`。其它值（包括误把密钥填进供应商变量）都按 `unconfigured` 处理。密钥环境变量见 `.env.example`，默认为空，不阻止启动。

## 代码

| 类型 | 类 |
| --- | --- |
| HTTP | `MessageController`、`ConversationController` |
| 用例 | `MessagingService`、`ConversationQueryService` |
| 请求 | `MessageRequestParser`、`DestinationFieldGuard` |
| 保险库 | `VaultDestinationGate.requireSealed`：只确认密文列非空，返回 void |
| 模板 | `ApprovedTemplateCatalog`：`inquiry_followup_v1`、`quote_update_v1` |
| 限流 | `SendRateLimiter`：每用户每小时，默认 WhatsApp 30、邮件 60，进程内计数 |
| 客户端 | `WhatsAppClient` / `StubWhatsAppClient`，`EmailClient` / `StubEmailClient` |

`OutboundMessagePort` 和 `ConversationArchivePort` 仍是会失败的旧占位，HTTP 骨架还没有调用它们。

## 接通供应商时要做的事

1. 用新的 `WhatsAppClient` 替换 `StubWhatsAppClient`。只在 `sendTemplate` 内部按 `leadId` 解密 WhatsApp 号码，调用 360dialog 或 WATI 的已批准模板接口，调用结束即丢掉明文。`ProviderAck` 不能增加号码、正文或密钥字段。
2. 用新的 `EmailClient` 替换 `StubEmailClient`。Gmail 走 OAuth 环境变量，Resend 走 `RESEND_API_KEY`。同样只在客户端内存里解密收件地址。
3. 解密结果不能进入控制器、DTO、审计 metadata、异常消息或日志。业务员响应继续经过 `PlaintextLeakGuard`。
4. 发送成功后把正文密封，调用 `ConversationArchivePort.append`。AAD 带上 leadId 和消息 id。在此之前不要把 `bodyCipher` 从解析器传到服务层。
5. 入站 webhook 单独做供应商签名校验，不要复用业务员 JWT，也不要把 webhook 正文回显给调用方。
6. 限流在多实例上会失真。试点如果开两台应用，先换成共享计数。
7. 模板编码换成供应商后台的正式名称后再加入 `ApprovedTemplateCatalog`。未列入的编码必须继续 400。
8. 真实发送的 HTTP 状态用 202，`delivered: true`。在那之前保持 501。
9. 不要把密钥写进仓库、镜像或 `dev` 档案。生产继续禁止 `dev` 档案。
