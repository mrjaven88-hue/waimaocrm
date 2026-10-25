package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.Lead;
import com.ftcrm.security.AuthUser;
import com.ftcrm.service.AuditService;
import com.ftcrm.service.LeadAccess;
import com.ftcrm.web.dto.MessageDtos.ArchiveCommand;
import com.ftcrm.web.dto.MessageDtos.ArchiveStubResponse;
import com.ftcrm.web.dto.MessageDtos.EmailSendRequest;
import com.ftcrm.web.dto.MessageDtos.SendStubResponse;
import com.ftcrm.web.dto.MessageDtos.WhatsAppSendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final LeadAccess access;
    private final ApprovedTemplateCatalog templates;
    private final VaultDestinationGate gate;
    private final SendRateLimiter rateLimiter;
    private final AuditService audit;
    private final WhatsAppClient whatsAppClient;
    private final EmailClient emailClient;

    public MessagingService(LeadAccess access, ApprovedTemplateCatalog templates, VaultDestinationGate gate,
                            SendRateLimiter rateLimiter, AuditService audit, WhatsAppClient whatsAppClient,
                            EmailClient emailClient) {
        this.access = access;
        this.templates = templates;
        this.gate = gate;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.whatsAppClient = whatsAppClient;
        this.emailClient = emailClient;
    }

    @Transactional
    public SendStubResponse sendWhatsapp(AuthUser actor, WhatsAppSendRequest request) {
        Lead lead = access.requireVisible(request.leadId(), actor);
        templates.require(MessageChannel.WHATSAPP, request.templateCode());
        SideChannelGuard.reject(request.body(), "正文");
        SideChannelGuard.reject(request.language(), "语言");
        String language = language(request.language());
        gate.requireSealed(lead.getId(), MessageChannel.WHATSAPP);
        rateLimiter.acquire(actor.id(), MessageChannel.WHATSAPP);
        ProviderAck ack = whatsAppClient.sendTemplate(new WhatsAppSend(lead.getId(), request.templateCode(), language));
        auditSend(actor, lead.getId(), "WHATSAPP", request.templateCode(), ack);
        log.info("messaging send channel=WHATSAPP leadId={} provider={} delivered={}",
                lead.getId(), ack.provider(), ack.delivered());
        return sendResponse(ack, lead.getId(), "WHATSAPP", request.templateCode(), "WhatsApp 发送尚未开通");
    }

    @Transactional
    public SendStubResponse sendEmail(AuthUser actor, EmailSendRequest request) {
        Lead lead = access.requireVisible(request.leadId(), actor);
        templates.require(MessageChannel.EMAIL, request.templateCode());
        SideChannelGuard.reject(request.subject(), "主题");
        SideChannelGuard.reject(request.body(), "正文");
        gate.requireSealed(lead.getId(), MessageChannel.EMAIL);
        rateLimiter.acquire(actor.id(), MessageChannel.EMAIL);
        ProviderAck ack = emailClient.sendTemplate(new EmailSend(lead.getId(), request.templateCode(), request.subject()));
        auditSend(actor, lead.getId(), "EMAIL", request.templateCode(), ack);
        log.info("messaging send channel=EMAIL leadId={} provider={} delivered={}",
                lead.getId(), ack.provider(), ack.delivered());
        return sendResponse(ack, lead.getId(), "EMAIL", request.templateCode(), "邮件发送尚未开通");
    }

    @Transactional
    public ArchiveStubResponse archive(AuthUser actor, ArchiveCommand command) {
        Lead lead = access.requireVisible(command.leadId(), actor);
        gate.requireSealed(lead.getId(), command.channel());
        audit.record(actor, "MESSAGE_ARCHIVE", "LEAD", lead.getId(), null, Map.of(
                "channel", command.channel().name(),
                "direction", command.direction().name(),
                "outcome", "stub"));
        log.info("archive stub channel={} leadId={}", command.channel().name(), lead.getId());
        // TODO: 存档表就绪后改为 conversationArchive.append(leadId, channel, bodyCipher, bodyIv)，只传密文。
        return new ArchiveStubResponse(
                "NOT_IMPLEMENTED",
                "沟通存档尚未开通",
                lead.getId(),
                command.channel().name(),
                command.direction().name(),
                false);
    }

    private static String language(String requested) {
        String value = requested == null ? "zh" : requested;
        if (!value.equals("zh") && !value.equals("en")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "语言不合法");
        }
        return value;
    }

    private void auditSend(AuthUser actor, long leadId, String channel, String templateCode, ProviderAck ack) {
        String outcome = ack.delivered()
                ? ("mock".equals(ack.provider()) ? "mock" : "accepted")
                : "stub";
        audit.record(actor, "MESSAGE_SEND", "LEAD", leadId, null, Map.of(
                "channel", channel,
                "templateCode", templateCode,
                "provider", ack.provider(),
                "outcome", outcome));
    }

    private static SendStubResponse sendResponse(ProviderAck ack, long leadId, String channel, String templateCode, String pendingMessage) {
        if (ack.delivered()) {
            return new SendStubResponse(null, "已提交发送", leadId, channel, ack.provider(), templateCode, true);
        }
        return new SendStubResponse("NOT_IMPLEMENTED", pendingMessage, leadId, channel, ack.provider(), templateCode, false);
    }
}
