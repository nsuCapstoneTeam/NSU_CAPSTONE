package com.nsu.capstone.identity.repository;

import com.nsu.capstone.identity.domain.UserTermsAgreement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, UUID> {
    List<UserTermsAgreement> findByUserId(UUID userId);
}
