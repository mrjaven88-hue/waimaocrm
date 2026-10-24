package com.ftcrm.web.dto;

import java.time.Instant;

public record AuditResponse(
        Long id,
        String actorName,
        String actorUsername,
        String action,
        String actionLabel,
        String targetType,
        Long targetId,
        String reason,
        Instant createdAt
) {
}
