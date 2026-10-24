package com.ftcrm.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftcrm.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

/**
 * 发送和存档请求不能自带目的地。拒绝时不把字段值写进异常。
 */
public final class DestinationFieldGuard {

    private static final Set<String> FORBIDDEN = Set.of(
            "phone", "email", "whatsapp", "to", "cc", "bcc", "recipient", "recipients",
            "destination", "from", "replyto", "waid", "phonenumber", "address", "mailto",
            "e164", "msisdn", "whatsappnumber", "revealed", "plaintext", "contact");

    private DestinationFieldGuard() {
    }

    public static void reject(JsonNode node) {
        walk(node);
    }

    private static void walk(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                if (FORBIDDEN.contains(key)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "DESTINATION_NOT_ACCEPTED",
                            "不能提交联系方式，服务器只按客户编号发送");
                }
                walk(field.getValue());
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(DestinationFieldGuard::walk);
        }
    }
}
