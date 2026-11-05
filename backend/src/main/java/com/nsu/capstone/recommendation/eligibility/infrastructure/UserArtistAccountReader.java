package com.nsu.capstone.recommendation.eligibility.infrastructure;

import com.nsu.capstone.identity.repository.UserAccountRow;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.recommendation.eligibility.application.ArtistAccountReader;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistAccount;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@code users}에서 역할과 상태를 한 번의 조회로 읽는다.
 */
@Component
public class UserArtistAccountReader implements ArtistAccountReader {

    private final UserRepository userRepository;

    public UserArtistAccountReader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Map<UUID, ArtistAccount> findByArtistIds(Collection<UUID> artistIds) {
        return userRepository.findAllByIdIn(artistIds).stream().collect(Collectors.toUnmodifiableMap(
            UserAccountRow::id,
            row -> new ArtistAccount(row.role(), row.status())
        ));
    }
}
