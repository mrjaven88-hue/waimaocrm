# 阶段⑤ 跟进提醒与 30 天公海

常量：`PublicPoolPolicy.SILENT_DAYS = 30`。

尚未实现的行为：

- 记录「有效互动」时间。只打招呼、没有询盘要素，不算有效互动。
- 满 30 天无有效互动，或客户明确拒绝，系统把阶段标为流失。
- 流失后进入公海，原业务员不再独占。
- 同时排入唤醒序列。流失成立后调用 `WakeSequencePlanner.planForLostLead`，得到第 0 / 3 / 7 天的计划，`sendEnabled` 为 false。
- `WakeSequencePlanner.dispatch` 仍会失败，不读保险库，也不发 WhatsApp / 邮件。

未接通阶段④之前不要发真消息。样例见 `docs/M3_STAGE_ABCD_SAMPLES.md`。
