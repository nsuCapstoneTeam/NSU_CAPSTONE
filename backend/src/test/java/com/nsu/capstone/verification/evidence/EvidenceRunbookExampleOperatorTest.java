package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 예시 {@code operator} 값을 runbook에서 읽는 추출이 문서에 적힌 값을 그대로 돌려주는지 본다. 예시가 허용 값으로 바뀌면
 * "예시 값이면 DB가 거절한다" 테스트가 실패해야 하므로, 추출이 고정 값을 돌려주면 안 된다(ADR artist_trust_architecture/10 7번).
 */
class EvidenceRunbookExampleOperatorTest {

    @Test
    void 예시_operator는_set_줄에_적힌_값을_그대로_돌려준다() {
        String block = """
                \\set ON_ERROR_STOP on
                \\set artist_id '00000000-0000-0000-0000-000000000000'
                \\set operator 'octocat'
                \\set reason 'EVIDENCE_INVALID'
                """;

        assertThat(EvidenceRunbook.exampleOperator(block)).isEqualTo("octocat");
    }

    /** 줄을 못 찾았는데 빈 값으로 넘어가면 DB가 빈 값을 거절해, 문서가 바뀌어도 테스트가 우연히 통과한다. */
    @Test
    void set_operator_줄이_없으면_예시를_찾지_못해_실패한다() {
        String block = """
                \\set ON_ERROR_STOP on
                \\set artist_id '00000000-0000-0000-0000-000000000000'
                """;

        assertThatThrownBy(() -> EvidenceRunbook.exampleOperator(block))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("\\set operator");
    }

    @Test
    void 예시_operator_값이_비어_있으면_실패한다() {
        String block = """
                \\set operator ''
                """;

        assertThatThrownBy(() -> EvidenceRunbook.exampleOperator(block))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("\\set operator");
    }
}
