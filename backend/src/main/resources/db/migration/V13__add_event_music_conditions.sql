-- 행사의 음악 조건 선택값 mood·tempo·vocal (03 행사 API §3.1·§3.3, ADR event_management/05 결정 6).
-- 세 열은 NULL 허용이고 NULL이 "고르지 않음"이다. 이 마이그레이션 전에 만든 행은 세 값이 모두 NULL이다.
-- 장르 모양 제약(ck_events_genres_distinct)도 함께 더한다. V5의 ck_events_genres는 개수와 허용 값만 보므로
-- 중복({POP,POP}), 2차원 배열, 하한이 1이 아닌 배열이 통과한다. 그런 행은 추천 입력(MusicConditions)을 막는다.
--
-- 락 범위: 아래 ALTER TABLE이 잡은 ACCESS EXCLUSIVE 잠금은 파일 끝까지(마이그레이션 트랜잭션이 끝날 때까지)
-- 유지된다. 표를 두 번 훑는다(장르 정리, CHECK 검증). 데이터가 있는 DB에 적용하기 전에 행 수와
-- 장르 정리가 고칠 행 수를 센다(PR 설명의 배포 확인 항목).
-- 롤백은 앱만 되돌린다. 스키마까지 되돌릴 때는 새 마이그레이션으로 CHECK 넷과 열 셋을 지운다. 그때 고른
-- 음악 조건은 사라진다. 장르 정리는 같은 집합을 다시 쓴 것이라 되돌리지 않는다.

-- 긴 트랜잭션이 events를 붙잡고 있으면 아래 문장이 락 대기열에서 줄을 서고, 그 뒤에 온 모든 요청이
-- 함께 멈춘다. 3초 안에 락을 얻지 못하면 마이그레이션을 실패시켜 서비스가 멈추지 않게 한다(V6~V10과 같다).
SET LOCAL lock_timeout = '3s';

-- 기본값이 없는 NULL 허용 열 추가는 테이블을 다시 쓰지 않는 메타데이터 변경이다.
ALTER TABLE events
    ADD COLUMN mood VARCHAR(32),
    ADD COLUMN tempo VARCHAR(32),
    ADD COLUMN vocal VARCHAR(32);

-- 새 장르 모양 제약을 어기는 행만 같은 장르 집합으로 다시 쓴다. 첫 등장 순서를 지키며 중복을 지우고
-- 1차원·하한 1로 편다. DB는 장르 순서를 본 적이 없고 순서는 EventContent가 읽을 때 정한다.
-- updated_at은 바꾸지 않는다. 사용자가 고친 것이 아니라 뜻이 같은 정리이기 때문이다.
-- WHERE 식은 아래 ck_events_genres_distinct의 부정이라, 이 문장이 고치는 행이 곧 제약이 거절할 행이다.
UPDATE events
SET genres = ARRAY(
    SELECT first_seen.genre
    FROM (
        SELECT g.genre, min(g.position) AS position
        FROM unnest(events.genres) WITH ORDINALITY AS g(genre, position)
        GROUP BY g.genre
    ) AS first_seen
    ORDER BY first_seen.position
)
WHERE NOT (
    array_ndims(genres) = 1
    AND array_lower(genres, 1) = 1
    AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
    AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
);

ALTER TABLE events
    ADD CONSTRAINT ck_events_mood CHECK (
        mood IS NULL OR mood IN ('ENERGETIC', 'CHEERFUL', 'EMOTIONAL', 'CALM', 'ROMANTIC', 'GRAND')
    ),
    ADD CONSTRAINT ck_events_tempo CHECK (tempo IS NULL OR tempo IN ('SLOW', 'MEDIUM', 'FAST')),
    ADD CONSTRAINT ck_events_vocal CHECK (
        vocal IS NULL OR vocal IN ('INSTRUMENTAL', 'WITH_VOCALS', 'MALE_VOCALS', 'FEMALE_VOCALS')
    ),
    ADD CONSTRAINT ck_events_genres_distinct CHECK (
        array_ndims(genres) = 1
        AND array_lower(genres, 1) = 1
        AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
        AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
    );
