package com.ftcrm.messaging;

/**
 * 邮件官方 API。占位供应商是 Gmail 或 Resend。
 * 真实实现才能解密保险库，明文只留在这次调用的内存里，不能放进返回值。
 */
public interface EmailClient {

    ProviderAck sendTemplate(EmailSend command);
}
