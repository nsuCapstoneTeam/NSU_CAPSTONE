package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 원칙 테스트(ADR artist_trust_architecture/10 9번, NSU-33 AC "Trust Signal이 Matching Score와 별도로 관리된다"):
 * 증빙 확인 기록은 표시 전용이다. 추천·유사도·AI 입력 등 모듈 밖 코드가 이 기록에 닿는 길을 막는다. import만 보지 않고,
 * 모듈 밖 {@code src/main} 파일(Java, SQL, 설정)에 패키지 이름이나 테이블 이름이 문자열로라도 나오면 실패한다.
 * 완전 수식 이름(FQN)이나 native SQL로 읽는 경로도 이렇게 걸린다.
 */
class EvidenceVerificationDisplayOnlyScanTest {

    private static final Path MAIN = Paths.get("src/main");
    private static final Path MODULE = Paths.get("src/main/java/com/nsu/capstone/verification/evidence");
    private static final List<String> FORBIDDEN = List.of("verification.evidence", "evidence_verification");

    /** 이 모듈이 만든 마이그레이션만 테이블 이름을 쓸 수 있다. */
    private static final Set<String> ALLOWED_MIGRATIONS =
            Set.of("src/main/resources/db/migration/V12__create_evidence_verifications.sql");

    @Test
    void 모듈_밖_운영_파일은_증빙_확인_패키지나_테이블_이름을_문자열로도_쓰지_않는다() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN)) {
            violations = files.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(MODULE))
                    .filter(path -> !ALLOWED_MIGRATIONS.contains(path.toString().replace('\\', '/')))
                    .filter(EvidenceVerificationDisplayOnlyScanTest::mentionsForbidden)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(violations).as("증빙 확인 기록에 닿는 모듈 밖 파일").isEmpty();
    }

    @Test
    void 스캔이_실제로_모듈_안의_금지_문자열을_찾을_수_있다() throws IOException {
        // 이 검사가 아무것도 못 찾는 상태(경로 오류 등)로 통과하지 않게, 모듈 안에서는 찾아지는지 확인한다
        List<String> hits;
        try (Stream<Path> files = Files.walk(MODULE)) {
            hits = files.filter(Files::isRegularFile)
                    .filter(EvidenceVerificationDisplayOnlyScanTest::mentionsForbidden)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(hits).isNotEmpty();
    }

    private static boolean mentionsForbidden(Path path) {
        try {
            String content = Files.readString(path);
            return FORBIDDEN.stream().anyMatch(content::contains);
        } catch (IOException e) {
            // 바이너리 등 글자로 읽을 수 없는 파일은 문자열을 담지 않는다
            return false;
        }
    }
}
