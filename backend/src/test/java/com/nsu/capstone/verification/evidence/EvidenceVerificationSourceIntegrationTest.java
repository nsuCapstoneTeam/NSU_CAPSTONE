package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** API 01 §4.3·§7: 증빙 확인 기록이 있는 항목만 Trust Profile에 알리는 source(ADR artist_trust_architecture/10 8번). */
class EvidenceVerificationSourceIntegrationTest extends EvidenceVerificationIntegrationTestBase {

    private static final String OPERATOR = "evidence-operator";
    private static final VerificationItem BUSINESS = new VerificationItem(VerificationType.BUSINESS_REGISTRATION, null);
    private static final VerificationItem PERFORMANCE =
            new VerificationItem(VerificationType.PERFORMANCE_PARTICIPATION, null);

    @Autowired
    EvidenceVerificationSource source;

    private EvidenceRunbook runbook;

    @BeforeEach
    void createRunbook() {
        runbook = new EvidenceRunbook(jdbcTemplate);
    }

    @Test
    void 기록한_항목만_그_아티스트에게_나오고_기록이_없는_아티스트는_키가_없다() {
        User recorded = newUser(UserRole.ARTIST, "evidence-source-recorded@example.com");
        User other = newUser(UserRole.ARTIST, "evidence-source-other@example.com");
        User none = newUser(UserRole.ARTIST, "evidence-source-none@example.com");
        runbook.record(uuidV7Generator.generate(), recorded.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        runbook.record(uuidV7Generator.generate(), recorded.getId(), "PERFORMANCE_PARTICIPATION", OPERATOR);
        runbook.record(uuidV7Generator.generate(), other.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        Map<UUID, List<VerificationItem>> items =
                source.findVerifiedByArtistIds(List.of(recorded.getId(), other.getId(), none.getId()));

        assertThat(items).containsOnlyKeys(recorded.getId(), other.getId());
        assertThat(items.get(recorded.getId())).containsExactlyInAnyOrder(BUSINESS, PERFORMANCE);
        assertThat(items.get(other.getId()))
                .containsExactly(new VerificationItem(VerificationType.OFFICIAL_ACTIVITY, null));
    }

    /** ADR "읽기": 빈 입력이면 쿼리하지 않는다(SQL의 빈 IN 목록은 문법 오류다). */
    @Test
    void 빈_입력은_빈_결과다() {
        assertThat(source.findVerifiedByArtistIds(List.of())).isEmpty();
    }
}
