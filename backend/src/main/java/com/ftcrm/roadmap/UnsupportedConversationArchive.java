package com.ftcrm.roadmap;

import com.ftcrm.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedConversationArchive implements ConversationArchivePort {

    @Override
    public void append(long leadId, String channel, String bodyCipher, String bodyIv) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "沟通存档尚未开通");
    }
}
