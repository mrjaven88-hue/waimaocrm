package com.ftcrm.roadmap;

import com.ftcrm.roadmap.StagePromotionPolicy.LossDecision;

import java.util.List;
import java.util.Objects;

/**
 * 流失后的唤醒序列。只产出计划，不读保险库，也不发送 WhatsApp / 邮件。
 */
public final class WakeSequencePlanner {

    public static final List<Integer> DAY_OFFSETS = List.of(0, 3, 7);

    private WakeSequencePlanner() {
    }

    /**
     * 日程本身。业务入口用 {@link #planForLostLead}，避免给未流失客户排队。
     */
    public static WakePlan plan(long leadId) {
        if (leadId <= 0) {
            throw new IllegalArgumentException("leadId 必须为正数");
        }
        List<WakeStep> steps = DAY_OFFSETS.stream()
                .map(day -> new WakeStep(day, "wake.day" + day))
                .toList();
        return new WakePlan(leadId, steps, false);
    }

    public static WakePlan planForLostLead(long leadId, LossDecision decision) {
        if (decision == null || !decision.lost()) {
            throw new IllegalArgumentException("未流失，不排唤醒序列");
        }
        return plan(leadId);
    }

    /**
     * 真正发信仍是占位。调用即失败，不接触联系方式。
     */
    public static void dispatch(WakePlan plan) {
        Objects.requireNonNull(plan, "plan");
        throw new UnsupportedOperationException("唤醒序列只产出计划，WhatsApp/邮件发送尚未接通");
    }

    public record WakeStep(int dayOffset, String templateKey) {
    }

    public record WakePlan(long leadId, List<WakeStep> steps, boolean sendEnabled) {
        public WakePlan {
            if (leadId <= 0) {
                throw new IllegalArgumentException("leadId 必须为正数");
            }
            steps = List.copyOf(steps);
            if (sendEnabled) {
                throw new IllegalArgumentException("唤醒发送尚未开通");
            }
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("唤醒计划不能为空");
            }
        }
    }
}
