package com.ftcrm.messaging;

import com.ftcrm.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * 不调用 360dialog / WATI，也不解密。
 * TODO: 在本类的替换实现里读取 WHATSAPP_API_KEY 或 WATI_API_TOKEN，按 leadId 解密 WhatsApp，
 * 调用官方模板接口后立刻丢掉明文。不要把号码放进 ProviderAck、异常或日志。
 */
@Component
public class StubWhatsAppClient implements WhatsAppClient {

    private final VaultDestinationGate gate;
    private final AppProperties properties;

    public StubWhatsAppClient(VaultDestinationGate gate, AppProperties properties) {
        this.gate = gate;
        this.properties = properties;
    }

    @Override
    public ProviderAck sendTemplate(WhatsAppSend command) {
        gate.requireSealed(command.leadId(), MessageChannel.WHATSAPP);
        return new ProviderAck(properties.getMessaging().resolvedWhatsappProvider(), false);
    }
}
