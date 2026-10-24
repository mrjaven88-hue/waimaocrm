package com.ftcrm.roadmap;

import com.ftcrm.domain.CustomerGrade;

import java.util.Objects;

/**
 * A/B/C/D 等级。只看证据，不接受手填等级。
 * <p>
 * 优先级：A，然后 D，然后 B，然后 C，其余为 D。
 * 近 90 天成交，或同时写明预算和交货期，记为 A，不再因为沉默降成 D。
 * 未到 A 时，满 {@link PublicPoolPolicy#SILENT_DAYS} 天无有效互动，或连续未回复达到
 * {@link #UNANSWERED_STREAK_FOR_D} 次，记为 D，此时即使有报价往来也不记为 B。
 * 样例未写「多次」的具体次数，这里定为连续 3 次。
 * B 还要求从未成交：{@code daysSinceLastDeal == null}。近 90 天的成交已在 A 命中。
 */
public final class CustomerGradePolicy {

    public static final int RECENT_DEAL_DAYS = 90;
    public static final int UNANSWERED_STREAK_FOR_D = 3;

    private CustomerGradePolicy() {
    }

    public static CustomerGrade grade(GradeEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.daysSinceValidInteraction() < 0 || evidence.consecutiveUnanswered() < 0) {
            throw new IllegalArgumentException("互动天数和连续未回复次数不能为负");
        }
        if (evidence.daysSinceLastDeal() != null && evidence.daysSinceLastDeal() < 0) {
            throw new IllegalArgumentException("距成交天数不能为负");
        }
        if (matchesA(evidence)) {
            return CustomerGrade.A;
        }
        if (matchesD(evidence)) {
            return CustomerGrade.D;
        }
        if (matchesB(evidence)) {
            return CustomerGrade.B;
        }
        if (evidence.daysSinceValidInteraction() < PublicPoolPolicy.SILENT_DAYS) {
            return CustomerGrade.C;
        }
        return CustomerGrade.D;
    }

    private static boolean matchesA(GradeEvidence evidence) {
        boolean recentDeal = evidence.daysSinceLastDeal() != null
                && evidence.daysSinceLastDeal() <= RECENT_DEAL_DAYS;
        boolean budgetAndDelivery = StagePromotionPolicy.hasCommercialFact(evidence.budget())
                && StagePromotionPolicy.hasCommercialFact(evidence.deliveryDate());
        return recentDeal || budgetAndDelivery;
    }

    private static boolean matchesB(GradeEvidence evidence) {
        if (evidence.daysSinceLastDeal() != null) {
            return false;
        }
        return StagePromotionPolicy.hasSubstantiveInquiry(
                evidence.product(), evidence.quantity(), evidence.destinationPort())
                && StagePromotionPolicy.hasQuoteExchange(evidence.quotation(), evidence.customerReply());
    }

    private static boolean matchesD(GradeEvidence evidence) {
        return evidence.daysSinceValidInteraction() >= PublicPoolPolicy.SILENT_DAYS
                || evidence.consecutiveUnanswered() >= UNANSWERED_STREAK_FOR_D;
    }

    /**
     * @param daysSinceLastDeal      从未成交时为 null；0 表示当天成交
     * @param budget                 客户写明的预算原文，空或「已联系」不算
     * @param deliveryDate           客户写明的交货期原文，空或「已联系」不算
     * @param consecutiveUnanswered  连续未回复次数，对方一有实质回复就应由调用方归零
     */
    public record GradeEvidence(
            Integer daysSinceLastDeal,
            String budget,
            String deliveryDate,
            String product,
            String quantity,
            String destinationPort,
            String quotation,
            String customerReply,
            int daysSinceValidInteraction,
            int consecutiveUnanswered
    ) {
    }
}
