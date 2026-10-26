package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 按 WHATSAPP_PROVIDER 路由：unconfigured→stub，mock→已投递，wati/360dialog→需密钥的实发壳。
 */
@Component
@Primary
public class RoutingWhatsAppClient implements WhatsAppClient {

    private final VaultDestinationGate gate;
    private final VaultDestinationOpener opener;
    private final AppProperties properties;

    public RoutingWhatsAppClient(VaultDestinationGate gate, VaultDestinationOpener opener, AppProperties properties) {
        this.gate = gate;
        this.opener = opener;
        this.properties = properties;
    }

    @Override
    public ProviderAck sendTemplate(WhatsAppSend command) {
        gate.requireSealed(command.leadId(), MessageChannel.WHATSAPP);
        AppProperties.Messaging messaging = properties.getMessaging();
        String provider = messaging.resolvedWhatsappProvider();
        return switch (provider) {
            case "mock" -> new ProviderAck("mock", true);
            case "wati" -> wati(command, messaging);
            case "360dialog" -> dialog360(command, messaging);
            default -> new ProviderAck("unconfigured", false);
        };
    }

    private ProviderAck wati(WhatsAppSend command, AppProperties.Messaging messaging) {
        if (!messaging.watiConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "WhatsApp 供应商未配置完整");
        }
        String to = opener.open(command.leadId(), MessageChannel.WHATSAPP);
        try {
            String base = trimSlash(messaging.getWatiApiBase());
            String body = "{\"template_name\":\"" + ProviderHttp.jsonEscape(command.templateCode())
                    + "\",\"broadcast_name\":\"" + ProviderHttp.jsonEscape(command.templateCode())
                    + "\",\"parameters\":[],\"whatsapp_number\":\"" + ProviderHttp.jsonEscape(digits(to)) + "\"}";
            ProviderHttp.postJson(
                    base + "/api/v1/sendTemplateMessage",
                    Map.of("Authorization", "Bearer " + messaging.getWatiApiToken()),
                    body,
                    "WATI");
            return new ProviderAck("wati", true);
        } finally {
            to = null;
        }
    }

    private ProviderAck dialog360(WhatsAppSend command, AppProperties.Messaging messaging) {
        if (!messaging.whatsappHttpConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "WhatsApp 供应商未配置完整");
        }
        String to = opener.open(command.leadId(), MessageChannel.WHATSAPP);
        try {
            String base = trimSlash(messaging.getWhatsappApiBase());
            String language = command.language() == null ? "zh" : command.language();
            String body = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + ProviderHttp.jsonEscape(digits(to))
                    + "\",\"type\":\"template\",\"template\":{\"name\":\"" + ProviderHttp.jsonEscape(command.templateCode())
                    + "\",\"language\":{\"code\":\"" + ProviderHttp.jsonEscape(language) + "\"}}}";
            ProviderHttp.postJson(
                    base + "/messages",
                    Map.of("D360-API-KEY", messaging.getWhatsappApiKey()),
                    body,
                    "360dialog");
            return new ProviderAck("360dialog", true);
        } finally {
            to = null;
        }
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String digits(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
