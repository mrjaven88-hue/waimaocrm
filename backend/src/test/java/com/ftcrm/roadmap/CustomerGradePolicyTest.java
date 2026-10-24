package com.ftcrm.roadmap;

import com.ftcrm.domain.CustomerGrade;
import com.ftcrm.roadmap.CustomerGradePolicy.GradeEvidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerGradePolicyTest {

    @Test
    void recentDealIsA() {
        assertThat(CustomerGradePolicy.grade(facts(10, null, null, null, null, null, null, null, 10, 0)))
                .isEqualTo(CustomerGrade.A);
        assertThat(CustomerGradePolicy.grade(facts(90, null, null, null, null, null, null, null, 90, 5)))
                .isEqualTo(CustomerGrade.A);
    }

    @Test
    void budgetAndDeliveryAreAEvenWithoutADeal() {
        assertThat(CustomerGradePolicy.grade(facts(null, "USD 50000", "2026-12-01", null, null, null, null, null, 3, 0)))
                .isEqualTo(CustomerGrade.A);
        assertThat(CustomerGradePolicy.grade(facts(null, "USD 50000", "2026-12-01", null, null, null, null, null, 40, 4)))
                .isEqualTo(CustomerGrade.A);
    }

    @Test
    void budgetOrDeliveryAloneIsNotA() {
        assertThat(CustomerGradePolicy.grade(facts(null, "USD 50000", null, null, null, null, null, null, 4, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(null, null, "2026-12-01", null, null, null, null, null, 4, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(null, "已联系", "已联系", null, null, null, null, null, 4, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(91, null, null, null, null, null, null, null, 5, 0)))
                .isEqualTo(CustomerGrade.C);
    }

    @Test
    void openInquiryWithQuoteExchangeIsBAndPriorDealIsNot() {
        assertThat(CustomerGradePolicy.grade(facts(
                null, null, null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", "能否再降 5%", 6, 0)))
                .isEqualTo(CustomerGrade.B);
        assertThat(CustomerGradePolicy.grade(facts(
                200, null, null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", "能否再降 5%", 6, 0)))
                .isEqualTo(CustomerGrade.C);
    }

    @Test
    void quoteWithoutReplyIsNotB() {
        assertThat(CustomerGradePolicy.grade(facts(
                null, null, null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", null, 6, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(
                null, null, null, "DN50 球阀", null, null, "Q-2026-001", "we decided not to proceed", 2, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(
                null, null, null, "已联系", null, null, "Q-2026-001", "能否再降 5%", 2, 0)))
                .isEqualTo(CustomerGrade.C);
    }

    @Test
    void weakIntentInside30DaysIsC() {
        assertThat(CustomerGradePolicy.grade(facts(null, null, null, null, null, null, null, null, 0, 0)))
                .isEqualTo(CustomerGrade.C);
        assertThat(CustomerGradePolicy.grade(facts(null, null, null, null, null, null, null, null, 29, 2)))
                .isEqualTo(CustomerGrade.C);
    }

    @Test
    void silenceOrRepeatedSilenceIsD() {
        assertThat(CustomerGradePolicy.grade(facts(
                null, null, null, "DN50 球阀", "20 柜", "Rotterdam", "Q-2026-001", "能否再降 5%", 30, 0)))
                .isEqualTo(CustomerGrade.D);
        assertThat(CustomerGradePolicy.grade(facts(null, null, null, null, null, null, null, null, 1, 3)))
                .isEqualTo(CustomerGrade.D);
        assertThat(CustomerGradePolicy.grade(facts(91, null, null, null, null, null, null, null, 30, 0)))
                .isEqualTo(CustomerGrade.D);
        assertThat(CustomerGradePolicy.grade(facts(null, null, null, "DN50 球阀", null, null, null, null, 45, 0)))
                .isEqualTo(CustomerGrade.D);
    }

    @Test
    void negativeFactsAreRejected() {
        assertThatThrownBy(() -> CustomerGradePolicy.grade(facts(null, null, null, null, null, null, null, null, -1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomerGradePolicy.grade(facts(-1, null, null, null, null, null, null, null, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GradeEvidence facts(
            Integer daysSinceLastDeal,
            String budget,
            String delivery,
            String product,
            String quantity,
            String port,
            String quotation,
            String reply,
            int daysSinceInteraction,
            int unanswered
    ) {
        return new GradeEvidence(
                daysSinceLastDeal, budget, delivery, product, quantity, port, quotation, reply,
                daysSinceInteraction, unanswered);
    }
}