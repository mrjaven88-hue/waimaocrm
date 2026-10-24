package com.ftcrm.vault;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactCryptoServiceTest {

    private final ContactCryptoService crypto = new ContactCryptoService(properties());

    @Test
    void roundTripUsesRandomIvAndHidesPlaintext() {
        String plain = "sentinel.hans@acme-export.test";
        Sealed first = crypto.seal(plain, ContactCryptoService.aad(7, "email"));
        Sealed second = crypto.seal(plain, ContactCryptoService.aad(7, "email"));
        assertThat(first.cipher()).isNotEqualTo(second.cipher());
        assertThat(crypto.open(first.cipher(), first.iv(), ContactCryptoService.aad(7, "email"))).isEqualTo(plain);
        assertThat(new String(Base64.getDecoder().decode(first.cipher()), StandardCharsets.ISO_8859_1)).doesNotContain(plain);
        assertThat(crypto.hash(plain)).hasSize(64).doesNotContain(plain);
        assertThat(crypto.hash(plain)).isEqualTo(crypto.hash(plain));
    }

    @Test
    void wrongAadDoesNotReturnPlaintext() {
        Sealed sealed = crypto.seal("+4900001112299", ContactCryptoService.aad(7, "phone"));
        assertThatThrownBy(() -> crypto.open(sealed.cipher(), sealed.iv(), ContactCryptoService.aad(8, "phone")))
                .isInstanceOf(ApiException.class)
                .hasMessage("保险库记录无法解密");
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.getVault().setKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.getVault().setHashKey("ZGV2LWhhc2gta2V5LW5vdC1mb3ItcHJvZHVjdGlvbiE=");
        properties.getJwt().setSecret("test-only-jwt-secret-change-me-32b");
        return properties;
    }
}
