package com.nsu.capstone.partner.profile.application;

import com.nsu.capstone.event.region.RegionRepository;
import com.nsu.capstone.partner.profile.repository.EventPartnerProfileRepository;
import com.nsu.capstone.partner.profile.repository.StoredProfile;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventPartnerProfileService {

    private final EventPartnerProfileRepository repository;
    private final RegionRepository regionRepository;
    private final Clock clock;

    public EventPartnerProfileService(
        EventPartnerProfileRepository repository,
        RegionRepository regionRepository,
        Clock clock
    ) {
        this.repository = repository;
        this.regionRepository = regionRepository;
        this.clock = clock;
    }

    /** 저장된 프로필. 없으면(저장 전이거나 삭제함) 비어 있다. */
    public Optional<StoredProfile> find(UUID userId) {
        return repository.find(userId);
    }

    public StoredProfile save(UUID userId, ProfileContent content) {
        List<String> regionCodes = new RegionNormalizer(regionRepository.findAll())
            .normalize(content.regionCodes());
        return repository.upsert(
            userId,
            new ProfileContent(
                content.name(), content.organization(), content.bio(), content.eventTypes(), regionCodes),
            clock.instant()
        );
    }

    /** 프로필이 없어도 오류가 아니다(05 §1). */
    public void delete(UUID userId) {
        repository.delete(userId);
    }
}
