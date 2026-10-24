package com.ftcrm.roadmap;

import com.ftcrm.roadmap.StagePromotionPolicy.LossReason;
import com.ftcrm.roadmap.StagePromotionPolicy.LossSignal;
import com.ftcrm.roadmap.WakeSequencePlanner.WakePlan;
import com.ftcrm.roadmap.WakeSequencePlanner.WakeStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WakeSequencePlannerTest {

    @Test
    void lostLeadGetsAPlanThatDoesNotSend() {
        var decision = StagePromotionPolicy.evaluateLoss(
                LossSignal.fromMessage("we decided not to proceed", 2));
        WakePlan plan = WakeSequencePlanner.planForLostLead(42L, decision);

        assertThat(decision.reason()).isEqualTo(LossReason.EXPLICIT_REJECT);
        assertThat(plan.leadId()).isEqualTo(42L);
        assertThat(plan.sendEnabled()).isFalse();
        assertThat(plan.steps()).extracting(WakeStep::dayOffset).containsExactly(0, 3, 7);
        assertThat(plan.steps()).extracting(WakeStep::templateKey)
                .containsExactly("wake.day0", "wake.day3", "wake.day7");
        assertThatThrownBy(() -> WakeSequencePlanner.dispatch(plan))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("尚未接通");
    }

    @Test
    void silenceAlsoSchedulesWakeWithoutTouchingForwardStage() {
        var decision = StagePromotionPolicy.evaluateLoss(new LossSignal(false, 30));
        WakePlan plan = WakeSequencePlanner.planForLostLead(7L, decision);
        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.sendEnabled()).isFalse();
    }

    @Test
    void activeLeadIsNotQueued() {
        var decision = StagePromotionPolicy.evaluateLoss(LossSignal.fromMessage("能否再降 5%", 4));
        assertThat(decision.lost()).isFalse();
        assertThatThrownBy(() -> WakeSequencePlanner.planForLostLead(7L, decision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未流失");
        assertThatThrownBy(() -> WakeSequencePlanner.plan(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}