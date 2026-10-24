package com.ftcrm.service;

import com.ftcrm.common.ApiException;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.config.AppProperties;
import com.ftcrm.domain.Role;
import com.ftcrm.domain.UnmaskSession;
import com.ftcrm.domain.UnmaskStatus;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.UnmaskSessionRepository;
import com.ftcrm.security.AuthUser;
import com.ftcrm.time.TimeSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class UnmaskService {

    private static final DateTimeFormatter AUDIT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX").withZone(ZoneOffset.UTC);

    private final UnmaskSessionRepository sessions;
    private final AppUserRepository users;
    private final LeadAccess access;
    private final AuditService audit;
    private final TimeSource time;
    private final Duration ttl;

    public UnmaskService(UnmaskSessionRepository sessions, AppUserRepository users, LeadAccess access,
                         AuditService audit, TimeSource time, AppProperties properties) {
        this.users = users;
        int minutes = properties.getUnmaskTtlMinutes();
        if (minutes < 1 || minutes > 15) {
            throw new IllegalStateException("ftcrm.unmask-ttl-minutes must be 1..15");
        }
        this.sessions = sessions;
        this.access = access;
        this.audit = audit;
        this.time = time;
        this.ttl = Duration.ofMinutes(minutes);
    }

    @Transactional
    public void start(long leadId, String reason, AuthUser actor) {
        requireManager(actor);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 8 || trimmed.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "揭盲原因不合法");
        }
        SideChannelGuard.reject(trimmed, "揭盲原因");
        access.requireVisible(leadId, actor);
        if (activeFor(actor.id(), leadId) != null) {
            return;
        }
        Instant now = time.now();
        UnmaskSession session = new UnmaskSession();
        session.setManagerId(actor.id());
        session.setLeadId(leadId);
        session.setReason(trimmed);
        session.setStartedAt(now);
        session.setExpiresAt(now.plus(ttl));
        session.setStatus(UnmaskStatus.ACTIVE);
        session = sessions.saveAndFlush(session);
        audit.record(actor, "UNMASK_START", "LEAD", leadId, trimmed, Map.of(
                "expiresAt", AUDIT_TIME.format(session.getExpiresAt()),
                "ttlMinutes", (int) ttl.toMinutes(),
                "sessionId", session.getId()));
    }

    @Transactional
    public void end(long leadId, AuthUser actor) {
        requireManager(actor);
        access.requireVisible(leadId, actor);
        sessions.findFirstByManagerIdAndLeadIdAndStatusOrderByIdDesc(actor.id(), leadId, UnmaskStatus.ACTIVE)
                .ifPresent(session -> {
                    if (!session.getExpiresAt().isAfter(time.now())) {
                        markExpired(session, actor);
                        return;
                    }
                    session.setStatus(UnmaskStatus.ENDED);
                    session.setEndedAt(time.now());
                    audit.record(actor, "UNMASK_END", "LEAD", leadId, "主动结束", Map.of("sessionId", session.getId()));
                });
    }

    @Transactional
    public UnmaskSession activeFor(long managerId, long leadId) {
        return sessions.findFirstByManagerIdAndLeadIdAndStatusOrderByIdDesc(managerId, leadId, UnmaskStatus.ACTIVE)
                .map(session -> keepOrExpire(session, managerId))
                .orElse(null);
    }

    @Transactional
    public Map<Long, UnmaskSession> activeByLead(long managerId, Collection<Long> leadIds) {
        if (leadIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UnmaskSession> active = new HashMap<>();
        for (UnmaskSession session : sessions.findByManagerIdAndStatusAndLeadIdIn(managerId, UnmaskStatus.ACTIVE, leadIds)) {
            UnmaskSession kept = keepOrExpire(session, managerId);
            if (kept != null) {
                active.put(kept.getLeadId(), kept);
            }
        }
        return active;
    }

    private UnmaskSession keepOrExpire(UnmaskSession session, long managerId) {
        if (session.getExpiresAt().isAfter(time.now())) {
            return session;
        }
        markExpired(session, actorOf(managerId));
        return null;
    }

    private AuthUser actorOf(long managerId) {
        return users.findById(managerId)
                .map(AuthUser::from)
                .orElseGet(() -> new AuthUser(managerId, "unknown", "未知", Role.MANAGER));
    }

    private void markExpired(UnmaskSession session, AuthUser actor) {
        session.setStatus(UnmaskStatus.EXPIRED);
        session.setEndedAt(time.now());
        audit.record(actor, "UNMASK_EXPIRE", "LEAD", session.getLeadId(), "已到期限", Map.of(
                "sessionId", session.getId(),
                "expiresAt", AUDIT_TIME.format(session.getExpiresAt())));
    }

    private static void requireManager(AuthUser actor) {
        if (actor.role() != Role.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅经理可以揭盲");
        }
    }
}
