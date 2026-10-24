package com.ftcrm.web;

import com.ftcrm.messaging.ConversationQueryService;
import com.ftcrm.web.dto.MessageDtos.ConversationListResponse;
import com.ftcrm.web.dto.MessageDtos.MessageListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads/{id:\\d+}/conversations")
public class ConversationController {

    private final ConversationQueryService conversations;
    private final CurrentUser current;

    public ConversationController(ConversationQueryService conversations, CurrentUser current) {
        this.conversations = conversations;
        this.current = current;
    }

    @GetMapping
    public ConversationListResponse threads(@PathVariable long id) {
        return conversations.threads(current.require(), id);
    }

    @GetMapping("/messages")
    public MessageListResponse messages(@PathVariable long id) {
        return conversations.messages(current.require(), id);
    }
}
