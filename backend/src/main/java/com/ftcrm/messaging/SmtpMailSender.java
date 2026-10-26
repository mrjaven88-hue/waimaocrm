package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import org.springframework.http.HttpStatus;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 最小 SMTP 提交壳（STARTTLS + AUTH LOGIN）。失败时不回显收件人或密码。
 */
final class SmtpMailSender {

    private SmtpMailSender() {
    }

    static void send(AppProperties.Messaging messaging, String to, String subject, String text) {
        String host = messaging.getSmtpHost();
        int port = messaging.getSmtpPort() <= 0 ? 587 : messaging.getSmtpPort();
        try (Socket raw = new Socket(host, port)) {
            raw.setSoTimeout(20_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(raw.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(raw.getOutputStream(), StandardCharsets.UTF_8));
            expect(in, 220);
            write(out, "EHLO ftcrm.local");
            readEhlo(in);
            write(out, "STARTTLS");
            expect(in, 220);
            SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            tls.startHandshake();
            in = new BufferedReader(new InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(tls.getOutputStream(), StandardCharsets.UTF_8));
            write(out, "EHLO ftcrm.local");
            readEhlo(in);
            write(out, "AUTH LOGIN");
            expect(in, 334);
            write(out, Base64.getEncoder().encodeToString(messaging.getSmtpUsername().getBytes(StandardCharsets.UTF_8)));
            expect(in, 334);
            write(out, Base64.getEncoder().encodeToString(messaging.getSmtpPassword().getBytes(StandardCharsets.UTF_8)));
            expect(in, 235);
            write(out, "MAIL FROM:<" + messaging.getEmailFrom() + ">");
            expect(in, 250);
            write(out, "RCPT TO:<" + to + ">");
            expect(in, 250);
            write(out, "DATA");
            expect(in, 354);
            out.write("From: " + messaging.getEmailFrom() + "\r\n");
            out.write("To: " + to + "\r\n");
            out.write("Subject: " + sanitizeHeader(subject) + "\r\n");
            out.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
            out.write(text.replace("\r\n.", "\r\n.."));
            out.write("\r\n.\r\n");
            out.flush();
            expect(in, 250);
            write(out, "QUIT");
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE", "SMTP 暂时不可用");
        }
    }

    private static void write(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private static void expect(BufferedReader in, int code) throws IOException {
        String line = in.readLine();
        if (line == null || line.length() < 3 || !line.startsWith(String.valueOf(code))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED", "SMTP 发送被拒绝");
        }
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED", "SMTP 发送被拒绝");
            }
        }
    }

    private static void readEhlo(BufferedReader in) throws IOException {
        String line = in.readLine();
        if (line == null || !line.startsWith("250")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED", "SMTP 发送被拒绝");
        }
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED", "SMTP 发送被拒绝");
            }
        }
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ");
    }
}
