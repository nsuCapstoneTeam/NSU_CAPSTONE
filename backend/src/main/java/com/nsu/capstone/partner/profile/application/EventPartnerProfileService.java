package com.nsu.capstone.partner.profile.application;

import com.nsu.capstone.partner.profile.repository.EventPartnerProfileRepository;
import com.nsu.capstone.partner.profile.repository.StoredProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventPartnerProfileService {

    private final EventPartnerProfileRepository repository;

    public EventPartnerProfileService(EventPartnerProfileRepository repository) {
        this.repository = repository;
    }

    /** 저장된 프로필. 없으면(저장 전이거나 삭제함) 비어 있다. */
    public Optional<StoredProfile> find(UUID userId) {
        return repository.find(userId);
    }
}
