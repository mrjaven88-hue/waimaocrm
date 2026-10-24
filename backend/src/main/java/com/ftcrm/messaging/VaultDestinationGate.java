package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.domain.ContactVaultEntry;
import com.ftcrm.repo.ContactVaultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 按客户编号确认保险库里有密封的目的地。不解密，也不返回密文。
 */
@Component
public class VaultDestinationGate {

    private final ContactVaultRepository vaults;

    public VaultDestinationGate(ContactVaultRepository vaults) {
        this.vaults = vaults;
    }

    public void requireSealed(long leadId, MessageChannel channel) {
        ContactVaultEntry vault = vaults.findByLeadId(leadId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_CONTACT", "客户没有可发送的联系方式"));
        String cipher = channel == MessageChannel.WHATSAPP ? vault.getWhatsappCipher() : vault.getEmailCipher();
        if (cipher == null || cipher.isBlank()) {
            String message = channel == MessageChannel.WHATSAPP
                    ? "该客户没有可发送的 WhatsApp"
                    : "该客户没有可发送的邮件地址";
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CONTACT", message);
        }
    }
}
