# 阶段④ 服务端代发 WhatsApp / 邮件

业务员不能把客户号码复制出去再在个人 WhatsApp 或邮箱里发送。发送必须走服务端官方 API。

当前是 M2 骨架，不会对外发送。合同在 `docs/TECH_SPEC_MVP.md` 第 12 节，接线说明在 `docs/M2_MESSAGING_SKELETON.md`。

- `POST /api/messages/whatsapp` 与 `POST /api/messages/email` 只收客户编号，返回 501。
- `WhatsAppClient` / `EmailClient` 的占位实现只确认保险库有密文，不解密。
- `OutboundMessagePort` / `UnsupportedOutboundGateway` 仍会直接失败，留给真实网关替换。

接入时：

1. 只在真实的官方 API 客户端里解密。明文只活在这次调用的内存里，业务员接口不能触发揭盲来换取号码。
2. 不把明文写进业务员 API、审计 metadata、应用日志。
3. 发送正文进入阶段⑥存档时只存密文。
4. 密钥放在环境变量或 KMS，不进仓库。
