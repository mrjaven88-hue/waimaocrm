package com.ftcrm.messaging;

/**
 * 发给 WhatsApp 客户端的命令。目的地不在这里，客户端将来只按 leadId 自行解密。
 */
public record WhatsAppSend(long leadId, String templateCode, String language) {
}
