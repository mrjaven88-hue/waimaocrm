package com.ftcrm.messaging;

import com.ftcrm.config.AppProperties;

/**
 * 不调用 360dialog / WATI，也不解密。unconfigured 路径由 {@link RoutingWhatsAppClient} 内联同等行为。
 * 本类保留给规则测试与文档对照：不得依赖 ContactCryptoService。
 */
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
