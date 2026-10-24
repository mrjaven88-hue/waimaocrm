package com.ftcrm.web;

import com.ftcrm.service.AuditQueryService;
import com.ftcrm.web.dto.AuditResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditResponse> list() {
        return audit.latest();
    }
}
