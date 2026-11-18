package com.nsu.capstone.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ArtistReadAccessIntegrationTest extends EventIntegrationTestBase {

    @Autowired
    ArtistReadAccess access;

    @Test
    void 아티스트는_자기_정보를_읽을_수_있다() {
        User artist = newUser(UserRole.ARTIST, "access-own@example.com");

        assertThatCode(() -> access.require(artist.getId(), UserRole.ARTIST, artist.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void 아티스트가_남의_정보를_읽으면_대상이_있는지_보기_전에_ACCESS_DENIED다() {
        User artist = newUser(UserRole.ARTIST, "access-denied@example.com");
        UUID unknownArtistId = uuidV7Generator.generate();

        assertThatThrownBy(() -> access.require(artist.getId(), UserRole.ARTIST, unknownArtistId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void EVENT_PARTNER는_ACTIVE인_아티스트의_정보를_읽을_수_있다() {
        User artist = newUser(UserRole.ARTIST, "access-active@example.com");

        assertThatCode(() -> access.require(partner.getId(), UserRole.EVENT_PARTNER, artist.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void EVENT_PARTNER에게_없는_사용자_다른_역할_정지_탈퇴_아티스트는_모두_ARTIST_NOT_FOUND다() {
        User suspended = newUser(UserRole.ARTIST, "access-suspended@example.com");
        User withdrawn = newUser(UserRole.ARTIST, "access-withdrawn@example.com");
        setStatus(suspended, UserStatus.SUSPENDED);
        setStatus(withdrawn, UserStatus.WITHDRAWN);
        User otherPartner = newUser(UserRole.EVENT_PARTNER, "access-other-partner@example.com");

        for (UUID target :
                new UUID[] {uuidV7Generator.generate(), otherPartner.getId(), suspended.getId(), withdrawn.getId()}) {
            assertThatThrownBy(() -> access.require(partner.getId(), UserRole.EVENT_PARTNER, target))
                    .as(target.toString())
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                    .isEqualTo(ErrorCode.ARTIST_NOT_FOUND));
        }
    }

    private void setStatus(User user, UserStatus status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status.name(), user.getId());
    }
}
