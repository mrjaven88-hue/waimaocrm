package com.ftcrm.messaging;

import com.ftcrm.security.AuthUser;
import com.ftcrm.service.LeadAccess;
import com.ftcrm.web.dto.MessageDtos.ConversationListResponse;
import com.ftcrm.web.dto.MessageDtos.MessageListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationQueryService {

    private final LeadAccess access;

    public ConversationQueryService(LeadAccess access) {
        this.access = access;
    }

    @Transactional(readOnly = true)
    public ConversationListResponse threads(AuthUser actor, long leadId) {
        access.requireVisible(leadId, actor);
        return new ConversationListResponse(leadId, "NOT_IMPLEMENTED", List.of());
    }

    @Transactional(readOnly = true)
    public MessageListResponse messages(AuthUser actor, long leadId) {
        access.requireVisible(leadId, actor);
        return new MessageListResponse(leadId, "NOT_IMPLEMENTED", List.of());
    }
}
