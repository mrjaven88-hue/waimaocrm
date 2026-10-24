package com.ftcrm.messaging;

/**
 * 供应商回执。不允许增加目的地、正文或密钥字段。
 */
public record ProviderAck(String provider, boolean delivered) {
}
