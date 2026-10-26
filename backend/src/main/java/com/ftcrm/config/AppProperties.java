package com.ftcrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "ftcrm")
public class AppProperties {

    private final Vault vault = new Vault();
    private final Jwt jwt = new Jwt();
    private final Messaging messaging = new Messaging();
    private boolean openapiPublic;
    private int unmaskTtlMinutes = 15;

    public Vault getVault() {
        return vault;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public boolean isOpenapiPublic() {
        return openapiPublic;
    }

    public void setOpenapiPublic(boolean openapiPublic) {
        this.openapiPublic = openapiPublic;
    }

    public int getUnmaskTtlMinutes() {
        return unmaskTtlMinutes;
    }

    public void setUnmaskTtlMinutes(int unmaskTtlMinutes) {
        this.unmaskTtlMinutes = unmaskTtlMinutes;
    }

    public static class Vault {
        private String key;
        private String hashKey;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getHashKey() {
            return hashKey;
        }

        public void setHashKey(String hashKey) {
            this.hashKey = hashKey;
        }
    }

    public static class Jwt {
        private String secret;
        private int ttlHours = 12;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(int ttlHours) {
            this.ttlHours = ttlHours;
        }
    }

    /**
     * 供应商密钥只从环境变量绑定。骨架不把它们写进响应、审计或日志。
     * 未识别的供应商名称一律当成 unconfigured，避免把误填的密钥回显出去。
     */
    public static class Messaging {
        private int whatsappPerHour = 30;
        private int emailPerHour = 60;
        private String whatsappProvider = "unconfigured";
        private String emailProvider = "unconfigured";
        private String whatsappApiBase = "";
        private String whatsappApiKey = "";
        private String watiApiBase = "";
        private String watiApiToken = "";
        private String resendApiKey = "";
        private String emailFrom = "";
        private String smtpHost = "";
        private int smtpPort = 587;
        private String smtpUsername = "";
        private String smtpPassword = "";
        private String gmailClientId = "";
        private String gmailClientSecret = "";
        private String gmailRefreshToken = "";

        public int getWhatsappPerHour() {
            return whatsappPerHour;
        }

        public void setWhatsappPerHour(int whatsappPerHour) {
            this.whatsappPerHour = whatsappPerHour;
        }

        public int getEmailPerHour() {
            return emailPerHour;
        }

        public void setEmailPerHour(int emailPerHour) {
            this.emailPerHour = emailPerHour;
        }

        public String getWhatsappProvider() {
            return whatsappProvider;
        }

        public void setWhatsappProvider(String whatsappProvider) {
            this.whatsappProvider = whatsappProvider;
        }

        public String getEmailProvider() {
            return emailProvider;
        }

        public void setEmailProvider(String emailProvider) {
            this.emailProvider = emailProvider;
        }

        public String getWhatsappApiBase() {
            return whatsappApiBase;
        }

        public void setWhatsappApiBase(String whatsappApiBase) {
            this.whatsappApiBase = whatsappApiBase;
        }

        public String getWhatsappApiKey() {
            return whatsappApiKey;
        }

        public void setWhatsappApiKey(String whatsappApiKey) {
            this.whatsappApiKey = whatsappApiKey;
        }

        public String getWatiApiBase() {
            return watiApiBase;
        }

        public void setWatiApiBase(String watiApiBase) {
            this.watiApiBase = watiApiBase;
        }

        public String getWatiApiToken() {
            return watiApiToken;
        }

        public void setWatiApiToken(String watiApiToken) {
            this.watiApiToken = watiApiToken;
        }

        public String getResendApiKey() {
            return resendApiKey;
        }

        public void setResendApiKey(String resendApiKey) {
            this.resendApiKey = resendApiKey;
        }

        public String getEmailFrom() {
            return emailFrom;
        }

        public void setEmailFrom(String emailFrom) {
            this.emailFrom = emailFrom;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public int getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(int smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getSmtpUsername() {
            return smtpUsername;
        }

        public void setSmtpUsername(String smtpUsername) {
            this.smtpUsername = smtpUsername;
        }

        public String getSmtpPassword() {
            return smtpPassword;
        }

        public void setSmtpPassword(String smtpPassword) {
            this.smtpPassword = smtpPassword;
        }

        public String getGmailClientId() {
            return gmailClientId;
        }

        public void setGmailClientId(String gmailClientId) {
            this.gmailClientId = gmailClientId;
        }

        public String getGmailClientSecret() {
            return gmailClientSecret;
        }

        public void setGmailClientSecret(String gmailClientSecret) {
            this.gmailClientSecret = gmailClientSecret;
        }

        public String getGmailRefreshToken() {
            return gmailRefreshToken;
        }

        public void setGmailRefreshToken(String gmailRefreshToken) {
            this.gmailRefreshToken = gmailRefreshToken;
        }

        public String resolvedWhatsappProvider() {
            return allow(whatsappProvider, "mock", "360dialog", "wati");
        }

        public String resolvedEmailProvider() {
            return allow(emailProvider, "mock", "gmail", "resend", "smtp");
        }

        public boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        public boolean resendConfigured() {
            return hasText(resendApiKey) && hasText(emailFrom);
        }

        public boolean smtpConfigured() {
            return hasText(smtpHost) && hasText(smtpUsername) && hasText(smtpPassword) && hasText(emailFrom);
        }

        public boolean gmailConfigured() {
            return hasText(gmailClientId) && hasText(gmailClientSecret) && hasText(gmailRefreshToken) && hasText(emailFrom);
        }

        public boolean watiConfigured() {
            return hasText(watiApiBase) && hasText(watiApiToken);
        }

        public boolean whatsappHttpConfigured() {
            return hasText(whatsappApiBase) && hasText(whatsappApiKey);
        }

        private static String allow(String raw, String... names) {
            if (raw == null) {
                return "unconfigured";
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            for (String name : names) {
                if (name.equals(value)) {
                    return name;
                }
            }
            return "unconfigured";
        }
    }
}
