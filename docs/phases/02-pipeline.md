# 阶段② 客户管道

本阶段不改客户阶段，也没有升迁接口。规则已经写在 `docs/TECH_SPEC_MVP.md`，并用纯函数锁在：

- `backend/src/main/java/com/ftcrm/roadmap/StagePromotionPolicy.java`

接入时只允许调用这个策略，不能在接口里用手写 if 跳级。给 `LeadService` 的入口是 `forward(LeadStage, CommercialEvidence)`：品名、数量、目的港、报价、客户回复、订单或合同号、货运单证号、签收或到货反馈都是原文。备注只有「已联系」不能升迁。

样例和 A/B/C/D 在 `docs/M3_STAGE_ABCD_SAMPLES.md`，等级计算在 `CustomerGradePolicy`。流失用 `evaluateLoss`，不要和正向升迁写在同一次更新里。客户保存接口仍然没接。

建议的后续接口：

- `POST /api/leads/{id}/promote`，请求体只带证据，不带目标阶段。服务端算出下一阶段。
- 业务员响应仍然不返回联系方式明文。
