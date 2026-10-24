package com.ftcrm.web;

import com.ftcrm.service.LeadService;
import com.ftcrm.service.UnmaskService;
import com.ftcrm.web.dto.LeadDtos.ImportRequest;
import com.ftcrm.web.dto.LeadDtos.ImportResponse;
import com.ftcrm.web.dto.LeadDtos.LeadDetailResponse;
import com.ftcrm.web.dto.LeadDtos.LeadWriteRequest;
import com.ftcrm.web.dto.LeadDtos.StageEvidenceRequest;
import com.ftcrm.web.dto.LeadDtos.UnmaskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leads;
    private final UnmaskService unmask;
    private final CurrentUser current;

    public LeadController(LeadService leads, UnmaskService unmask, CurrentUser current) {
        this.leads = leads;
        this.unmask = unmask;
        this.current = current;
    }

    @GetMapping
    public List<LeadDetailResponse> list() {
        return leads.list(current.require());
    }

    @PostMapping
    public LeadDetailResponse create(@Valid @RequestBody LeadWriteRequest request) {
        return leads.create(request, current.require());
    }

    @PostMapping("/import")
    public ImportResponse importLeads(@Valid @RequestBody ImportRequest request) {
        return leads.importLeads(request, current.require());
    }

    @GetMapping("/{id:\\d+}")
    public LeadDetailResponse detail(@PathVariable long id) {
        return leads.detail(id, current.require());
    }

    /**
     * 一次保存：流失成立则写 LOST；否则至多升迁一阶。等级按证据计算，不可手填。
     */
    @PostMapping("/{id:\\d+}/promote")
    public LeadDetailResponse promote(@PathVariable long id, @Valid @RequestBody StageEvidenceRequest request) {
        return leads.promote(id, request, current.require());
    }

    @PostMapping("/{id:\\d+}/unmask")
    public LeadDetailResponse startUnmask(@PathVariable long id, @Valid @RequestBody UnmaskRequest request) {
        var actor = current.require();
        unmask.start(id, request.reason(), actor);
        return leads.detail(id, actor);
    }

    @PostMapping("/{id:\\d+}/unmask/end")
    public LeadDetailResponse endUnmask(@PathVariable long id) {
        var actor = current.require();
        unmask.end(id, actor);
        return leads.detail(id, actor);
    }
}
