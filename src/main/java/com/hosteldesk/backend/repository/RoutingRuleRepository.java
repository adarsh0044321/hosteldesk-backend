package com.hosteldesk.backend.repository;

import com.hosteldesk.backend.entity.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    Optional<RoutingRule> findByCategoryAndActiveTrue(String category);
}
