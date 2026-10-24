package com.ftcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.AuditEvent;
import com.ftcrm.repo.AuditEventRepository;
import com.ftcrm.security.AuthUser;
import com.ftcrm.time.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "expiresAt", "ttlMinutes", "companyName", "imported", "duplicated", "failed", "sessionId",
            "channel", "templateCode", "provider", "outcome", "direction",
            "fromStage", "toStage", "grade", "wakeSendEnabled");

    private final AuditEventRepository events;
    private final TimeSource time;
    private final ObjectMapper mapper;

    public AuditService(AuditEventRepository events, TimeSource time, ObjectMapper mapper) {
        this.events = events;
        this.time = time;
        this.mapper = mapper;
    }

    public void record(AuthUser actor, String action, String targetType, Long targetId, String reason, Map<String, ?> metadata) {
        write(actor.id(), actor.displayName(), actor.username(), action, targetType, targetId, reason, metadata);
    }

    public void recordAnonymous(String username, String action, String targetType) {
        String safe = username == null ? "-" : username.trim().toLowerCase(Locale.ROOT);
        if (!safe.matches("^[a-z0-9._-]{1,64}$")) {
            safe = "-";
        }
        write(null, "未知", safe, action, targetType, null, null, Map.of());
    }

    private void write(Long actorId, String actorName, String actorUsername, String action, String targetType,
                       Long targetId, String reason, Map<String, ?> metadata) {
        AuditEvent event = new AuditEvent();
        event.setActorId(actorId);
        event.setActorName(trim(actorName, 80));
        event.setActorUsername(trim(actorUsername, 64));
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setReason(reason == null ? null : trim(reason, 200));
        event.setMetadataJson(metadataJson(metadata));
        event.setCreatedAt(time.now());
        events.save(event);
        log.info("audit action={} target={}:{}", action, targetType, targetId);
    }

    private String metadataJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            if (!ALLOWED_KEYS.contains(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String text) {
                if (SideChannelGuard.containsContact(text)) {
                    throw new IllegalStateException("refusing sensitive audit metadata");
                }
                safe.put(entry.getKey(), text);
            } else if (value instanceof Number || value instanceof Boolean) {
                safe.put(entry.getKey(), value);
            }
        }
        if (safe.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(safe);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("audit metadata failed");
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
