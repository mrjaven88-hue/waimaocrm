package com.ftcrm.service;

import com.ftcrm.common.ApiException;
import com.ftcrm.domain.Lead;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.LeadRepository;
import com.ftcrm.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LeadAccess {

    private final LeadRepository leads;

    public LeadAccess(LeadRepository leads) {
        this.leads = leads;
    }

    public Lead requireVisible(long id, AuthUser actor) {
        Lead lead = leads.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "客户不存在"));
        if (actor.role() == Role.SALESPERSON && !lead.getOwnerId().equals(actor.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "客户不存在");
        }
        return lead;
    }
}
