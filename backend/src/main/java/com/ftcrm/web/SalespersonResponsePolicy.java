package com.ftcrm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.common.SideChannelGuard;

import java.util.Locale;
import java.util.Set;

public final class SalespersonResponsePolicy {

    private static final Set<String> FORBIDDEN = Set.of(
            "phone",
            "email",
            "whatsapp",
            "revealed",
            "plaintext",
            "phonecipher",
            "emailcipher",
            "whatsappcipher",
            "phonehash",
            "emailhash",
            "whatsapphash",
            "password",
            "passwordhash",
            "blindindex",
            "fingerprint"
    );

    private static final Set<String> ECHO_KEYS = Set.of(
            "companyname",
            "country",
            "contactname",
            "productinterest",
            "source",
            "notes",
            "ownername",
            "displayname",
            "reason",
            "message"
    );

    private SalespersonResponsePolicy() {
    }

    public static void assertSafe(ObjectMapper mapper, Object body) {
        if (body == null) {
            return;
        }
        walk(mapper.valueToTree(body));
    }

    private static void walk(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String normalized = normalizeKey(field.getKey());
                if (forbiddenKey(normalized)) {
                    throw new IllegalStateException("blocked");
                }
                if (ECHO_KEYS.contains(normalized) && field.getValue().isTextual()
                        && SideChannelGuard.containsContact(field.getValue().asText())) {
                    throw new IllegalStateException("blocked");
                }
                walk(field.getValue());
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(SalespersonResponsePolicy::walk);
        }
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static boolean forbiddenKey(String normalized) {
        if (FORBIDDEN.contains(normalized) || normalized.startsWith("revealed")) {
            return true;
        }
        return normalized.endsWith("hash")
                || normalized.endsWith("cipher")
                || normalized.endsWith("ciphertext")
                || normalized.endsWith("plaintext")
                || normalized.endsWith("secret");
    }
}
