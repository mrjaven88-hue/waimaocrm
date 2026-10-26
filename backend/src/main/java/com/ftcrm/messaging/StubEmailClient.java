package com.ftcrm.messaging;

import com.ftcrm.config.AppProperties;

/**
 * 不调用 Gmail / Resend / SMTP，也不解密。unconfigured 路径由 {@link RoutingEmailClient} 内联同等行为。
 * 本类保留给规则测试与文档对照：不得依赖 ContactCryptoService。
 */
public class StubEmailClient implements EmailClient {

    private final VaultDestinationGate gate;
    private final AppProperties properties;

    public StubEmailClient(VaultDestinationGate gate, AppProperties properties) {
        this.gate = gate;
        this.properties = properties;
    }

    @Override
    public ProviderAck sendTemplate(EmailSend command) {
        gate.requireSealed(command.leadId(), MessageChannel.EMAIL);
        return new ProviderAck(properties.getMessaging().resolvedEmailProvider(), false);
    }
}
