package com.nsu.capstone.verification.evidence;

import com.nsu.capstone.trust.profile.application.VerificationSource;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Trust Profile에 증빙 확인 기록(사업자등록, 실연 참여, 공식 활동)을 알리는 source(API 01 §4.3, §7, ADR
 * artist_trust_architecture/10). 기록이 있는 항목만 준다. 취소하면 상태 행이 지워지므로 다음 조회부터 빠진다.
 * 확인 시각, 운영자, 취소 사유는 내보내지 않는다(API 01 §5).
 */
@Component
@RequiredArgsConstructor
public class EvidenceVerificationSource implements VerificationSource {

    private final JdbcClient jdbc;

    @Override
    public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
        // TrustProfileQueryService가 이미 막지만, 빈 IN 목록은 SQL 문법 오류라 source도 지킨다
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("""
                        SELECT artist_id, verification_type FROM evidence_verifications
                        WHERE artist_id IN (:artistIds)""")
                .param("artistIds", artistIds)
                .query((rs, rowNum) -> new Row(
                        rs.getObject("artist_id", UUID.class),
                        VerificationType.valueOf(rs.getString("verification_type"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Row::artistId,
                        Collectors.mapping(row -> new VerificationItem(row.type(), null), Collectors.toList())));
    }

    private record Row(UUID artistId, VerificationType type) {}
}
