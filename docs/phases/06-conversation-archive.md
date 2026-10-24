# 阶段⑥ 沟通存档

占位接口 `ConversationArchivePort.append(leadId, channel, bodyCipher, bodyIv)` 只接受密文。

`UnsupportedConversationArchive` 会拒绝写入。

M2 另有 HTTP 骨架，仍然不落库：

- `GET /api/leads/{id}/conversations` 和 `GET /api/leads/{id}/conversations/messages` 返回空列表。
- `POST /api/messages/archive` 只接受 `WHATSAPP` / `EMAIL`、方向和 Base64 密文。校验后丢弃密文，返回 501，响应不回显密文。

说明见 `docs/M2_MESSAGING_SKELETON.md`。

接入时：

- 渠道只允许 `whatsapp` 和 `email`。
- 正文用和联系人保险库相同的 AES-GCM，AAD 带上 leadId 和消息 id。
- 列表给业务员的是时间、方向、渠道和摘要，不是原文，除非经理处于揭盲会话中。揭盲规则与联系人相同：原因、15 分钟、审计。
