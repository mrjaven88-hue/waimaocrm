package com.ftcrm.common;

import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会回显的字段不能夹带联系方式或查重哈希。
 * 先做 NFKC、十进制数字折叠，并去掉格式字符，避免全角、零宽和分隔符绕过。
 */
public final class SideChannelGuard {

    private static final String REDACTED = "已拦截";

    private static final Pattern EIGHT_DIGITS = Pattern.compile("\\d{8,}");
    private static final Pattern DATE_TOKEN = Pattern.compile("\\d{4}[-/.]\\d{2}[-/.]\\d{2}");
    private static final Pattern PLUS_PHONE = Pattern.compile("(?<![A-Za-z0-9])\\+(?:\\d[\\s().\\-/]*){7}\\d");
    private static final Pattern LOOSE_PHONE = Pattern.compile("(?<![A-Za-z0-9])\\+?(?:\\d[\\s().\\-/]*){8,}");
    private static final Pattern TRAILING_SEPARATOR = Pattern.compile("[\\s().\\-/]+$");
    private static final Pattern SOFT_SEPARATOR = Pattern.compile("[\\s()./]");
    private static final Pattern HASH_FRAGMENT = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{16,}(?![0-9a-f])");

    private SideChannelGuard() {
    }

    public static void reject(String value, String label) {
        if (containsContact(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAINTEXT_SIDE_CHANNEL", label + "不能包含联系方式");
        }
    }

    public static String display(String value) {
        if (value == null || !containsContact(value)) {
            return value;
        }
        return REDACTED;
    }

    public static boolean containsContact(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = DATE_TOKEN.matcher(normalize(value)).replaceAll(" ");
        if (normalized.indexOf('@') >= 0 || EIGHT_DIGITS.matcher(normalized).find() || HASH_FRAGMENT.matcher(normalized).find()) {
            return true;
        }
        if (PLUS_PHONE.matcher(normalized).find()) {
            return true;
        }
        Matcher loose = LOOSE_PHONE.matcher(normalized);
        while (loose.find()) {
            String token = TRAILING_SEPARATOR.matcher(loose.group()).replaceAll("");
            int digits = countDigits(token);
            if (digits >= 10 || (digits >= 8 && SOFT_SEPARATOR.matcher(token).find())) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String value) {
        String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(nfkc.length());
        nfkc.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.FORMAT || Character.isISOControl(cp)) {
                return;
            }
            if (type == Character.DECIMAL_DIGIT_NUMBER) {
                int digit = Character.getNumericValue(cp);
                if (digit >= 0 && digit <= 9) {
                    out.append((char) ('0' + digit));
                    return;
                }
            }
            if (Character.isWhitespace(cp)) {
                out.append(' ');
                return;
            }
            if (isDash(cp)) {
                out.append('-');
                return;
            }
            out.appendCodePoint(cp);
        });
        return out.toString();
    }

    private static boolean isDash(int cp) {
        return cp == '\u2010' || cp == '\u2011' || cp == '\u2012' || cp == '\u2013'
                || cp == '\u2014' || cp == '\u2212' || cp == '\uFE63' || cp == '\uFF0D';
    }

    private static int countDigits(String token) {
        int digits = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) >= '0' && token.charAt(i) <= '9') {
                digits++;
            }
        }
        return digits;
    }
}
