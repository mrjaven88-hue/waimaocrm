package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.domain.ContactVaultEntry;
import com.ftcrm.repo.ContactVaultRepository;
import com.ftcrm.vault.ContactCryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 仅供真实供应商客户端在发送瞬间解密。明文不得进入返回值、异常文案或日志。
 */
@Component
public class VaultDestinationOpener {

    private final ContactVaultRepository vaults;
    private final ContactCryptoService crypto;

    public VaultDestinationOpener(ContactVaultRepository vaults, ContactCryptoService crypto) {
        this.vaults = vaults;
        this.crypto = crypto;
    }

    public String open(long leadId, MessageChannel channel) {
        ContactVaultEntry vault = vaults.findByLeadId(leadId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_CONTACT", "客户没有可发送的联系方式"));
        if (channel == MessageChannel.WHATSAPP) {
            String plain = crypto.open(vault.getWhatsappCipher(), vault.getWhatsappIv(),
                    ContactCryptoService.aad(leadId, "whatsapp"));
            if (plain == null || plain.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CONTACT", "该客户没有可发送的 WhatsApp");
            }
            return plain;
        }
        String plain = crypto.open(vault.getEmailCipher(), vault.getEmailIv(),
                ContactCryptoService.aad(leadId, "email"));
        if (plain == null || plain.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CONTACT", "该客户没有可发送的邮件地址");
        }
        return plain;
    }
}
