package com.nsu.capstone.partner.profile.application;

import com.nsu.capstone.partner.profile.repository.EventPartnerProfileRepository;
import com.nsu.capstone.partner.profile.repository.StoredProfile;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventPartnerProfileService {

    private final EventPartnerProfileRepository repository;
    private final Clock clock;

    public EventPartnerProfileService(EventPartnerProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** 저장된 프로필. 없으면(저장 전이거나 삭제함) 비어 있다. */
    public Optional<StoredProfile> find(UUID userId) {
        return repository.find(userId);
    }

    public StoredProfile save(UUID userId, ProfileContent content) {
        return repository.upsert(userId, content, clock.instant());
    }
}
