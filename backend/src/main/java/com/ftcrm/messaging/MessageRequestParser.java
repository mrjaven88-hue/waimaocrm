package com.ftcrm.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftcrm.common.ApiException;
import com.ftcrm.web.dto.MessageDtos.ArchiveCommand;
import com.ftcrm.web.dto.MessageDtos.EmailSendRequest;
import com.ftcrm.web.dto.MessageDtos.WhatsAppSendRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 只抽出发送所需的业务字段。存档密文在校验后丢弃，骨架不保存、不回显。
 */
@Component
public class MessageRequestParser {

    private static final Pattern TEMPLATE = Pattern.compile("^(?!.*\\d{8,})[a-z][a-z0-9_]{1,63}$");
    private static final Pattern CIPHER = Pattern.compile("^[A-Za-z0-9+/]{16,4000}={0,2}$");

    public WhatsAppSendRequest whatsapp(JsonNode node) {
        object(node);
        DestinationFieldGuard.reject(node);
        only(node, Set.of("leadId", "templateCode", "body", "language"));
        return new WhatsAppSendRequest(
                leadId(node),
                template(node),
                optionalText(node, "body", 4000, "正文"),
                optionalText(node, "language", 8, "语言"));
    }

    public EmailSendRequest email(JsonNode node) {
        object(node);
        DestinationFieldGuard.reject(node);
        only(node, Set.of("leadId", "templateCode", "subject", "body"));
        String subject = optionalText(node, "subject", 200, "主题");
        if (subject == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "主题不合法");
        }
        return new EmailSendRequest(leadId(node), template(node), subject, optionalText(node, "body", 4000, "正文"));
    }

    public ArchiveCommand archive(JsonNode node) {
        object(node);
        DestinationFieldGuard.reject(node);
        only(node, Set.of("leadId", "channel", "direction", "bodyCipher", "bodyIv"));
        long id = leadId(node);
        MessageChannel channel = channel(node);
        MessageDirection direction = direction(node);
        requireCipher(node, "bodyCipher");
        requireCipher(node, "bodyIv");
        return new ArchiveCommand(id, channel, direction);
    }

    private static void object(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_JSON", "请求格式不正确");
        }
    }

    private static void only(JsonNode node, Set<String> allowed) {
        var names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "请求含有不允许的字段");
            }
        }
    }

    private static long leadId(JsonNode node) {
        JsonNode value = node.get("leadId");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "客户编号不合法");
        }
        return value.longValue();
    }

    private static String template(JsonNode node) {
        JsonNode value = node.get("templateCode");
        if (value == null || !value.isTextual() || !TEMPLATE.matcher(value.asText()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "模板编码不合法");
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field, int max, String label) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", label + "不合法");
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", label + "不合法");
        }
        return text;
    }

    private static MessageChannel channel(JsonNode node) {
        JsonNode value = node.get("channel");
        if (value == null || !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "渠道不合法");
        }
        return switch (value.asText()) {
            case "WHATSAPP" -> MessageChannel.WHATSAPP;
            case "EMAIL" -> MessageChannel.EMAIL;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "渠道不合法");
        };
    }

    private static MessageDirection direction(JsonNode node) {
        JsonNode value = node.get("direction");
        if (value == null || !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "方向不合法");
        }
        return switch (value.asText()) {
            case "INBOUND" -> MessageDirection.INBOUND;
            case "OUTBOUND" -> MessageDirection.OUTBOUND;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "方向不合法");
        };
    }

    private static void requireCipher(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !CIPHER.matcher(value.asText()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "存档内容不合法");
        }
    }
}
