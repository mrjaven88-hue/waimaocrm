package com.ftcrm.service;

import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.AuditEvent;
import com.ftcrm.repo.AuditEventRepository;
import com.ftcrm.web.dto.AuditResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditQueryService {

    private final AuditEventRepository events;

    public AuditQueryService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> latest() {
        return events.findTop200ByOrderByIdDesc().stream().map(AuditQueryService::toResponse).toList();
    }

    private static AuditResponse toResponse(AuditEvent event) {
        return new AuditResponse(
                event.getId(),
                event.getActorName(),
                event.getActorUsername(),
                event.getAction(),
                label(event.getAction()),
                event.getTargetType(),
                event.getTargetId(),
                SideChannelGuard.display(event.getReason()),
                event.getCreatedAt());
    }

    private static String label(String action) {
        return switch (action) {
            case "LOGIN" -> "登录";
            case "LOGIN_FAILED" -> "登录失败";
            case "REGISTER" -> "注册";
            case "LEAD_CREATE" -> "新建客户";
            case "LEAD_IMPORT" -> "导入客户";
            case "UNMASK_START" -> "开始揭盲";
            case "UNMASK_END" -> "结束揭盲";
            case "UNMASK_EXPIRE" -> "揭盲过期";
            case "MESSAGE_SEND" -> "发送消息";
            case "MESSAGE_ARCHIVE" -> "沟通存档";
            default -> action;
        };
    }
}
