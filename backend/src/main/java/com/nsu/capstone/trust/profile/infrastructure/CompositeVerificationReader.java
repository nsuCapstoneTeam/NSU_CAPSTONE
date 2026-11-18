package com.nsu.capstone.trust.profile.infrastructure;

import com.nsu.capstone.trust.profile.application.VerificationReader;
import com.nsu.capstone.trust.profile.application.VerificationSource;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spring이 주입한 {@link VerificationSource}들의 결과를 합친다(API 01 §7).
 *
 * <ul>
 *   <li>source마다 한 번만 부른다. 쿼리 수는 source 수로 정해지고 아티스트 수와 관계없다.
 *   <li>합친 뒤 §4.3 순서로 정렬하고 같은 (type, provider)는 하나만 남긴다.
 *   <li>IDENTITY가 없는 아티스트의 COPYRIGHT는 버리고 경고 로그를 남긴다(§4.2 불변식 8).
 *   <li>source 하나가 실패하면 예외가 그대로 나가 전체가 실패한다. 빠진 항목이 "확인 안 됨"으로 보이면 안 된다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeVerificationReader implements VerificationReader {

    /** §4.3 표 순서(enum 선언 순서), 같은 type 안에서는 provider 이름 사전순이다. */
    private static final Comparator<VerificationItem> CONTRACT_ORDER = Comparator.comparing(VerificationItem::type)
            .thenComparing(
                    VerificationItem::provider,
                    Comparator.nullsFirst(Comparator.comparing(VerificationProvider::name)));

    private final List<VerificationSource> sources;

    @Override
    public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
        if (artistIds.isEmpty() || sources.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<VerificationItem>> merged = new HashMap<>();
        for (VerificationSource source : sources) {
            source.findVerifiedByArtistIds(artistIds)
                    .forEach((artistId, items) -> merged.computeIfAbsent(artistId, id -> new LinkedHashSet<>())
                            .addAll(items));
        }
        Map<UUID, List<VerificationItem>> result = new HashMap<>();
        merged.forEach((artistId, items) -> {
            List<VerificationItem> kept = withoutOrphanCopyright(artistId, items);
            if (!kept.isEmpty()) {
                result.put(artistId, kept.stream().sorted(CONTRACT_ORDER).toList());
            }
        });
        return result;
    }

    private static List<VerificationItem> withoutOrphanCopyright(UUID artistId, Set<VerificationItem> items) {
        boolean hasIdentity = items.stream().anyMatch(item -> item.type() == VerificationType.IDENTITY);
        boolean hasCopyright = items.stream().anyMatch(item -> item.type() == VerificationType.COPYRIGHT);
        if (!hasCopyright || hasIdentity) {
            return List.copyOf(items);
        }
        log.warn("IDENTITY 없이 COPYRIGHT가 있어 COPYRIGHT를 뺐다: 아티스트 {}", artistId);
        return items.stream()
                .filter(item -> item.type() != VerificationType.COPYRIGHT)
                .toList();
    }
}
