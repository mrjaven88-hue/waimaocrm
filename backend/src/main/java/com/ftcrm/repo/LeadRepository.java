package com.ftcrm.repo;

import com.ftcrm.domain.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    List<Lead> findAllByOrderByUpdatedAtDesc();
}
