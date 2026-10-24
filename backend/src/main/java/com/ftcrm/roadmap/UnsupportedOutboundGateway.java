package com.ftcrm.roadmap;

import com.ftcrm.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 阶段④的失败网关。HTTP 骨架在 {@code com.ftcrm.messaging}，真实供应商接通前不要在这里解密。
 */
@Component
public class UnsupportedOutboundGateway implements OutboundMessagePort {

    @Override
    public void sendWhatsapp(long leadId, String body) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "WhatsApp 发送尚未开通");
    }

    @Override
    public void sendEmail(long leadId, String subject, String body) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "邮件发送尚未开通");
    }
}
