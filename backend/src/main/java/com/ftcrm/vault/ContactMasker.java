package com.ftcrm.vault;

public final class ContactMasker {

    private ContactMasker() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        boolean plus = phone.startsWith("+");
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return plus ? "+***" : "***";
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return (plus ? "+" : "") + head + "****" + tail;
    }
}
