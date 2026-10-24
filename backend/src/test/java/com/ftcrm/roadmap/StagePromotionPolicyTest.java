package com.ftcrm.roadmap;

import com.ftcrm.common.ApiException;
import com.ftcrm.domain.LeadStage;
import com.ftcrm.roadmap.StagePromotionPolicy.CommercialEvidence;
import com.ftcrm.roadmap.StagePromotionPolicy.ForwardSignal;
import com.ftcrm.roadmap.StagePromotionPolicy.LossDecision;
import com.ftcrm.roadmap.StagePromotionPolicy.LossReason;
import com.ftcrm.roadmap.StagePromotionPolicy.LossSignal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagePromotionPolicyTest {

    @Test
    void contactedAloneCannotAdvance() {
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.LEAD, signal(true, false, false, false, false)))
                .isInstanceOf(ApiException.class)
                .hasMessage("仅「已联系」不能升迁");
    }

    @Test
    void inquiryNeedsOneCommercialFact() {
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD, signal(false, true, true, false, false)))
                .isEqualTo(LeadStage.INQUIRY);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.LEAD, signal(false, true, false, false, false)))
                .hasMessageContaining("目的港");
    }

    @Test
    void laterStagesNeedTheirDocuments() {
        ForwardSignal quoted = new ForwardSignal(false, true, true, false, false, true, true, null, null, false);
        assertThat(StagePromotionPolicy.forward(LeadStage.INQUIRY, quoted)).isEqualTo(LeadStage.NEGOTIATION);
        ForwardSignal won = new ForwardSignal(false, false, false, false, false, false, false, "SO-100", null, false);
        assertThat(StagePromotionPolicy.forward(LeadStage.NEGOTIATION, won)).isEqualTo(LeadStage.WON);
        ForwardSignal shipped = new ForwardSignal(false, false, false, false, false, false, false, null, "BL-9", false);
        assertThat(StagePromotionPolicy.forward(LeadStage.WON, shipped)).isEqualTo(LeadStage.SHIPPED);
        ForwardSignal after = new ForwardSignal(false, false, false, false, false, false, false, null, null, true);
        assertThat(StagePromotionPolicy.forward(LeadStage.SHIPPED, after)).isEqualTo(LeadStage.AFTERSALES);
    }

    @Test
    void lossIsSeparateFromForwardPromotion() {
        assertThat(StagePromotionPolicy.shouldMarkLost(new LossSignal(true, 1))).isTrue();
        assertThat(StagePromotionPolicy.shouldMarkLost(new LossSignal(false, 30))).isTrue();
        assertThat(StagePromotionPolicy.shouldMarkLost(new LossSignal(false, 29))).isFalse();
    }

    @Test
    void contactedNoteAloneCannotAdvance() {
        CommercialEvidence contacted = evidence("已联系。", null, null, null, null, null, null, null, null);
        assertThat(StagePromotionPolicy.isContactedOnlyNote("已联系")).isTrue();
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.LEAD, contacted))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "STAGE_BLOCKED")
                .hasMessage("仅「已联系」不能升迁");
        assertThat(StagePromotionPolicy.countsAsValidInteraction("已联系", contacted)).isFalse();
        assertThat(StagePromotionPolicy.countsAsValidInteraction("你好", evidence(null, null, null, null, null, null, null, null, null)))
                .isFalse();
    }

    @Test
    void oneInquiryFactAdvancesEvenIfNoteSaysContacted() {
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD,
                evidence("已联系", "DN50 球阀", null, null, null, null, null, null, null)))
                .isEqualTo(LeadStage.INQUIRY);
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD,
                evidence(null, null, "20 柜", null, null, null, null, null, null)))
                .isEqualTo(LeadStage.INQUIRY);
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD,
                evidence(null, null, null, "Rotterdam", null, null, null, null, null)))
                .isEqualTo(LeadStage.INQUIRY);
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD,
                evidence(null, "DN50 球阀", "20 柜", "Rotterdam", null, null, null, null, null)))
                .isEqualTo(LeadStage.INQUIRY);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.LEAD,
                evidence(null, "已联系", null, null, null, null, null, null, null)))
                .hasMessage("仅「已联系」不能升迁");
    }

    @Test
    void quoteWithoutReplyDoesNotReachNegotiation() {
        CommercialEvidence quoteOnly = evidence(null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", null, null, null, null);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.INQUIRY, quoteOnly))
                .hasMessage("询盘进入谈判需要已报价且客户已回复");
        assertThat(StagePromotionPolicy.hasQuoteExchange("Q-2026-001", null)).isFalse();
        CommercialEvidence quoteWithoutNewFacts = evidence(null, null, null, null, "Q-2026-001", null, null, null, null);
        assertThat(StagePromotionPolicy.countsAsValidInteraction(null, quoteWithoutNewFacts)).isFalse();
    }

    @Test
    void counterOfferReachesNegotiationButRejectionDoesNot() {
        CommercialEvidence counter = evidence(null, "DN50 球阀", "20 柜", "Rotterdam",
                "Q-2026-001", "能否再降 5%", null, null, null);
        assertThat(StagePromotionPolicy.forward(LeadStage.INQUIRY, counter)).isEqualTo(LeadStage.NEGOTIATION);

        CommercialEvidence repliedWithContacted = evidence(null, "DN50 球阀", "20 柜", "Rotterdam",
                "Q-2026-001", "已联系", null, null, null);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.INQUIRY, repliedWithContacted))
                .hasMessageContaining("客户已回复");

        CommercialEvidence rejected = evidence(null, "DN50 球阀", "20 柜", "Rotterdam",
                "Q-2026-001", "we decided not to proceed", null, null, null);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.INQUIRY, rejected))
                .hasMessageContaining("客户已回复");
        assertThat(StagePromotionPolicy.hasQuoteExchange("Q-2026-001", rejected.customerReply())).isFalse();
    }

    @Test
    void documentsAdvanceOneStageOnly() {
        CommercialEvidence full = evidence(null, "DN50 球阀", "20 柜", "Rotterdam",
                "Q-2026-001", "能否再降 5%", "CT-8891", "BL-77821", "签收确认");
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD, full)).isEqualTo(LeadStage.INQUIRY);
        assertThat(StagePromotionPolicy.forward(LeadStage.INQUIRY, full)).isEqualTo(LeadStage.NEGOTIATION);
        assertThat(StagePromotionPolicy.forward(LeadStage.NEGOTIATION, full)).isEqualTo(LeadStage.WON);
        assertThat(StagePromotionPolicy.forward(LeadStage.WON, full)).isEqualTo(LeadStage.SHIPPED);
        assertThat(StagePromotionPolicy.forward(LeadStage.SHIPPED, full)).isEqualTo(LeadStage.AFTERSALES);
        assertThat(StagePromotionPolicy.forward(LeadStage.NEGOTIATION, full)).isNotEqualTo(LeadStage.LOST);
    }

    @Test
    void missingDocumentsStayPut() {
        CommercialEvidence verbalOnly = evidence("口头意向", "DN50 球阀", "20 柜", "Rotterdam",
                "Q-2026-001", "能否再降 5%", null, null, null);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.NEGOTIATION, verbalOnly))
                .hasMessage("谈判进入成交需要订单或合同号");
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.NEGOTIATION,
                evidence(null, null, null, null, null, null, "已联系", null, null)))
                .hasMessageContaining("订单或合同号");
        assertThat(StagePromotionPolicy.forward(LeadStage.NEGOTIATION,
                evidence(null, null, null, null, null, null, "CT-8891", null, null)))
                .isEqualTo(LeadStage.WON);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.WON,
                evidence(null, null, null, null, null, null, "CT-8891", null, null)))
                .hasMessage("成交进入发货需要货运单证号");
        assertThat(StagePromotionPolicy.forward(LeadStage.WON,
                evidence(null, null, null, null, null, null, null, "BL-77821", null)))
                .isEqualTo(LeadStage.SHIPPED);
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.SHIPPED,
                evidence(null, null, null, null, null, null, null, "BL-77821", null)))
                .hasMessage("发货进入售后需要签收或反馈");
        assertThat(StagePromotionPolicy.forward(LeadStage.SHIPPED,
                evidence(null, null, null, null, null, null, null, null, "到货反馈")))
                .isEqualTo(LeadStage.AFTERSALES);
    }

    @Test
    void terminalStagesCannotUseForwardPath() {
        CommercialEvidence full = evidence(null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", "能否再降 5%", "CT-8891", "BL-77821", "签收确认");
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.AFTERSALES, full))
                .hasMessage("当前阶段不能沿主路径升迁");
        assertThatThrownBy(() -> StagePromotionPolicy.forward(LeadStage.LOST, full))
                .hasMessage("当前阶段不能沿主路径升迁");
    }

    @Test
    void explicitRejectionCopyMarksLostWithoutChangingForwardResult() {
        LossDecision decision = StagePromotionPolicy.evaluateLoss(
                LossSignal.fromMessage("We decided not to proceed with this order.", 1));
        assertThat(decision.lost()).isTrue();
        assertThat(decision.reason()).isEqualTo(LossReason.EXPLICIT_REJECT);
        assertThat(StagePromotionPolicy.shouldMarkLost(
                LossSignal.fromMessage("We decided not to proceed with this order.", 1))).isTrue();

        CommercialEvidence inquiry = evidence("we decided not to proceed", "DN50 球阀", null, null, null, null, null, null, null);
        assertThat(StagePromotionPolicy.forward(LeadStage.LEAD, inquiry)).isEqualTo(LeadStage.INQUIRY);
        assertThat(StagePromotionPolicy.evaluateLoss(LossSignal.fromMessage(inquiry.notes(), 0)).reason())
                .isEqualTo(LossReason.EXPLICIT_REJECT);
    }

    @Test
    void silenceMarksLostOnDay30AndNotBefore() {
        LossDecision silent = StagePromotionPolicy.evaluateLoss(LossSignal.fromMessage("能否再降 5%", 30));
        assertThat(silent.lost()).isTrue();
        assertThat(silent.reason()).isEqualTo(LossReason.NO_VALID_INTERACTION);

        LossDecision active = StagePromotionPolicy.evaluateLoss(LossSignal.fromMessage("能否再降 5%", 29));
        assertThat(active.lost()).isFalse();
        assertThat(active.reason()).isEqualTo(LossReason.NONE);

        LossDecision both = StagePromotionPolicy.evaluateLoss(
                new LossSignal(true, 40, "we decided not to proceed"));
        assertThat(both.reason()).isEqualTo(LossReason.EXPLICIT_REJECT);
    }

    @Test
    void otherRejectionPhrasesCountAndOrdinaryReplyDoesNot() {
        assertThat(StagePromotionPolicy.isExplicitRejection("we decided not to proceed")).isTrue();
        assertThat(StagePromotionPolicy.isExplicitRejection("We Decided Not To Proceed")).isTrue();
        assertThat(StagePromotionPolicy.isExplicitRejection("能否再降 5%")).isFalse();
        assertThat(StagePromotionPolicy.isExplicitRejection("not interested")).isFalse();
        assertThatThrownBy(() -> new LossSignal(false, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ForwardSignal signal(boolean contactedOnly, boolean inquiry, boolean product, boolean qty, boolean port) {
        return new ForwardSignal(contactedOnly, inquiry, product, qty, port, false, false, null, null, false);
    }

    private static CommercialEvidence evidence(
            String notes,
            String product,
            String quantity,
            String port,
            String quotation,
            String reply,
            String order,
            String shipping,
            String receipt
    ) {
        return new CommercialEvidence(notes, product, quantity, port, quotation, reply, order, shipping, receipt);
    }
}
