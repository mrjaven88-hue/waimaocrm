package com.ftcrm.messaging;

import com.ftcrm.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * 不调用 Gmail / Resend，也不解密。
 * TODO: 在本类的替换实现里读取 RESEND_API_KEY 或 Gmail OAuth 环境变量，按 leadId 解密邮箱，
 * 调用官方接口后立刻丢掉明文。不要把地址放进 ProviderAck、异常或日志。
 */
@Component
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
