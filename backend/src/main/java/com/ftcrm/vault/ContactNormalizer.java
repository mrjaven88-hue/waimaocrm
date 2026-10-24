package com.ftcrm.vault;

import com.ftcrm.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public final class ContactNormalizer {

    private ContactNormalizer() {
    }

    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        boolean plus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() < 6 || digits.length() > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTACT", "电话格式不正确");
        }
        return (plus ? "+" : "") + digits;
    }

    public static String email(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 120 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTACT", "邮箱格式不正确");
        }
        return email;
    }
}
