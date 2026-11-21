package com.nsu.capstone.artist.profile;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class ArtistProfileIntegrationTestBase extends EventIntegrationTestBase {

    protected User artist;

    @BeforeEach
    void createArtist() {
        artist = newUser(UserRole.ARTIST, "artist@example.com");
    }

    /** 프로필이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteArtistProfiles() {
        jdbcTemplate.update("DELETE FROM artist_profiles");
    }
}
