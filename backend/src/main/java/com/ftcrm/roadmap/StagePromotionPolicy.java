package com.ftcrm.roadmap;

import com.ftcrm.common.ApiException;
import com.ftcrm.domain.LeadStage;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Objects;

/**
 * 阶段升迁与流失判定。两者是两条路径：{@link #forward} 只沿主路径走一步，
 * {@link #evaluateLoss} 只判断流失，不返回下一正向阶段。
 * 同一次客户保存只能采用其中一个结果。流失成立时不要把 {@code forward} 的返回值写回阶段。
 * <p>
 * 业务样例见 {@code docs/M3_STAGE_ABCD_SAMPLES.md}。尚未接到客户更新接口。
 */
public final class StagePromotionPolicy {

    public static final int LOST_AFTER_DAYS = PublicPoolPolicy.SILENT_DAYS;

    /**
     * 样例文档写死的拒绝原文。忽略大小写和多余空白，允许包在更长回复里。
     * 不另加近义词。
     */
    private static final String REJECTION_PHRASE = "we decided not to proceed";

    private static final String CONTACTED_ONLY_NOTE = "已联系";

    private StagePromotionPolicy() {
    }

    public static LossDecision evaluateLoss(LossSignal signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal.explicitReject() || isExplicitRejection(signal.customerMessage())) {
            return new LossDecision(true, LossReason.EXPLICIT_REJECT);
        }
        if (signal.daysSinceValidInteraction() >= LOST_AFTER_DAYS) {
            return new LossDecision(true, LossReason.NO_VALID_INTERACTION);
        }
        return new LossDecision(false, LossReason.NONE);
    }

    public static boolean shouldMarkLost(LossSignal signal) {
        return evaluateLoss(signal).lost();
    }

    public static LeadStage forward(LeadStage from, ForwardSignal signal) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(signal, "signal");
        return switch (from) {
            case LEAD -> toInquiry(signal);
            case INQUIRY -> {
                if (signal.quoted() && signal.customerReplied()) {
                    yield LeadStage.NEGOTIATION;
                }
                throw blocked("询盘进入谈判需要已报价且客户已回复");
            }
            case NEGOTIATION -> {
                if (hasCommercialFact(signal.orderNumber())) {
                    yield LeadStage.WON;
                }
                throw blocked("谈判进入成交需要订单或合同号");
            }
            case WON -> {
                if (hasCommercialFact(signal.shippingDocNumber())) {
                    yield LeadStage.SHIPPED;
                }
                throw blocked("成交进入发货需要货运单证号");
            }
            case SHIPPED -> {
                if (signal.receiptOrFeedback()) {
                    yield LeadStage.AFTERSALES;
                }
                throw blocked("发货进入售后需要签收或反馈");
            }
            case AFTERSALES, LOST -> throw blocked("当前阶段不能沿主路径升迁");
        };
    }

    /**
     * 给后续 {@code LeadService} 用的入口。证据来自字段原文，不接受「已联系」勾选。
     * 只升迁一阶，合同号或运单号不会让线索直接跳到成交或发货。
     */
    public static LeadStage forward(LeadStage from, CommercialEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return forward(from, toSignal(evidence));
    }

    public static boolean isContactedOnlyNote(String notes) {
        if (notes == null || notes.isBlank()) {
            return false;
        }
        return CONTACTED_ONLY_NOTE.equals(normalizeToken(notes));
    }

    public static boolean isExplicitRejection(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.contains(REJECTION_PHRASE);
    }

    /**
     * 非空、且不是单独的「已联系」。产品、数量、目的港、单号、预算都用这一条。
     */
    public static boolean hasCommercialFact(String value) {
        return value != null && !value.isBlank() && !isContactedOnlyNote(value);
    }

    public static boolean hasSubstantiveInquiry(String product, String quantity, String destinationPort) {
        return hasCommercialFact(product) || hasCommercialFact(quantity) || hasCommercialFact(destinationPort);
    }

    public static boolean hasSubstantiveReply(String customerReply) {
        return hasCommercialFact(customerReply) && !isExplicitRejection(customerReply);
    }

    /** 已报价且对方有实质回复。拒绝文案和「已联系」都不算报价往来。 */
    public static boolean hasQuoteExchange(String quotation, String customerReply) {
        return hasCommercialFact(quotation) && hasSubstantiveReply(customerReply);
    }

    /**
     * 这一次新发生的内容算不算有效互动。档案上早已记下的产品不能传进来充数，
     * 否则「你好」也会把 30 天时钟清零。
     * 只打招呼、仅「已联系」、或只有我方报价而对方没回复，都返回 false。
     * 明确拒绝返回 true，同时由流失路径处理。
     */
    public static boolean countsAsValidInteraction(String message, CommercialEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (isExplicitRejection(message) || isExplicitRejection(evidence.notes())
                || isExplicitRejection(evidence.customerReply())) {
            return true;
        }
        if (hasSubstantiveInquiry(evidence.product(), evidence.quantity(), evidence.destinationPort())) {
            return true;
        }
        if (hasQuoteExchange(evidence.quotation(), evidence.customerReply())) {
            return true;
        }
        return hasCommercialFact(evidence.orderOrContractNumber())
                || hasCommercialFact(evidence.shippingDocumentNumber())
                || hasCommercialFact(evidence.receiptOrArrivalNote());
    }

    private static LeadStage toInquiry(ForwardSignal signal) {
        if (signal.contactedOnly() && !signal.substantiveInquiry()) {
            throw blocked("仅「已联系」不能升迁");
        }
        if (!signal.substantiveInquiry()) {
            throw blocked("线索进入询盘需要有效询盘，且品名、数量、目的港至少一项");
        }
        return LeadStage.INQUIRY;
    }

    private static ForwardSignal toSignal(CommercialEvidence evidence) {
        boolean product = hasCommercialFact(evidence.product());
        boolean quantity = hasCommercialFact(evidence.quantity());
        boolean port = hasCommercialFact(evidence.destinationPort());
        boolean contactedMarker = isContactedOnlyNote(evidence.notes())
                || isContactedOnlyNote(evidence.product())
                || isContactedOnlyNote(evidence.quantity())
                || isContactedOnlyNote(evidence.destinationPort());
        boolean contactedOnly = contactedMarker && !product && !quantity && !port;
        return new ForwardSignal(
                contactedOnly,
                product || quantity || port,
                product,
                quantity,
                port,
                hasCommercialFact(evidence.quotation()),
                hasSubstantiveReply(evidence.customerReply()),
                evidence.orderOrContractNumber(),
                evidence.shippingDocumentNumber(),
                hasCommercialFact(evidence.receiptOrArrivalNote())
        );
    }

    private static String normalizeToken(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\u3000\\p{Punct}，。！？、；：]+", "");
    }

    private static ApiException blocked(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "STAGE_BLOCKED", message);
    }

    public enum LossReason {
        NONE,
        EXPLICIT_REJECT,
        NO_VALID_INTERACTION
    }

    public record LossDecision(boolean lost, LossReason reason) {
        public LossDecision {
            Objects.requireNonNull(reason, "reason");
            boolean reasonSaysLost = reason != LossReason.NONE;
            if (lost != reasonSaysLost) {
                throw new IllegalArgumentException("流失标记与原因不一致");
            }
        }
    }

    public record ForwardSignal(
            boolean contactedOnly,
            boolean inquiryReceived,
            boolean hasProduct,
            boolean hasQuantity,
            boolean hasDestinationPort,
            boolean quoted,
            boolean customerReplied,
            String orderNumber,
            String shippingDocNumber,
            boolean receiptOrFeedback
    ) {
        public boolean substantiveInquiry() {
            return inquiryReceived && (hasProduct || hasQuantity || hasDestinationPort);
        }
    }

    /**
     * 字段里的原文。空和「已联系」都不是商业事实。
     */
    public record CommercialEvidence(
            String notes,
            String product,
            String quantity,
            String destinationPort,
            String quotation,
            String customerReply,
            String orderOrContractNumber,
            String shippingDocumentNumber,
            String receiptOrArrivalNote
    ) {
    }

    public record LossSignal(boolean explicitReject, int daysSinceValidInteraction, String customerMessage) {
        public LossSignal {
            if (daysSinceValidInteraction < 0) {
                throw new IllegalArgumentException("距上次有效互动的天数不能为负");
            }
        }

        public LossSignal(boolean explicitReject, int daysSinceValidInteraction) {
            this(explicitReject, daysSinceValidInteraction, null);
        }

        public static LossSignal fromMessage(String customerMessage, int daysSinceValidInteraction) {
            return new LossSignal(false, daysSinceValidInteraction, customerMessage);
        }
    }
}
