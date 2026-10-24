package com.ftcrm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LeadDtos {

    private LeadDtos() {
    }

    public record LeadWriteRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 80) String country,
            @NotBlank @Size(max = 80) String contactName,
            @Size(max = 40) String phone,
            @Size(max = 120) String email,
            @Size(max = 40) String whatsapp,
            @Size(max = 200) String productInterest,
            @Size(max = 80) String source,
            @Size(max = 2000) String notes
    ) {
    }

    /**
     * 升迁 / 流失证据。不带目标阶段或目标等级；同一次只走流失或一阶升迁之一。
     */
    public record StageEvidenceRequest(
            @Size(max = 2000) String notes,
            @Size(max = 200) String product,
            @Size(max = 80) String quantity,
            @Size(max = 120) String destinationPort,
            @Size(max = 200) String quotation,
            @Size(max = 2000) String customerReply,
            @Size(max = 120) String orderOrContractNumber,
            @Size(max = 120) String shippingDocumentNumber,
            @Size(max = 500) String receiptOrArrivalNote,
            Boolean explicitReject,
            @Min(0) Integer daysSinceValidInteraction,
            Integer daysSinceLastDeal,
            @Size(max = 200) String budget,
            @Size(max = 120) String deliveryDate,
            @Min(0) Integer consecutiveUnanswered
    ) {
    }

    public record ImportRequest(
            @NotEmpty @Size(max = 100) List<@Valid LeadWriteRequest> leads
    ) {
    }

    public record MaskedContact(String phoneMasked, String emailMasked, String whatsappMasked) {
    }

    public record RevealedContact(String phone, String email, String whatsapp) {
    }

    public record UnmaskState(boolean active, Long sessionId, Instant expiresAt) {
    }

    public record WakeStepView(int dayOffset, String templateKey) {
    }

    public record WakePlanView(long leadId, List<WakeStepView> steps, boolean sendEnabled) {
    }

    public record LeadDetailResponse(
            Long id,
            String companyName,
            String country,
            String contactName,
            String stage,
            String stageLabel,
            String grade,
            String gradeLabel,
            String productInterest,
            String source,
            String notes,
            Long ownerId,
            String ownerName,
            Instant createdAt,
            Instant updatedAt,
            MaskedContact contact,
            UnmaskState unmask,
            RevealedContact revealed,
            WakePlanView wakePlan
    ) {
    }

    public record RowError(int index, String code, String message) {
    }

    public record ImportResponse(
            int imported,
            int duplicated,
            int failed,
            List<LeadDetailResponse> leads,
            List<RowError> errors
    ) {
    }

    public record UnmaskRequest(
            @NotBlank @Size(min = 8, max = 200) String reason
    ) {
    }
}
