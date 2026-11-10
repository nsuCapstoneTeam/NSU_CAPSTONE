package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;

import com.nsu.capstone.global.exception.*;
import com.nsu.capstone.identity.application.*;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.repository.UserTermsAgreementRepository;
import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.terms.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoSignupTermsIntegrationTest {
    @Autowired
    ArtistSignupService artists;

    @Autowired
    EventPartnerSignupService partners;

    @Autowired
    RequiredTermsPolicy policy;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    UserTermsAgreementRepository terms;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void startsAndSignsUpBothRolesWithFourTermsAndRejectsMissingOrWrongVersions() {
        var agreements = List.copyOf(policy.requiredTerms());
        assertEquals(4, agreements.size());
        for (UserRole role : List.of(UserRole.ARTIST, UserRole.EVENT_PARTNER)) {
            for (var invalid : List.of(
                    SignupTestTerms.agreements(),
                    agreements.stream()
                            .map(t -> new TermVersion(t.id(), "wrong-version"))
                            .toList())) {
                var failure = assertThrows(BusinessException.class, () -> create(role, invalid));
                assertEquals(ErrorCode.REQUIRED_TERMS_NOT_AGREED, failure.getErrorCode());
            }
            String id = create(role, agreements);
            var consent = sessions.findById(id).orElseThrow().consent();
            for (String field : List.of("emailVerified", "phoneVerified", "adultConfirmed"))
                redis.opsForHash().put(SignupRedisKeys.session(id), field, "true");
            UUID user = role == UserRole.ARTIST
                    ? artists.signup(id, "test-password").userId()
                    : partners.signup(id, "test-password").userId();
            var saved = terms.findByUserId(user);
            assertEquals(4, saved.size());
            assertTrue(saved.stream().allMatch(t -> t.getAgreedAt().equals(consent.agreedAt())));
        }
    }

    private String create(UserRole role, List<TermVersion> agreements) {
        String email = UUID.randomUUID() + "@example.com";
        String ip = "2001:db8:" + UUID.randomUUID().toString().substring(0, 4) + ":1::1";
        return role == UserRole.ARTIST
                ? artists.createSession(email, "01012345678", agreements, ip).signupSessionId()
                : partners.createSession(email, "01012345678", agreements, ip).signupSessionId();
    }
}
