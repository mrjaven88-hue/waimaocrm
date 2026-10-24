package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 占位批准目录。正式模板名要换成供应商后台里已批准的名称，不能由业务员临时指定。
 */
@Component
public class ApprovedTemplateCatalog {

    private static final Map<MessageChannel, Set<String>> APPROVED = Map.of(
            MessageChannel.WHATSAPP, Set.of("inquiry_followup_v1", "quote_update_v1"),
            MessageChannel.EMAIL, Set.of("inquiry_followup_v1", "quote_update_v1"));

    public void require(MessageChannel channel, String templateCode) {
        if (!APPROVED.getOrDefault(channel, Set.of()).contains(templateCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEMPLATE_NOT_APPROVED", "模板未批准，不能发送");
        }
    }
}
