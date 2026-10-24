package com.ftcrm.web.dto;

import com.ftcrm.messaging.MessageChannel;
import com.ftcrm.messaging.MessageDirection;

import java.time.Instant;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record WhatsAppSendRequest(long leadId, String templateCode, String body, String language) {
    }

    public record EmailSendRequest(long leadId, String templateCode, String subject, String body) {
    }

    public record ArchiveCommand(long leadId, MessageChannel channel, MessageDirection direction) {
    }

    public record SendStubResponse(
            String error,
            String message,
            long leadId,
            String channel,
            String provider,
            String templateCode,
            boolean delivered
    ) {
    }

    public record ArchiveStubResponse(
            String error,
            String message,
            long leadId,
            String channel,
            String direction,
            boolean stored
    ) {
    }

    public record ThreadStub(long id, String channel, String direction, Instant updatedAt, String summary) {
    }

    public record ConversationListResponse(long leadId, String archiveStatus, List<ThreadStub> threads) {
    }

    public record MessageStub(long id, String channel, String direction, Instant createdAt, String summary) {
    }

    public record MessageListResponse(long leadId, String archiveStatus, List<MessageStub> messages) {
    }
}
