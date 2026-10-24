package com.ftcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.ContactVaultEntry;
import com.ftcrm.domain.CustomerGrade;
import com.ftcrm.domain.Lead;
import com.ftcrm.domain.Role;
import com.ftcrm.domain.UnmaskSession;
import com.ftcrm.security.AuthUser;
import com.ftcrm.vault.ContactCryptoService;
import com.ftcrm.web.dto.LeadDtos.LeadDetailResponse;
import com.ftcrm.web.dto.LeadDtos.MaskedContact;
import com.ftcrm.web.dto.LeadDtos.RevealedContact;
import com.ftcrm.web.dto.LeadDtos.UnmaskState;
import com.ftcrm.web.dto.LeadDtos.WakePlanView;
import com.ftcrm.web.dto.LeadDtos.WakeStepView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LeadViewMapper {

    private final ContactCryptoService crypto;
    private final ObjectMapper mapper;

    public LeadViewMapper(ContactCryptoService crypto, ObjectMapper mapper) {
        this.crypto = crypto;
        this.mapper = mapper;
    }

    public LeadDetailResponse toView(Lead lead, ContactVaultEntry vault, AuthUser viewer,
                                      UnmaskSession session, boolean allowReveal, String ownerName) {
        boolean manager = viewer.role() == Role.MANAGER;
        UnmaskState state = manager && session != null
                ? new UnmaskState(true, session.getId(), session.getExpiresAt())
                : new UnmaskState(false, null, null);
        RevealedContact revealed = null;
        if (manager && allowReveal && session != null) {
            long leadId = lead.getId();
            revealed = new RevealedContact(
                    crypto.open(vault.getPhoneCipher(), vault.getPhoneIv(), ContactCryptoService.aad(leadId, "phone")),
                    crypto.open(vault.getEmailCipher(), vault.getEmailIv(), ContactCryptoService.aad(leadId, "email")),
                    crypto.open(vault.getWhatsappCipher(), vault.getWhatsappIv(), ContactCryptoService.aad(leadId, "whatsapp")));
        }
        CustomerGrade grade = lead.getCustomerGrade();
        return new LeadDetailResponse(
                lead.getId(),
                SideChannelGuard.display(lead.getCompanyName()),
                SideChannelGuard.display(lead.getCountry()),
                SideChannelGuard.display(lead.getContactName()),
                lead.getStage().name(),
                lead.getStage().getLabel(),
                grade == null ? null : grade.name(),
                grade == null ? null : grade.getLabel(),
                SideChannelGuard.display(lead.getProductInterest()),
                SideChannelGuard.display(lead.getSource()),
                SideChannelGuard.display(lead.getNotes()),
                lead.getOwnerId(),
                SideChannelGuard.display(ownerName),
                lead.getCreatedAt(),
                lead.getUpdatedAt(),
                new MaskedContact(vault.getPhoneMasked(), vault.getEmailMasked(), vault.getWhatsappMasked()),
                state,
                revealed,
                wakePlan(lead));
    }

    private WakePlanView wakePlan(Lead lead) {
        String raw = lead.getWakePlanJson();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = mapper.readValue(raw, new TypeReference<>() {
            });
            Object leadIdRaw = root.get("leadId");
            long leadId = leadIdRaw instanceof Number n ? n.longValue() : lead.getId();
            boolean sendEnabled = Boolean.TRUE.equals(root.get("sendEnabled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsRaw = (List<Map<String, Object>>) root.get("steps");
            List<WakeStepView> steps = stepsRaw == null ? List.of() : stepsRaw.stream()
                    .map(step -> new WakeStepView(
                            ((Number) step.get("dayOffset")).intValue(),
                            String.valueOf(step.get("templateKey"))))
                    .toList();
            return new WakePlanView(leadId, steps, sendEnabled);
        } catch (Exception ex) {
            return null;
        }
    }
}
