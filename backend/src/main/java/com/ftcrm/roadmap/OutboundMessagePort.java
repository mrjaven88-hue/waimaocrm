package com.ftcrm.roadmap;

/**
 * 阶段④服务端代发。实现时只从保险库解密后调用官方 API，明文不回写业务员响应。
 */
public interface OutboundMessagePort {

    void sendWhatsapp(long leadId, String body);

    void sendEmail(long leadId, String subject, String body);
}
