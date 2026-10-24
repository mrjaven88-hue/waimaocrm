package com.ftcrm.repo;

import com.ftcrm.domain.UnmaskSession;
import com.ftcrm.domain.UnmaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UnmaskSessionRepository extends JpaRepository<UnmaskSession, Long> {

    Optional<UnmaskSession> findFirstByManagerIdAndLeadIdAndStatusOrderByIdDesc(
            Long managerId, Long leadId, UnmaskStatus status);

    List<UnmaskSession> findByManagerIdAndStatusAndLeadIdIn(
            Long managerId, UnmaskStatus status, Collection<Long> leadIds);
}
