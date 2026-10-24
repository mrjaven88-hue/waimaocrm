package com.ftcrm.messaging;

/**
 * WhatsApp 官方 API。占位供应商是 360dialog 或 WATI。
 * 真实实现才能解密保险库，明文只留在这次调用的内存里，不能放进返回值。
 */
public interface WhatsAppClient {

    ProviderAck sendTemplate(WhatsAppSend command);
}
