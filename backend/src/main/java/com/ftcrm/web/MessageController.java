package com.ftcrm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftcrm.messaging.MessageRequestParser;
import com.ftcrm.messaging.MessagingService;
import com.ftcrm.web.dto.MessageDtos.ArchiveStubResponse;
import com.ftcrm.web.dto.MessageDtos.SendStubResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessagingService messaging;
    private final MessageRequestParser parser;
    private final CurrentUser current;

    public MessageController(MessagingService messaging, MessageRequestParser parser, CurrentUser current) {
        this.messaging = messaging;
        this.parser = parser;
        this.current = current;
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<SendStubResponse> whatsapp(@RequestBody JsonNode body) {
        SendStubResponse response = messaging.sendWhatsapp(current.require(), parser.whatsapp(body));
        return ResponseEntity.status(response.delivered() ? HttpStatus.ACCEPTED : HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    @PostMapping("/email")
    public ResponseEntity<SendStubResponse> email(@RequestBody JsonNode body) {
        SendStubResponse response = messaging.sendEmail(current.require(), parser.email(body));
        return ResponseEntity.status(response.delivered() ? HttpStatus.ACCEPTED : HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    @PostMapping("/archive")
    public ResponseEntity<ArchiveStubResponse> archive(@RequestBody JsonNode body) {
        ArchiveStubResponse response = messaging.archive(current.require(), parser.archive(body));
        return ResponseEntity.status(response.stored() ? HttpStatus.ACCEPTED : HttpStatus.NOT_IMPLEMENTED).body(response);
    }
}
