package com.ftcrm.repo;

import com.ftcrm.domain.ContactVaultEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContactVaultRepository extends JpaRepository<ContactVaultEntry, Long> {

    Optional<ContactVaultEntry> findByLeadId(Long leadId);

    List<ContactVaultEntry> findByLeadIdIn(Collection<Long> leadIds);

    @Query("""
            select count(v) from ContactVaultEntry v
            where (v.phoneHash is not null and v.phoneHash in :hashes)
               or (v.emailHash is not null and v.emailHash in :hashes)
               or (v.whatsappHash is not null and v.whatsappHash in :hashes)
            """)
    long countByAnyHash(@Param("hashes") Collection<String> hashes);
}
