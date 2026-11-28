package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ArtistProfileSchemaIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final String EMPTY = "ARRAY[]::varchar[]";
    private static final String TWENTY_REGIONS =
            "ARRAY(SELECT lpad(g::text, 2, '0') FROM generate_series(10, 29) g)::varchar[]";
    private static final String TWENTY_ONE_REGIONS =
            "ARRAY(SELECT lpad(g::text, 2, '0') FROM generate_series(10, 30) g)::varchar[]";

    @Test
    void 공연_형태_CHECK는_개수_초과_중복_허용_밖_NULL_다차원_시작_번호가_1이_아닌_배열을_거절한다() {
        assertTypesRejected("ARRAY['SOLO','DUO_GROUP','BAND','DJ','SOLO']");
        assertTypesRejected("ARRAY['SOLO','SOLO']");
        assertTypesRejected("ARRAY['SOLO','BAND','SOLO']");
        assertTypesRejected("ARRAY['SOLO','BAND','DJ','DJ']");
        assertTypesRejected("ARRAY['ORCHESTRA']");
        assertTypesRejected("ARRAY[NULL]::varchar[]");
        assertTypesRejected("ARRAY['SOLO',NULL]::varchar[]");
        assertTypesRejected("ARRAY[['SOLO','BAND']]");
        assertTypesRejected("'[0:0]={SOLO}'::varchar[]");
    }

    @Test
    void 장르_CHECK는_개수_초과_중복_허용_밖_NULL_다차원_시작_번호가_1이_아닌_배열을_거절한다() {
        assertGenresRejected("ARRAY['POP','ROCK','JAZZ','TROT']");
        assertGenresRejected("ARRAY['POP','POP']");
        assertGenresRejected("ARRAY['POP','ROCK','POP']");
        assertGenresRejected("ARRAY['POP','ROCK','ROCK']");
        assertGenresRejected("ARRAY['KPOP']");
        assertGenresRejected("ARRAY[NULL]::varchar[]");
        assertGenresRejected("ARRAY['POP',NULL]::varchar[]");
        assertGenresRejected("ARRAY[['POP','ROCK']]");
        assertGenresRejected("'[0:0]={POP}'::varchar[]");
    }

    @Test
    void 지역_코드_CHECK는_개수_초과_형식_오류_NULL_다차원_시작_번호가_1이_아닌_배열을_거절한다() {
        assertRegionsRejected(TWENTY_ONE_REGIONS);
        assertRegionsRejected("ARRAY['123']");
        assertRegionsRejected("ARRAY['11,22']");
        assertRegionsRejected("ARRAY['1a']");
        assertRegionsRejected("ARRAY['']");
        assertRegionsRejected("ARRAY[NULL]::varchar[]");
        assertRegionsRejected("ARRAY['11',NULL]::varchar[]");
        assertRegionsRejected("ARRAY[['11','26']]");
        assertRegionsRejected("'[0:0]={11}'::varchar[]");
    }

    @Test
    void CHECK는_경계값을_통과시킨다() {
        insert(
                "ARRAY['SOLO','DUO_GROUP','BAND','DJ']",
                "ARRAY['POP','ROCK','JAZZ']",
                TWENTY_REGIONS,
                "0",
                "1000000000");
        insert(EMPTY, EMPTY, EMPTY, "NULL", "NULL");
        insert("ARRAY['DJ']", "ARRAY['TROT']", "ARRAY['11','11680','36']", "1000000000", "1000000000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cardinality(region_codes) FROM artist_profiles WHERE user_id = ?",
                        Integer.class,
                        artist.getId()))
                .isEqualTo(3);
    }

    @Test
    void 출연료_CHECK는_둘_다_있거나_둘_다_없어야_하고_0_이상_순서_상한을_지킨다() {
        insert(EMPTY, EMPTY, EMPTY, "NULL", "NULL");
        insert(EMPTY, EMPTY, EMPTY, "0", "0");
        insert(EMPTY, EMPTY, EMPTY, "300000", "500000");
        insert(EMPTY, EMPTY, EMPTY, "1000000000", "1000000000");

        assertFeeRejected("NULL", "5");
        assertFeeRejected("5", "NULL");
        assertFeeRejected("-1", "5");
        assertFeeRejected("6", "5");
        assertFeeRejected("0", "1000000001");
        assertFeeRejected("1000000001", "1000000001");
    }

    @Test
    void 이름이_비거나_공백뿐인_행은_거절한다() {
        // btrim은 공백(U+0020)만 지운다. 탭·NBSP·보이지 않는 글자는 앱의 UserText가 막고, CHECK는 마지막 방어선이다(V7과 같음)
        for (String name : List.of("", "  ")) {
            assertThatThrownBy(() -> jdbcTemplate.update("""
                            INSERT INTO artist_profiles
                                (user_id, name, artist_types, genres, region_codes, updated_at)
                            VALUES (?, ?, ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                            """, artist.getId(), name))
                    .as("name=[%s]", name)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void 존재하지_않는_사용자의_행은_거절하고_프로필이_있는_사용자는_지울_수_없다() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO artist_profiles
                            (user_id, name, artist_types, genres, region_codes, updated_at)
                        VALUES (?, '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                        """, uuidV7Generator.generate()))
                .isInstanceOf(DataIntegrityViolationException.class);

        insert(EMPTY, EMPTY, EMPTY, "NULL", "NULL");
        // FK에 ON DELETE가 없어 사용자를 지우면 프로필이 따라 지워지지 않고 거절된다(ADR artist_profile/00 결정 4)
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", artist.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공연_형태와_장르_CHECK는_events와_프로필_모두_enum_값_전체와_같다() {
        List<String> artistTypes =
                Arrays.stream(ArtistType.values()).map(Enum::name).toList();
        List<String> genres = Arrays.stream(Genre.values()).map(Enum::name).toList();

        assertThat(allowedValues("ck_artist_profiles_artist_types")).containsExactlyInAnyOrderElementsOf(artistTypes);
        assertThat(allowedValues("ck_events_artist_type")).containsExactlyInAnyOrderElementsOf(artistTypes);
        assertThat(allowedValues("ck_artist_profiles_genres")).containsExactlyInAnyOrderElementsOf(genres);
        assertThat(allowedValues("ck_events_genres")).containsExactlyInAnyOrderElementsOf(genres);
    }

    @Test
    void 지역_점검_함수는_정규형이_아닌_배열이_있으면_실패한다() {
        // 개편 마이그레이션이 코드만 바꾸고 05 §2 정규화(정렬 포함)를 빠뜨린 경우
        for (String regionCodes :
                List.of("ARRAY['26','11']", "ARRAY['11','11680']", "ARRAY['36110']", "ARRAY['11','11']")) {
            insert(EMPTY, EMPTY, regionCodes, "NULL", "NULL");
            assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_artist_profile_regions()"))
                    .as(regionCodes)
                    .hasMessageContaining("아티스트 프로필 지역 코드가 정규형이 아닌 사용자가 있다")
                    .hasMessageContaining(artist.getId().toString());
        }
    }

    @Test
    void 지역_점검_함수는_목록에_없는_코드가_있으면_실패하고_정상_데이터는_통과한다() {
        insert(EMPTY, EMPTY, "ARRAY['11','36','41110']", "NULL", "NULL");
        jdbcTemplate.execute("SELECT check_artist_profile_regions()");

        insert(EMPTY, EMPTY, "ARRAY['11','99999']", "NULL", "NULL");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_artist_profile_regions()"))
                .hasMessageContaining("지역 목록에 없는 아티스트 프로필 지역 코드가 있다")
                .hasMessageContaining("99999");

        insert(EMPTY, EMPTY, "ARRAY['29']", "NULL", "NULL");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_artist_profile_regions()"))
                .hasMessageContaining("29");
    }

    @Test
    void 묶음_점검_함수는_두_표를_모두_검사한다() {
        insert(EMPTY, EMPTY, "ARRAY['11','36','41110']", "NULL", "NULL");
        insertPartnerRegions("ARRAY['11','44']");
        jdbcTemplate.execute("SELECT check_profile_regions()");

        insertPartnerRegions("ARRAY['11','99999']");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_profile_regions()"))
                .hasMessageContaining("99999");

        insertPartnerRegions("ARRAY['11','44']");
        insert(EMPTY, EMPTY, "ARRAY['11','99999']", "NULL", "NULL");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_profile_regions()"))
                .hasMessageContaining("99999");
    }

    private void assertTypesRejected(String artistTypes) {
        assertRejected(artistTypes, EMPTY, EMPTY, "NULL", "NULL");
    }

    private void assertGenresRejected(String genres) {
        assertRejected(EMPTY, genres, EMPTY, "NULL", "NULL");
    }

    private void assertRegionsRejected(String regionCodes) {
        assertRejected(EMPTY, EMPTY, regionCodes, "NULL", "NULL");
    }

    private void assertFeeRejected(String feeMin, String feeMax) {
        assertRejected(EMPTY, EMPTY, EMPTY, feeMin, feeMax);
    }

    private void assertRejected(String artistTypes, String genres, String regionCodes, String feeMin, String feeMax) {
        assertThatThrownBy(() -> insert(artistTypes, genres, regionCodes, feeMin, feeMax))
                .as("%s / %s / %s / %s / %s", artistTypes, genres, regionCodes, feeMin, feeMax)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(String artistTypes, String genres, String regionCodes, String feeMin, String feeMax) {
        jdbcTemplate.update("DELETE FROM artist_profiles");
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, fee_min, fee_max, updated_at)
                VALUES (?, '이름', %s, %s, %s, %s, %s, now())
                """.formatted(artistTypes, genres, regionCodes, feeMin, feeMax), artist.getId());
    }

    private void insertPartnerRegions(String regionCodes) {
        jdbcTemplate.update("DELETE FROM event_partner_profiles");
        jdbcTemplate.update("""
                INSERT INTO event_partner_profiles (user_id, name, event_types, region_codes, updated_at)
                VALUES (?, '이름', ARRAY[]::varchar[], %s, now())
                """.formatted(regionCodes), partner.getId());
    }

    private List<String> allowedValues(String constraint) {
        String definition = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraint);
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("'([A-Z_]+)'").matcher(definition);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
