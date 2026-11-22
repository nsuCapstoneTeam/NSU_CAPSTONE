package com.nsu.capstone.event.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 05 §5, 09 §5: 지역 배열에는 FK가 없으므로, 행정구역 개편 마이그레이션이 배열 이전이나 다시 정규화하기를 빠뜨리면 이 테스트가 잡는다.
 * 지역 배열을 가진 두 표(EVENT_PARTNER·아티스트 프로필)를 함께 본다.
 * PR마다 자동으로 돌리는 CI는 아직 없다(NSU-135). 개편 PR 작성자가 직접 돌려 결과를 PR 설명에 적는다.
 *
 * <p>같은 컨테이너에 별도 데이터베이스를 두 표가 모두 있는 버전({@link #PROFILE_TABLES_VERSION})까지 만들고, 그 뒤의 버전마다
 * 그 시점의 지역 목록으로 여러 지역 조합을 담은 프로필을 넣은 뒤 한 버전을 적용하고 묶음 함수 {@code check_profile_regions()}를
 * 부른다. 이어서 시드한 같은 user_id의 배열을 기대값과 비교한다(코드 보존 검사). 개편이 코드를 없앴다면 그 개편 PR이 테스트에
 * {@link #OPERATIONAL_MAPPINGS}에 자기 버전으로 더하는 대응표(옛 코드 → 새 코드 목록, 아래 {@code SUWON_MOVED}처럼)로
 * 기대 배열을 계산한다. 단계마다 그 버전의 대응표만 쓴다.
 * 별도 데이터베이스를 쓰는 이유는 운영과 같은 기본 search_path로 돌게 해서 확장(pgvector 등)을 쓰는 마이그레이션도 그대로 적용하기 위해서다.
 * 운영 기동 경로(Flyway 콜백)에서 점검하지 않는 이유는 ADR event_partner_profile/00 "지역 점검을 강제하는 방법"에 있다.
 * 테스트가 점검 함수를 직접 부르므로, 개편 마이그레이션이 함수 호출 자체를 빠뜨린 것은 잡지 못한다(05 §5와 같은 한계).
 *
 * <p>마이그레이션 번호가 바뀌면 {@link #PROFILE_TABLES_VERSION}도 함께 바꾼다(ADR artist_profile/00 "마이그레이션 번호").
 */
class ProfileRegionMigrationIntegrationTest extends EventIntegrationTestBase {

    /** 두 프로필 표가 모두 있는 첫 버전. 이 버전보다 앞선 버전은 이 테스트가 다시 돌리지 않는다. */
    private static final String PROFILE_TABLES_VERSION = "12";

    private static final String DATABASE = "region_migration_check";
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String FIXTURES = "classpath:db/region-migration-fixture/";
    /** 운영 위치에 개편 마이그레이션(V13)이 이미 있는 상태를 흉내 내는 테스트 전용 위치 */
    private static final String OPERATIONAL_LIKE = "classpath:db/region-migration-operational-like";
    /** 실패 픽스처의 마이그레이션 버전. 픽스처는 모두 이 버전 하나다. */
    private static final String FIXTURE_VERSION = "900";

    /**
     * 운영 개편 마이그레이션의 버전별 대응표(버전 → 개편이 없앤 옛 코드 → 새 코드 목록). 개편 PR이 여기에 자기 버전의 대응표를 더한다.
     * 지금은 지역 개편이 없어 비어 있다.
     */
    private static final Map<String, Map<String, List<String>>> OPERATIONAL_MAPPINGS = Map.of();

    private static final Map<String, Map<String, List<String>>> NO_MAPPING = Map.of();
    private static final Map<String, List<String>> SUWON_MOVED = Map.of("41110", List.of("41999"));
    private static final Map<String, Map<String, List<String>>> SUWON_MOVED_AT_FIXTURE =
            Map.of(FIXTURE_VERSION, SUWON_MOVED);

    @Autowired
    DataSource dataSource;

    /** 지역 배열 컬럼({@code region_codes})을 가진 표. 표를 더하는 PR은 여기에 항목과 최소 행을 더한다. */
    enum ProfileTable {
        EVENT_PARTNER(
                "event_partner_profiles",
                "EVENT_PARTNER 프로필",
                "EVENT_PARTNER",
                "region-partner-",
                "name, event_types",
                "'담당자', ARRAY[]::varchar[]"),
        ARTIST(
                "artist_profiles",
                "아티스트 프로필",
                "ARTIST",
                "region-artist-",
                "name, artist_types, genres",
                "'아티스트', ARRAY[]::varchar[], ARRAY[]::varchar[]");

        private final String table;
        private final String label;
        private final String role;
        private final String emailPrefix;
        private final String columns;
        private final String values;

        ProfileTable(String table, String label, String role, String emailPrefix, String columns, String values) {
            this.table = table;
            this.label = label;
            this.role = role;
            this.emailPrefix = emailPrefix;
            this.columns = columns;
            this.values = values;
        }

        static ProfileTable ofTable(String name) {
            return Arrays.stream(values())
                    .filter(candidate -> candidate.table.equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("지역 배열을 가진 표에 넣을 최소 행 픽스처가 이 테스트에 없다: " + name));
        }

        /** 사용자 한 명과 지역 배열 하나를 가진 프로필 한 행을 넣는다. */
        void insertRow(JdbcTemplate jdbc, String email, String regionCode) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO users (id, email, phone, role, status, created_at, updated_at)"
                            + " VALUES (?, ?, '01000000000', ?, 'ACTIVE', now(), now())",
                    id,
                    email,
                    role);
            jdbc.update(
                    "INSERT INTO " + table + " (user_id, " + columns + ", region_codes, updated_at)" + " VALUES (?, "
                            + values + ", ARRAY[?]::varchar[], now())",
                    id,
                    regionCode);
        }

        /** 지금 지역 목록으로 정규형 지역 조합마다 프로필을 하나씩 넣는다. */
        void insertCombinations(JdbcTemplate jdbc) {
            jdbc.execute(COMBINATIONS_CTE + """
                    , new_users AS (
                        INSERT INTO users (id, email, phone, role, status, created_at, updated_at)
                        SELECT gen_random_uuid(), '%1$s' || n || '@test.local', '01000000000', '%2$s',
                            'ACTIVE', now(), now()
                        FROM numbered
                        RETURNING id, email
                    )
                    INSERT INTO %3$s (user_id, %4$s, region_codes, updated_at)
                    SELECT u.id, %5$s, n.codes::varchar[], now()
                    FROM new_users u
                    JOIN numbered n ON u.email = '%1$s' || n.n || '@test.local'
                    """.formatted(emailPrefix, role, table, columns, values));
        }
    }

    /**
     * 조합: 코드 하나, 시·도 쌍, 같은 시·도의 시·군·구 쌍(이웃한 코드), 시·도와 다른 시·도의 시·군·구,
     * 이웃한 시·도 셋, 같은 시·도의 이웃한 시·군·구 셋(개편이 둘을 합치면 중복이 생기는 경우를 이전 SQL이 다루는지 본다).
     */
    private static final String COMBINATIONS_CTE = """
            WITH cities AS (
                SELECT r.code, r.province_code FROM regions r
                WHERE (SELECT count(*) FROM regions s WHERE s.province_code = r.province_code) > 1
            ), provinces AS (
                SELECT DISTINCT province_code AS code FROM regions
            ), combos AS (
                SELECT ARRAY[code] AS codes FROM provinces
                UNION ALL SELECT ARRAY[code] FROM cities
                UNION ALL
                SELECT ARRAY[a.code, b.code] FROM provinces a JOIN provinces b ON a.code < b.code
                UNION ALL
                SELECT ARRAY[code, next_code] FROM (
                    SELECT code, lead(code) OVER (PARTITION BY province_code ORDER BY code) AS next_code
                    FROM cities) pairs
                WHERE next_code IS NOT NULL
                UNION ALL
                SELECT ARRAY[code, next_code, after_next_code] FROM (
                    SELECT code, lead(code) OVER w AS next_code, lead(code, 2) OVER w AS after_next_code
                    FROM provinces WINDOW w AS (ORDER BY code)) triples
                WHERE after_next_code IS NOT NULL
                UNION ALL
                SELECT ARRAY[code, next_code, after_next_code] FROM (
                    SELECT code, lead(code) OVER w AS next_code, lead(code, 2) OVER w AS after_next_code
                    FROM cities WINDOW w AS (PARTITION BY province_code ORDER BY code)) triples
                WHERE after_next_code IS NOT NULL
                UNION ALL
                SELECT ARRAY(SELECT x FROM unnest(ARRAY[p.code, c.code]) AS x ORDER BY x COLLATE "C")
                FROM provinces p JOIN cities c ON c.province_code <> p.code
            ), numbered AS (
                SELECT codes, row_number() OVER () AS n FROM combos
            )""";

    @BeforeEach
    void createDatabase() {
        dropDatabase();
        jdbcTemplate.execute("CREATE DATABASE " + DATABASE);
    }

    @AfterEach
    void dropDatabase() {
        jdbcTemplate.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    void 여러_지역_조합을_담은_프로필은_최신_마이그레이션까지_점검을_통과한다() {
        migrateStepwiseWithProfiles(EnumSet.allOf(ProfileTable.class), OPERATIONAL_MAPPINGS, MIGRATIONS);
    }

    @Test
    void 시드는_두_표_모두_원소가_여러_개인_배열을_담는다() {
        JdbcTemplate migrated =
                migrateStepwiseWithProfiles(EnumSet.allOf(ProfileTable.class), OPERATIONAL_MAPPINGS, MIGRATIONS);

        for (ProfileTable table : ProfileTable.values()) {
            assertThat(migrated.queryForObject(
                            "SELECT count(*) FROM " + table.table + " WHERE cardinality(region_codes) >= 2",
                            Long.class))
                    .as(table.label)
                    .isPositive();
        }
    }

    @Test
    void 시드는_두_표_모두_원소가_세_개_이상인_배열을_담는다() {
        JdbcTemplate migrated =
                migrateStepwiseWithProfiles(EnumSet.allOf(ProfileTable.class), OPERATIONAL_MAPPINGS, MIGRATIONS);

        for (ProfileTable table : ProfileTable.values()) {
            assertThat(migrated.queryForObject(
                            "SELECT count(*) FROM " + table.table + " WHERE cardinality(region_codes) >= 3",
                            Long.class))
                    .as(table.label)
                    .isPositive();
        }
    }

    @Test
    void 두_표를_모두_옮기고_대응표대로_코드를_보존한_개편은_통과한다() {
        migrateStepwiseWithProfiles(
                EnumSet.allOf(ProfileTable.class), SUWON_MOVED_AT_FIXTURE, MIGRATIONS, fixture("move-both-tables"));
    }

    @Test
    void 운영_개편은_자기_버전의_대응표대로_코드를_보존하면_통과한다() {
        migrateStepwiseWithProfiles(
                EnumSet.allOf(ProfileTable.class), Map.of("13", SUWON_MOVED), MIGRATIONS, OPERATIONAL_LIKE);
    }

    @Test
    void 다른_버전의_대응표로는_그_버전이_없앤_코드를_설명하지_못한다() {
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.allOf(ProfileTable.class), Map.of("14", SUWON_MOVED), MIGRATIONS, OPERATIONAL_LIKE))
                .hasMessageContaining("대응표 없이 사라진 코드: 41110");
    }

    @Test
    void 대응표_없이_코드가_사라진_개편은_실패한다() {
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.allOf(ProfileTable.class), NO_MAPPING, MIGRATIONS, fixture("move-both-tables")))
                .hasMessageContaining("대응표 없이 사라진 코드: 41110");
    }

    @ParameterizedTest
    @EnumSource(ProfileTable.class)
    void 바뀐_원소만_떨어뜨린_이전은_점검_함수를_통과해도_코드_보존_검사에서_실패한다(ProfileTable table) {
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.of(table), SUWON_MOVED_AT_FIXTURE, MIGRATIONS, fixture("drop-changed-element")))
                .hasMessageContaining(table.label)
                .hasMessageContaining("지역 배열")
                .hasMessageContaining("41110");
    }

    @ParameterizedTest
    @EnumSource(ProfileTable.class)
    void 개편_뒤에도_목록에_남은_코드를_떨어뜨린_이전은_실패한다(ProfileTable table) {
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.of(table), NO_MAPPING, MIGRATIONS, fixture("drop-remaining-code")))
                .hasMessageContaining(table.label)
                .hasMessageContaining("남은 코드")
                .hasMessageContaining("41110");
    }

    @ParameterizedTest(name = "{0} - {2}")
    @MethodSource("failureFixturesPerTable")
    void 점검_함수는_표마다_개편_누락을_잡는다(String fixture, String message, ProfileTable table) {
        // "<표> 지역 코드"는 그 표의 점검 함수 메시지에만 있다. 코드 보존 검사가 대신 잡은 실패는 여기서 통과하지 않는다
        assertThatThrownBy(
                        () -> migrateStepwiseWithProfiles(EnumSet.of(table), NO_MAPPING, MIGRATIONS, fixture(fixture)))
                .hasMessageContaining(table.label + " 지역 코드")
                .hasMessageContaining(message);
    }

    static Stream<Arguments> failureFixturesPerTable() {
        // 픽스처 이름 -> 실패 메시지에 있어야 할 조각
        Map<String, String> fixtures = new LinkedHashMap<>();
        fixtures.put("move-without-profiles", "41110");
        fixtures.put("merge-without-normalize", "정규형");
        fixtures.put("new-code-then-lost", "41999");
        fixtures.put("reorg-not-last", "41110");
        return fixtures.entrySet().stream()
                .flatMap(entry -> Arrays.stream(ProfileTable.values())
                        .map(table -> Arguments.of(entry.getKey(), entry.getValue(), table)));
    }

    @Test
    void 실패_픽스처는_운영_위치에_개편이_있어도_V12_바로_위에서_실행한다() {
        // 운영 개편(V13)이 이미 수원시 코드를 옮겼다면, 그 위에서 도는 픽스처는 옮길 코드가 없어 실패하지 않는다
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.allOf(ProfileTable.class),
                        Map.of("13", SUWON_MOVED),
                        MIGRATIONS,
                        OPERATIONAL_LIKE,
                        fixture("move-without-profiles")))
                .hasMessageContaining("지역 코드")
                .hasMessageContaining("41110");
    }

    @Test
    void EVENT_PARTNER_표만_옮기고_아티스트_표를_빠뜨린_개편은_아티스트_점검에서_실패한다() {
        assertThatThrownBy(() -> migrateStepwiseWithProfiles(
                        EnumSet.allOf(ProfileTable.class),
                        SUWON_MOVED_AT_FIXTURE,
                        MIGRATIONS,
                        fixture("move-partner-but-not-artist")))
                .hasMessageContaining(ProfileTable.ARTIST.label + " 지역 코드")
                .hasMessageContaining("41110");
    }

    @Test
    void EVENT_PARTNER_표만_옮긴_픽스처는_EVENT_PARTNER_표만_보면_통과한다() {
        migrateStepwiseWithProfiles(
                EnumSet.of(ProfileTable.EVENT_PARTNER),
                SUWON_MOVED_AT_FIXTURE,
                MIGRATIONS,
                fixture("move-partner-but-not-artist"));
    }

    @Test
    void 아직_없는_프로필_표가_있어도_정리와_시드는_있는_표만_다룬다() {
        DataSource databaseDataSource = databaseDataSource();
        flyway(databaseDataSource, MIGRATIONS)
                .target(MigrationVersion.fromVersion(PROFILE_TABLES_VERSION))
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(databaseDataSource);
        // 새 표를 만드는 개편 PR이 ProfileTable에 표를 등록했지만 그 표가 아직 없는 시점을 흉내 낸다. 그 시점의 묶음 함수는 있는 표만 부른다
        jdbc.execute("ALTER TABLE artist_profiles RENAME TO artist_profiles_not_yet");
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION check_profile_regions() RETURNS void
                LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM check_event_partner_profile_regions();
                END;
                $$""");

        reseed(jdbc, EnumSet.allOf(ProfileTable.class));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_partner_profiles", Long.class))
                .isPositive();
    }

    @Test
    void 지역_배열을_가진_모든_표가_묶음_함수에_연결되어_있다() {
        DataSource databaseDataSource = databaseDataSource();
        flyway(databaseDataSource, MIGRATIONS).load().migrate();

        assertEveryRegionTableIsChecked(new JdbcTemplate(databaseDataSource));
    }

    @Test
    void 묶음_함수가_표_하나를_빠뜨리면_연결_검사가_그_표를_짚어_실패한다() {
        DataSource databaseDataSource = databaseDataSource();
        flyway(databaseDataSource, MIGRATIONS)
                .target(MigrationVersion.fromVersion(PROFILE_TABLES_VERSION))
                .load()
                .migrate();
        fixtureOnly(databaseDataSource, fixture("bundle-misses-artist")).load().migrate();

        assertThatThrownBy(() -> assertEveryRegionTableIsChecked(new JdbcTemplate(databaseDataSource)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("artist_profiles");
    }

    @Test
    void users를_오래_잡은_트랜잭션이_있으면_프로필_표_마이그레이션은_무기한_기다리지_않고_실패한다() throws Exception {
        // FK와 트리거를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 기다리는 동안 뒤에 온 가입도 모두 막히므로 상한을 둔다(V6·V7과 같은 규칙)
        DataSource databaseDataSource = databaseDataSource();
        MigrationVersion profileTables = MigrationVersion.fromVersion(PROFILE_TABLES_VERSION);
        flyway(databaseDataSource, MIGRATIONS)
                .target(versionBefore(databaseDataSource, profileTables))
                .load()
                .migrate();
        try (Connection holder = databaseDataSource.getConnection()) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE users IN ROW EXCLUSIVE MODE");

            assertTimeoutPreemptively(
                    Duration.ofSeconds(15),
                    () -> assertThatThrownBy(() -> flyway(databaseDataSource, MIGRATIONS)
                                    .target(profileTables)
                                    .load()
                                    .migrate())
                            .hasMessageContaining("lock timeout"));
            holder.rollback();
        }
    }

    /**
     * 표마다 다른 표는 정상으로 둔 채 그 표에만 형식은 맞고 목록에 없는 코드를 넣고, 묶음 함수가 실패하는지 본다.
     * 지역 배열을 가진 표는 information_schema에서 찾으므로 함수 이름이나 정의 문자열에 기대지 않는다.
     */
    private static void assertEveryRegionTableIsChecked(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name = 'region_codes' AND data_type = 'ARRAY'
                ORDER BY table_name""", String.class);
        assertThat(tables).as("지역 배열을 가진 표").isNotEmpty();
        List<ProfileTable> found = tables.stream().map(ProfileTable::ofTable).toList();

        for (ProfileTable broken : found) {
            clearProfiles(jdbc);
            for (ProfileTable table : found) {
                table.insertRow(jdbc, table.emailPrefix + "normal@test.local", "11");
            }
            jdbc.execute("SELECT check_profile_regions()");

            broken.insertRow(jdbc, broken.emailPrefix + "unknown@test.local", "99999");

            Throwable thrown = catchThrowable(() -> jdbc.execute("SELECT check_profile_regions()"));

            assertThat(thrown)
                    .as("묶음 함수가 %s의 알 수 없는 코드를 잡아야 한다", broken.table)
                    .isNotNull()
                    .hasMessageContaining("99999");
        }
    }

    /**
     * 기준 버전까지 만든 뒤, 그 뒤의 버전마다 "그 시점의 지역 목록으로 시드 → 한 버전 적용 → 묶음 함수 → 코드 보존 검사"를
     * 반복한다. 개편 마이그레이션이 PR의 마지막 파일이 아니어도, 앞선 개편이 새로 만든 코드도 검사된다.
     * 점검이 실패하면 그 예외가 그대로 나온다.
     *
     * @param tables 시드할 표. 표 하나만 주면 그 표의 점검만 따로 볼 수 있다
     * @param mappings 버전 → 그 버전이 없앤 옛 코드 → 새 코드 목록. 개편 PR이 자기 버전의 대응표를 더한다
     * @param locations 운영 마이그레이션 위치와, 있으면 실패 픽스처 위치({@link #FIXTURES}로 시작). 픽스처가 있으면 운영
     *     마이그레이션은 기준 버전까지만 적용하고 그 뒤에는 픽스처만 적용한다. 운영 위치에 개편이 이미 있어도 픽스처가 그
     *     위가 아니라 기준 버전 바로 위에서 돌게 하기 위해서다
     */
    private JdbcTemplate migrateStepwiseWithProfiles(
            Set<ProfileTable> tables, Map<String, Map<String, List<String>>> mappings, String... locations) {
        DataSource databaseDataSource = databaseDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(databaseDataSource);
        MigrationVersion profileTables = MigrationVersion.fromVersion(PROFILE_TABLES_VERSION);
        String[] fixtures = Arrays.stream(locations)
                .filter(location -> location.startsWith(FIXTURES))
                .toArray(String[]::new);
        String[] operational = Arrays.stream(locations)
                .filter(location -> !location.startsWith(FIXTURES))
                .toArray(String[]::new);
        Supplier<FluentConfiguration> afterProfileTables = () -> fixtures.length == 0
                ? flyway(databaseDataSource, operational)
                : fixtureOnly(databaseDataSource, fixtures);

        flyway(databaseDataSource, operational).target(profileTables).load().migrate();
        reseed(jdbc, tables);
        List<MigrationVersion> later = Arrays.stream(
                        afterProfileTables.get().load().info().pending())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull) // 반복 마이그레이션(R__)은 버전이 없다
                .sorted()
                .toList();
        for (MigrationVersion version : later) {
            Set<ProfileTable> present = existingTables(jdbc, tables);
            Map<ProfileTable, Map<UUID, List<String>>> seeded = new EnumMap<>(ProfileTable.class);
            present.forEach(table -> seeded.put(table, regionCodesByUser(jdbc, table)));

            afterProfileTables.get().target(version).load().migrate();
            jdbc.execute("SELECT check_profile_regions()");

            RegionIndex regions = RegionIndex.load(jdbc);
            Map<String, List<String>> codeMapping = mappings.getOrDefault(version.getVersion(), Map.of());
            for (ProfileTable table : present) {
                assertCodesPreserved(table, seeded.get(table), regionCodesByUser(jdbc, table), regions, codeMapping);
            }
            reseed(jdbc, tables);
        }
        return jdbc;
    }

    /** 이전 시드는 지우고 지금 지역 목록으로 다시 넣는다. 지금 스키마에 있는 표만 다룬다. */
    private static void reseed(JdbcTemplate jdbc, Set<ProfileTable> tables) {
        clearProfiles(jdbc);
        existingTables(jdbc, tables).forEach(table -> table.insertCombinations(jdbc));
        jdbc.execute("SELECT check_profile_regions()");
    }

    private static void clearProfiles(JdbcTemplate jdbc) {
        for (ProfileTable table : existingTables(jdbc, EnumSet.allOf(ProfileTable.class))) {
            jdbc.update("DELETE FROM " + table.table);
        }
        jdbc.update("DELETE FROM users WHERE email LIKE 'region-%@test.local'");
    }

    /**
     * 지금 스키마에 실제로 있는 표만 고른다. 새 표를 만드는 개편 PR이 ProfileTable에 표를 등록해도, 그 표가 만들어지기 전
     * 버전에서는 정리·시드·비교가 그 표를 건드리지 않게 한다. 마지막 스키마에서 미등록 표를 거절하는 연결 검사와는 별개다.
     */
    private static Set<ProfileTable> existingTables(JdbcTemplate jdbc, Collection<ProfileTable> tables) {
        Set<ProfileTable> existing = EnumSet.noneOf(ProfileTable.class);
        for (ProfileTable table : tables) {
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table.table))) {
                existing.add(table);
            }
        }
        return existing;
    }

    private static Map<UUID, List<String>> regionCodesByUser(JdbcTemplate jdbc, ProfileTable table) {
        Map<UUID, List<String>> codes = new HashMap<>();
        for (Map<String, Object> row :
                jdbc.queryForList("SELECT user_id, array_to_string(region_codes, ',') AS codes FROM " + table.table)) {
            String joined = (String) row.get("codes");
            codes.put((UUID) row.get("user_id"), joined.isEmpty() ? List.of() : List.of(joined.split(",")));
        }
        return codes;
    }

    /**
     * 점검 함수는 남은 코드의 존재와 정규형만 보므로 이전 SQL이 원소를 떨어뜨려도 통과한다. 그래서 시드한 배열과 비교한다.
     * <ul>
     *   <li>개편 뒤에도 regions에 남은 코드는 배열에 그대로 있거나 그 시·도 코드로 덮여 있어야 한다.
     *   <li>비어 있지 않던 배열은 비지 않아야 한다.
     *   <li>사라진 코드는 대응표로 기대 배열을 계산해 같은지 본다. 대응표 없이 사라진 코드가 있으면 실패한다.
     * </ul>
     */
    private static void assertCodesPreserved(
            ProfileTable table,
            Map<UUID, List<String>> seeded,
            Map<UUID, List<String>> migrated,
            RegionIndex regions,
            Map<String, List<String>> codeMapping) {
        assertThat(migrated.keySet()).as(table.label + " 행").isEqualTo(seeded.keySet());
        for (Map.Entry<UUID, List<String>> entry : seeded.entrySet()) {
            List<String> before = entry.getValue();
            List<String> after = migrated.get(entry.getKey());
            List<String> expectedBeforeNormalize = new ArrayList<>();
            boolean anyVanished = false;
            for (String code : before) {
                if (regions.exists(code)) {
                    if (!regions.covers(after, code)) {
                        throw new AssertionError(table.label + ": 개편 뒤에도 목록에 남은 코드가 지역 배열에서 빠졌다: " + code + " (이전 "
                                + before + ", 이후 " + after + ")");
                    }
                    expectedBeforeNormalize.add(code);
                    continue;
                }
                List<String> replacement = codeMapping.get(code);
                if (replacement == null) {
                    throw new AssertionError(table.label + ": 대응표 없이 사라진 코드: " + code);
                }
                anyVanished = true;
                expectedBeforeNormalize.addAll(replacement);
            }
            if (!before.isEmpty() && after.isEmpty()) {
                throw new AssertionError(table.label + ": 비어 있지 않던 지역 배열이 비었다: " + before);
            }
            if (anyVanished) {
                List<String> expected = regions.normalize(expectedBeforeNormalize);
                if (!expected.equals(after)) {
                    throw new AssertionError(table.label + ": 이전한 지역 배열이 대응표로 계산한 기대값과 다르다: 기대 " + expected + ", 실제 "
                            + after + " (이전 " + before + ")");
                }
            }
        }
    }

    /**
     * 개편 뒤의 지역 목록. 05 §2 정규화를 테스트에서 다시 계산하는 데 쓴다.
     * 앱의 {@code RegionNormalizer}는 운영 DB를 읽어서, 별도 데이터베이스의 목록에는 쓸 수 없다.
     */
    private record RegionIndex(Map<String, String> provinceOfCity, Map<String, Long> citiesPerProvince) {

        static RegionIndex load(JdbcTemplate jdbc) {
            Map<String, String> provinceOfCity = new HashMap<>();
            for (Map<String, Object> row : jdbc.queryForList("SELECT code, province_code FROM regions")) {
                provinceOfCity.put((String) row.get("code"), (String) row.get("province_code"));
            }
            Map<String, Long> citiesPerProvince = provinceOfCity.values().stream()
                    .collect(Collectors.groupingBy(code -> code, Collectors.counting()));
            return new RegionIndex(provinceOfCity, citiesPerProvince);
        }

        boolean exists(String code) {
            return citiesPerProvince.containsKey(code) || provinceOfCity.containsKey(code);
        }

        /** 배열이 이 코드를 가졌거나, 이 코드가 시·군·구이고 배열이 그 시·도 코드를 가졌다. */
        boolean covers(Collection<String> array, String code) {
            return array.contains(code)
                    || (provinceOfCity.containsKey(code) && array.contains(provinceOfCity.get(code)));
        }

        List<String> normalize(Collection<String> codes) {
            Set<String> provinces = new TreeSet<>();
            for (String code : codes) {
                if (citiesPerProvince.containsKey(code)) {
                    provinces.add(code);
                }
            }
            Set<String> result = new TreeSet<>(provinces);
            for (String code : codes) {
                if (provinces.contains(code)) {
                    continue;
                }
                String province = provinceOfCity.get(code);
                if (province == null) {
                    throw new AssertionError("대응표가 가리키는 코드가 regions에 없다: " + code);
                }
                if (citiesPerProvince.get(province) == 1) {
                    result.add(province);
                } else if (!provinces.contains(province)) {
                    result.add(code);
                }
            }
            return List.copyOf(result);
        }
    }

    private static String fixture(String name) {
        return FIXTURES + name;
    }

    private static MigrationVersion versionBefore(DataSource databaseDataSource, MigrationVersion version) {
        return Arrays.stream(
                        flyway(databaseDataSource, MIGRATIONS).load().info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.compareTo(version) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    /** 픽스처 위치만 쓴다. 적용 이력에 있는 운영 마이그레이션이 이 위치에 없어도 검증이 막지 않게 한다. */
    private static FluentConfiguration fixtureOnly(DataSource databaseDataSource, String... fixtureLocations) {
        return flyway(databaseDataSource, fixtureLocations).ignoreMigrationPatterns("*:missing");
    }

    private static FluentConfiguration flyway(DataSource databaseDataSource, String... locations) {
        return Flyway.configure().dataSource(databaseDataSource).locations(locations);
    }

    private DataSource databaseDataSource() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        String url = hikari.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
        return new DriverManagerDataSource(url, hikari.getUsername(), hikari.getPassword());
    }
}
