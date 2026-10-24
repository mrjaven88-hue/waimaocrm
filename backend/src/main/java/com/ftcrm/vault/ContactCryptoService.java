package com.ftcrm.vault;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ContactCryptoService {

    private static final Logger log = LoggerFactory.getLogger(ContactCryptoService.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final byte[] key;
    private final byte[] hashKey;
    private final SecureRandom random = new SecureRandom();

    public ContactCryptoService(AppProperties properties) {
        this.key = decode(properties.getVault().getKey(), "CONTACT_VAULT_KEY");
        this.hashKey = decode(properties.getVault().getHashKey(), "CONTACT_VAULT_HASH_KEY");
        log.info("contact vault ready");
    }

    public static String aad(long leadId, String field) {
        return "ftcrm:v1:" + leadId + ":" + field;
    }

    public Sealed seal(String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Sealed(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(iv));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("vault seal failed");
        }
    }

    public String open(String cipherText, String ivText, String aad) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = Base64.getDecoder().decode(ivText);
            byte[] encrypted = Base64.getDecoder().decode(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.warn("vault decrypt failed");
            throw new ApiException(HttpStatus.CONFLICT, "VAULT_DECRYPT", "保险库记录无法解密");
        }
    }

    public String hash(String normalized) {
        if (normalized == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("vault hash failed");
        }
    }

    private static byte[] decode(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(raw.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException(name + " must decode to 32 bytes");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(name + " is not valid base64");
        }
    }
}
