package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 按 EMAIL_PROVIDER 路由：unconfigured→stub，mock→已投递，resend/smtp/gmail→需密钥的实发壳。
 */
@Component
@Primary
public class RoutingEmailClient implements EmailClient {

    private final VaultDestinationGate gate;
    private final VaultDestinationOpener opener;
    private final AppProperties properties;

    public RoutingEmailClient(VaultDestinationGate gate, VaultDestinationOpener opener, AppProperties properties) {
        this.gate = gate;
        this.opener = opener;
        this.properties = properties;
    }

    @Override
    public ProviderAck sendTemplate(EmailSend command) {
        gate.requireSealed(command.leadId(), MessageChannel.EMAIL);
        AppProperties.Messaging messaging = properties.getMessaging();
        String provider = messaging.resolvedEmailProvider();
        return switch (provider) {
            case "mock" -> new ProviderAck("mock", true);
            case "resend" -> resend(command, messaging);
            case "smtp" -> smtp(command, messaging);
            case "gmail" -> gmail(command, messaging);
            default -> new ProviderAck("unconfigured", false);
        };
    }

    private ProviderAck resend(EmailSend command, AppProperties.Messaging messaging) {
        if (!messaging.resendConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "邮件供应商未配置完整");
        }
        String to = opener.open(command.leadId(), MessageChannel.EMAIL);
        try {
            String subject = command.subject() == null ? command.templateCode() : command.subject();
            String body = "{\"from\":\"" + ProviderHttp.jsonEscape(messaging.getEmailFrom())
                    + "\",\"to\":[\"" + ProviderHttp.jsonEscape(to)
                    + "\"],\"subject\":\"" + ProviderHttp.jsonEscape(subject)
                    + "\",\"text\":\"" + ProviderHttp.jsonEscape("template:" + command.templateCode()) + "\"}";
            ProviderHttp.postJson(
                    "https://api.resend.com/emails",
                    Map.of("Authorization", "Bearer " + messaging.getResendApiKey()),
                    body,
                    "Resend");
            return new ProviderAck("resend", true);
        } finally {
            to = null;
        }
    }

    private ProviderAck smtp(EmailSend command, AppProperties.Messaging messaging) {
        if (!messaging.smtpConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "邮件供应商未配置完整");
        }
        String to = opener.open(command.leadId(), MessageChannel.EMAIL);
        try {
            SmtpMailSender.send(messaging, to, command.subject() == null ? command.templateCode() : command.subject(),
                    "template:" + command.templateCode());
            return new ProviderAck("smtp", true);
        } finally {
            to = null;
        }
    }

    private ProviderAck gmail(EmailSend command, AppProperties.Messaging messaging) {
        if (!messaging.gmailConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "邮件供应商未配置完整");
        }
        // OAuth 刷新与 Gmail API 仍是壳：密钥齐全才允许进入，暂不解密以免误发。
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "Gmail 发送尚未接通");
    }
}
