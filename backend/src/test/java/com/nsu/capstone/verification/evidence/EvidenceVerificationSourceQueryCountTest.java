package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * API 01 §7: 일괄 조회의 쿼리 수는 아티스트 수와 관계없다. 이 source는 JdbcClient로만 읽으므로, 증빙 확인 표를 읽는
 * {@code JdbcClient.sql(...)} 호출 수를 센다. 아티스트마다 한 번씩 읽는 구현이면 100명에서 100번으로 늘어난다.
 */
class EvidenceVerificationSourceQueryCountTest extends EvidenceVerificationIntegrationTestBase {

    @Autowired
    EvidenceVerificationSource source;

    @Autowired
    TrustProfileQueryService trustProfileQueryService;

    @MockitoSpyBean
    JdbcClient jdbcClient;

    @Test
    void 아티스트_1명과_100명에서_증빙_확인_표를_읽는_쿼리는_각각_한_번이다() {
        clearInvocations(jdbcClient);
        source.findVerifiedByArtistIds(List.of(uuidV7Generator.generate()));
        int forOne = evidenceQueries();

        clearInvocations(jdbcClient);
        source.findVerifiedByArtistIds(uuids(100));
        int forHundred = evidenceQueries();

        assertThat(forOne).isEqualTo(1);
        assertThat(forHundred).isEqualTo(1);
    }

    @Test
    void 빈_입력은_쿼리하지_않는다() {
        clearInvocations(jdbcClient);

        source.findVerifiedByArtistIds(List.of());

        assertThat(evidenceQueries()).isZero();
    }

    /** Trust Profile 일괄 조회 전체에서도 이 표는 아티스트 수와 관계없이 한 번만 읽는다. */
    @Test
    void Trust_Profile_일괄_조회에서도_아티스트_100명이_쿼리_한_번이다() {
        List<UUID> artistIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            artistIds.add(newUser(UserRole.ARTIST, "evidence-batch-" + i + "@example.com")
                    .getId());
        }
        clearInvocations(jdbcClient);

        assertThat(trustProfileQueryService.findByArtistIds(artistIds)).hasSize(100);

        assertThat(evidenceQueries()).isEqualTo(1);
    }

    private int evidenceQueries() {
        return (int) mockingDetails(jdbcClient).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("sql"))
                .filter(invocation -> {
                    Object sql = invocation.getArgument(0);
                    return sql.toString().contains("evidence_verifications");
                })
                .count();
    }

    private List<UUID> uuids(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(uuidV7Generator.generate());
        }
        return ids;
    }
}
