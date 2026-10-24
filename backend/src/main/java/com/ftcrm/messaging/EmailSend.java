package com.ftcrm.messaging;

/**
 * 发给邮件客户端的命令。收件地址不在这里，客户端将来只按 leadId 自行解密。
 */
public record EmailSend(long leadId, String templateCode, String subject) {
}
