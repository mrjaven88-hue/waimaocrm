package com.ftcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.common.ApiException;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.ContactVaultEntry;
import com.ftcrm.domain.CustomerGrade;
import com.ftcrm.domain.Lead;
import com.ftcrm.domain.LeadStage;
import com.ftcrm.domain.Role;
import com.ftcrm.domain.UnmaskSession;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.ContactVaultRepository;
import com.ftcrm.repo.LeadRepository;
import com.ftcrm.roadmap.CustomerGradePolicy;
import com.ftcrm.roadmap.CustomerGradePolicy.GradeEvidence;
import com.ftcrm.roadmap.StagePromotionPolicy;
import com.ftcrm.roadmap.StagePromotionPolicy.CommercialEvidence;
import com.ftcrm.roadmap.StagePromotionPolicy.LossDecision;
import com.ftcrm.roadmap.StagePromotionPolicy.LossSignal;
import com.ftcrm.roadmap.WakeSequencePlanner;
import com.ftcrm.roadmap.WakeSequencePlanner.WakePlan;
import com.ftcrm.security.AuthUser;
import com.ftcrm.time.TimeSource;
import com.ftcrm.vault.ContactCryptoService;
import com.ftcrm.vault.ContactMasker;
import com.ftcrm.vault.ContactNormalizer;
import com.ftcrm.vault.Sealed;
import com.ftcrm.web.dto.LeadDtos.ImportRequest;
import com.ftcrm.web.dto.LeadDtos.ImportResponse;
import com.ftcrm.web.dto.LeadDtos.LeadDetailResponse;
import com.ftcrm.web.dto.LeadDtos.LeadWriteRequest;
import com.ftcrm.web.dto.LeadDtos.RowError;
import com.ftcrm.web.dto.LeadDtos.StageEvidenceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeadService {

    private static final int LIST_LIMIT = 200;

    private final LeadRepository leads;
    private final ContactVaultRepository vaults;
    private final AppUserRepository users;
    private final LeadAccess access;
    private final LeadViewMapper views;
    private final UnmaskService unmask;
    private final AuditService audit;
    private final ContactCryptoService crypto;
    private final TimeSource time;
    private final ObjectMapper mapper;

    public LeadService(LeadRepository leads, ContactVaultRepository vaults, AppUserRepository users,
                       LeadAccess access, LeadViewMapper views, UnmaskService unmask, AuditService audit,
                       ContactCryptoService crypto, TimeSource time, ObjectMapper mapper) {
        this.leads = leads;
        this.vaults = vaults;
        this.users = users;
        this.access = access;
        this.views = views;
        this.unmask = unmask;
        this.audit = audit;
        this.crypto = crypto;
        this.time = time;
        this.mapper = mapper;
    }

    @Transactional
    public LeadDetailResponse create(LeadWriteRequest request, AuthUser actor) {
        Prepared prepared = prepare(request);
        if (duplicate(prepared)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CONTACT", "联系方式已存在，未重复建档");
        }
        return persist(prepared, actor);
    }

    @Transactional
    public ImportResponse importLeads(ImportRequest request, AuthUser actor) {
        int imported = 0;
        int duplicated = 0;
        int failed = 0;
        List<LeadDetailResponse> created = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        List<LeadWriteRequest> rows = request.leads();
        for (int i = 0; i < rows.size(); i++) {
            try {
                Prepared prepared = prepare(rows.get(i));
                if (duplicate(prepared)) {
                    duplicated++;
                    continue;
                }
                created.add(persist(prepared, actor));
                imported++;
            } catch (ApiException ex) {
                failed++;
                errors.add(new RowError(i, ex.getCode(), ex.getMessage()));
            }
        }
        audit.record(actor, "LEAD_IMPORT", "LEAD", null, null, Map.of(
                "imported", imported,
                "duplicated", duplicated,
                "failed", failed));
        return new ImportResponse(imported, duplicated, failed, created, errors);
    }

    @Transactional
    public List<LeadDetailResponse> list(AuthUser actor) {
        List<Lead> rows = actor.role() == Role.MANAGER
                ? leads.findAllByOrderByUpdatedAtDesc()
                : leads.findByOwnerIdOrderByUpdatedAtDesc(actor.id());
        if (rows.size() > LIST_LIMIT) {
            rows = rows.subList(0, LIST_LIMIT);
        }
        return toViews(rows, actor, false);
    }

    @Transactional
    public LeadDetailResponse detail(long id, AuthUser actor) {
        Lead lead = access.requireVisible(id, actor);
        return toViews(List.of(lead), actor, true).getFirst();
    }

    /**
     * 同一次保存只走一条路径：流失成立则写 LOST 并保存唤醒计划（不发信）；
     * 否则至多升迁一阶。等级始终按证据单独计算。
     */
    @Transactional
    public LeadDetailResponse promote(long id, StageEvidenceRequest request, AuthUser actor) {
        Lead lead = access.requireVisible(id, actor);
        EvidenceBundle evidence = prepareEvidence(request, lead);

        LossDecision loss = StagePromotionPolicy.evaluateLoss(evidence.lossSignal());
        LeadStage previous = lead.getStage();
        applyEvidenceFields(lead, evidence);

        if (loss.lost()) {
            lead.setStage(LeadStage.LOST);
            WakePlan plan = WakeSequencePlanner.planForLostLead(lead.getId(), loss);
            // 只保存计划，sendEnabled=false；绝不 dispatch / 触达出站
            lead.setWakePlanJson(toWakeJson(plan));
            lead.setCustomerGrade(CustomerGradePolicy.grade(evidence.gradeEvidence()));
            lead.setUpdatedAt(time.now());
            leads.saveAndFlush(lead);
            audit.record(actor, "LEAD_LOST", "LEAD", lead.getId(), loss.reason().name(), Map.of(
                    "fromStage", previous.name(),
                    "toStage", LeadStage.LOST.name(),
                    "grade", lead.getCustomerGrade().name(),
                    "wakeSendEnabled", false));
            return toViews(List.of(lead), actor, true).getFirst();
        }

        if (hasPromotionIntent(evidence.commercial())) {
            LeadStage next = StagePromotionPolicy.forward(previous, evidence.commercial());
            lead.setStage(next);
            lead.setWakePlanJson(null);
            if (next == LeadStage.WON && lead.getDaysSinceLastDeal() == null) {
                lead.setDaysSinceLastDeal(0);
            }
            audit.record(actor, "LEAD_PROMOTE", "LEAD", lead.getId(), null, Map.of(
                    "fromStage", previous.name(),
                    "toStage", next.name()));
        } else if (previous == LeadStage.LOST) {
            // 未再流失且无升迁证据：保持 LOST，仅刷新等级/指标
        }

        CustomerGrade grade = CustomerGradePolicy.grade(evidence.gradeEvidence());
        lead.setCustomerGrade(grade);
        lead.setUpdatedAt(time.now());
        leads.saveAndFlush(lead);
        if (!hasPromotionIntent(evidence.commercial()) && previous != LeadStage.LOST) {
            audit.record(actor, "LEAD_GRADE", "LEAD", lead.getId(), null, Map.of(
                    "grade", grade.name(),
                    "fromStage", previous.name(),
                    "toStage", lead.getStage().name()));
        } else if (hasPromotionIntent(evidence.commercial())) {
            // promote audit already written; append grade via second record
            audit.record(actor, "LEAD_GRADE", "LEAD", lead.getId(), null, Map.of(
                    "grade", grade.name(),
                    "fromStage", previous.name(),
                    "toStage", lead.getStage().name()));
        }
        return toViews(List.of(lead), actor, true).getFirst();
    }

    private EvidenceBundle prepareEvidence(StageEvidenceRequest request, Lead lead) {
        String notes = coalesce(blankToNull(request.notes()), lead.getNotes());
        String product = coalesce(blankToNull(request.product()), lead.getProductInterest());
        String quantity = coalesce(blankToNull(request.quantity()), lead.getQuantity());
        String destinationPort = coalesce(blankToNull(request.destinationPort()), lead.getDestinationPort());
        String quotation = coalesce(blankToNull(request.quotation()), lead.getQuotation());
        String customerReply = coalesce(blankToNull(request.customerReply()), lead.getCustomerReply());
        String orderOrContractNumber = coalesce(blankToNull(request.orderOrContractNumber()), lead.getOrderOrContractNumber());
        String shippingDocumentNumber = coalesce(blankToNull(request.shippingDocumentNumber()), lead.getShippingDocumentNumber());
        String receiptOrArrivalNote = coalesce(blankToNull(request.receiptOrArrivalNote()), lead.getReceiptOrArrivalNote());
        String budget = coalesce(blankToNull(request.budget()), lead.getBudget());
        String deliveryDate = coalesce(blankToNull(request.deliveryDate()), lead.getDeliveryDate());

        SideChannelGuard.reject(notes, "备注");
        SideChannelGuard.reject(product, "品名");
        SideChannelGuard.reject(quantity, "数量");
        SideChannelGuard.reject(destinationPort, "目的港");
        SideChannelGuard.reject(quotation, "报价");
        SideChannelGuard.reject(customerReply, "客户回复");
        SideChannelGuard.reject(orderOrContractNumber, "订单或合同号");
        SideChannelGuard.reject(shippingDocumentNumber, "货运单证号");
        SideChannelGuard.reject(receiptOrArrivalNote, "签收或到货反馈");
        SideChannelGuard.reject(budget, "预算");
        SideChannelGuard.reject(deliveryDate, "交货期");

        int daysSinceValidInteraction = request.daysSinceValidInteraction() == null
                ? lead.getDaysSinceValidInteraction()
                : request.daysSinceValidInteraction();
        int consecutiveUnanswered = request.consecutiveUnanswered() == null
                ? lead.getConsecutiveUnanswered()
                : request.consecutiveUnanswered();
        Integer daysSinceLastDeal = request.daysSinceLastDeal() != null
                ? request.daysSinceLastDeal()
                : lead.getDaysSinceLastDeal();
        if (daysSinceValidInteraction < 0 || consecutiveUnanswered < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "互动天数和连续未回复次数不能为负");
        }
        if (daysSinceLastDeal != null && daysSinceLastDeal < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "距成交天数不能为负");
        }

        boolean explicitReject = Boolean.TRUE.equals(request.explicitReject());
        CommercialEvidence commercial = new CommercialEvidence(
                blankToNull(request.notes()),
                blankToNull(request.product()),
                blankToNull(request.quantity()),
                blankToNull(request.destinationPort()),
                blankToNull(request.quotation()),
                blankToNull(request.customerReply()),
                blankToNull(request.orderOrContractNumber()),
                blankToNull(request.shippingDocumentNumber()),
                blankToNull(request.receiptOrArrivalNote()));
        // 升迁证据只看本次请求字段（档案旧值不能冒充新互动）
        LossSignal lossSignal = new LossSignal(explicitReject, daysSinceValidInteraction, blankToNull(request.customerReply()));
        GradeEvidence gradeEvidence = new GradeEvidence(
                daysSinceLastDeal,
                budget,
                deliveryDate,
                product,
                quantity,
                destinationPort,
                quotation,
                customerReply,
                daysSinceValidInteraction,
                consecutiveUnanswered);
        return new EvidenceBundle(
                commercial,
                lossSignal,
                gradeEvidence,
                notes,
                product,
                quantity,
                destinationPort,
                quotation,
                customerReply,
                orderOrContractNumber,
                shippingDocumentNumber,
                receiptOrArrivalNote,
                budget,
                deliveryDate,
                daysSinceValidInteraction,
                consecutiveUnanswered,
                daysSinceLastDeal);
    }

    private void applyEvidenceFields(Lead lead, EvidenceBundle evidence) {
        if (evidence.notes() != null) {
            lead.setNotes(evidence.notes());
        }
        if (evidence.product() != null) {
            lead.setProductInterest(evidence.product());
        }
        if (evidence.quantity() != null) {
            lead.setQuantity(evidence.quantity());
        }
        if (evidence.destinationPort() != null) {
            lead.setDestinationPort(evidence.destinationPort());
        }
        if (evidence.quotation() != null) {
            lead.setQuotation(evidence.quotation());
        }
        if (evidence.customerReply() != null) {
            lead.setCustomerReply(evidence.customerReply());
        }
        if (evidence.orderOrContractNumber() != null) {
            lead.setOrderOrContractNumber(evidence.orderOrContractNumber());
        }
        if (evidence.shippingDocumentNumber() != null) {
            lead.setShippingDocumentNumber(evidence.shippingDocumentNumber());
        }
        if (evidence.receiptOrArrivalNote() != null) {
            lead.setReceiptOrArrivalNote(evidence.receiptOrArrivalNote());
        }
        if (evidence.budget() != null) {
            lead.setBudget(evidence.budget());
        }
        if (evidence.deliveryDate() != null) {
            lead.setDeliveryDate(evidence.deliveryDate());
        }
        lead.setDaysSinceValidInteraction(evidence.daysSinceValidInteraction());
        lead.setConsecutiveUnanswered(evidence.consecutiveUnanswered());
        lead.setDaysSinceLastDeal(evidence.daysSinceLastDeal());
    }

    /** 请求里出现任一升迁相关字段即视为有意图（含「已联系」）；由 forward 判定能否前进。 */
    private static boolean hasPromotionIntent(CommercialEvidence commercial) {
        return present(commercial.notes())
                || present(commercial.product())
                || present(commercial.quantity())
                || present(commercial.destinationPort())
                || present(commercial.quotation())
                || present(commercial.customerReply())
                || present(commercial.orderOrContractNumber())
                || present(commercial.shippingDocumentNumber())
                || present(commercial.receiptOrArrivalNote());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String toWakeJson(WakePlan plan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("leadId", plan.leadId());
        root.put("sendEnabled", plan.sendEnabled());
        root.put("steps", plan.steps().stream()
                .map(step -> Map.<String, Object>of(
                        "dayOffset", step.dayOffset(),
                        "templateKey", step.templateKey()))
                .toList());
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("wake plan serialize failed", ex);
        }
    }

    private List<LeadDetailResponse> toViews(List<Lead> rows, AuthUser actor, boolean allowReveal) {
        List<Long> ids = rows.stream().map(Lead::getId).toList();
        Map<Long, ContactVaultEntry> byLead = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ContactVaultEntry vault : vaults.findByLeadIdIn(ids)) {
                byLead.put(vault.getLeadId(), vault);
            }
        }
        Map<Long, String> ownerNames = new HashMap<>();
        for (AppUser user : users.findAllById(rows.stream().map(Lead::getOwnerId).distinct().toList())) {
            ownerNames.put(user.getId(), user.getDisplayName());
        }
        Map<Long, UnmaskSession> sessions = actor.role() == Role.MANAGER
                ? unmask.activeByLead(actor.id(), ids)
                : Map.of();
        List<LeadDetailResponse> result = new ArrayList<>();
        for (Lead lead : rows) {
            ContactVaultEntry vault = byLead.get(lead.getId());
            if (vault == null) {
                throw new IllegalStateException("vault missing");
            }
            UnmaskSession session = sessions.get(lead.getId());
            result.add(views.toView(
                    lead,
                    vault,
                    actor,
                    session,
                    allowReveal,
                    ownerNames.getOrDefault(lead.getOwnerId(), "未知")));
        }
        return result;
    }

    private Prepared prepare(LeadWriteRequest request) {
        String company = required(request.companyName(), "公司");
        String country = required(request.country(), "国家");
        String contactName = required(request.contactName(), "联系人");
        String product = blankToNull(request.productInterest());
        String source = blankToNull(request.source());
        String notes = blankToNull(request.notes());
        SideChannelGuard.reject(company, "公司");
        SideChannelGuard.reject(country, "国家");
        SideChannelGuard.reject(contactName, "联系人");
        SideChannelGuard.reject(product, "意向产品");
        SideChannelGuard.reject(source, "来源");
        SideChannelGuard.reject(notes, "备注");

        String phone = ContactNormalizer.phone(request.phone());
        String email = ContactNormalizer.email(request.email());
        String whatsapp = ContactNormalizer.phone(request.whatsapp());
        if (phone == null && email == null && whatsapp == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED", "至少填写一种联系方式");
        }
        return new Prepared(company, country, contactName, product, source, notes, phone, email, whatsapp,
                crypto.hash(phone), crypto.hash(email), crypto.hash(whatsapp));
    }

    private boolean duplicate(Prepared prepared) {
        List<String> hashes = new ArrayList<>();
        if (prepared.phoneHash() != null) {
            hashes.add(prepared.phoneHash());
        }
        if (prepared.emailHash() != null) {
            hashes.add(prepared.emailHash());
        }
        if (prepared.whatsappHash() != null) {
            hashes.add(prepared.whatsappHash());
        }
        return !hashes.isEmpty() && vaults.countByAnyHash(hashes) > 0;
    }

    private LeadDetailResponse persist(Prepared prepared, AuthUser actor) {
        Instant now = time.now();
        Lead lead = new Lead();
        lead.setCompanyName(prepared.company());
        lead.setCountry(prepared.country());
        lead.setContactName(prepared.contactName());
        lead.setStage(LeadStage.LEAD);
        lead.setCustomerGrade(CustomerGrade.C);
        lead.setProductInterest(prepared.product());
        lead.setSource(prepared.source());
        lead.setNotes(prepared.notes());
        lead.setDaysSinceValidInteraction(0);
        lead.setConsecutiveUnanswered(0);
        lead.setOwnerId(actor.id());
        lead.setCreatedAt(now);
        lead.setUpdatedAt(now);
        lead = leads.saveAndFlush(lead);

        ContactVaultEntry vault = new ContactVaultEntry();
        vault.setLeadId(lead.getId());
        vault.setCreatedAt(now);
        sealPhone(vault, lead.getId(), prepared.phone(), prepared.phoneHash());
        sealEmail(vault, lead.getId(), prepared.email(), prepared.emailHash());
        sealWhatsapp(vault, lead.getId(), prepared.whatsapp(), prepared.whatsappHash());
        vaults.saveAndFlush(vault);

        audit.record(actor, "LEAD_CREATE", "LEAD", lead.getId(), null, Map.of("companyName", prepared.company()));
        return views.toView(lead, vault, actor, null, false, actor.displayName());
    }

    private void sealPhone(ContactVaultEntry vault, long leadId, String phone, String hash) {
        if (phone == null) {
            return;
        }
        Sealed sealed = crypto.seal(phone, ContactCryptoService.aad(leadId, "phone"));
        vault.setPhoneCipher(sealed.cipher());
        vault.setPhoneIv(sealed.iv());
        vault.setPhoneHash(hash);
        vault.setPhoneMasked(ContactMasker.phone(phone));
    }

    private void sealEmail(ContactVaultEntry vault, long leadId, String email, String hash) {
        if (email == null) {
            return;
        }
        Sealed sealed = crypto.seal(email, ContactCryptoService.aad(leadId, "email"));
        vault.setEmailCipher(sealed.cipher());
        vault.setEmailIv(sealed.iv());
        vault.setEmailHash(hash);
        vault.setEmailMasked(ContactMasker.email(email));
    }

    private void sealWhatsapp(ContactVaultEntry vault, long leadId, String whatsapp, String hash) {
        if (whatsapp == null) {
            return;
        }
        Sealed sealed = crypto.seal(whatsapp, ContactCryptoService.aad(leadId, "whatsapp"));
        vault.setWhatsappCipher(sealed.cipher());
        vault.setWhatsappIv(sealed.iv());
        vault.setWhatsappHash(hash);
        vault.setWhatsappMasked(ContactMasker.phone(whatsapp));
    }

    private static String required(String raw, String label) {
        String value = blankToNull(raw);
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", label + "不合法");
        }
        return value;
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String coalesce(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private record Prepared(
            String company,
            String country,
            String contactName,
            String product,
            String source,
            String notes,
            String phone,
            String email,
            String whatsapp,
            String phoneHash,
            String emailHash,
            String whatsappHash
    ) {
    }

    private record EvidenceBundle(
            CommercialEvidence commercial,
            LossSignal lossSignal,
            GradeEvidence gradeEvidence,
            String notes,
            String product,
            String quantity,
            String destinationPort,
            String quotation,
            String customerReply,
            String orderOrContractNumber,
            String shippingDocumentNumber,
            String receiptOrArrivalNote,
            String budget,
            String deliveryDate,
            int daysSinceValidInteraction,
            int consecutiveUnanswered,
            Integer daysSinceLastDeal
    ) {
    }
}
