package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lombok 스타일 규칙")
class LombokStyleCheckerTest {

    @Test
    void rejectsLombokImportOutsideAllowList() {
        String source = """
                package sample;

                import lombok.Data;

                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(3);
            assertThat(violation.message()).contains("lombok.Data");
        });
    }

    @Test
    void acceptsLombokImportsOnAllowList() {
        String source = """
                package sample;

                import lombok.AccessLevel;
                import lombok.Getter;
                import lombok.NoArgsConstructor;
                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;

                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsWildcardLombokImport() {
        String source = """
                package sample;

                import lombok.*;

                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(3);
            assertThat(violation.message()).contains("lombok.*");
        });
    }

    @Test
    void rejectsStaticLombokImportEvenForAllowedClass() {
        String source = """
                package sample;

                import static lombok.AccessLevel.PROTECTED;

                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(3);
            assertThat(violation.message()).contains("lombok.AccessLevel.PROTECTED");
        });
    }

    @Test
    void rejectsFullyQualifiedLombokAnnotationOutsideAllowList() {
        String source = """
                package sample;

                @lombok.Data
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(3);
            assertThat(violation.message()).contains("lombok.Data");
        });
    }

    @Test
    void rejectsNoArgsConstructorWithoutAccessLevel() {
        String source = """
                package sample;

                import lombok.NoArgsConstructor;

                @NoArgsConstructor
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(5);
            assertThat(violation.message()).contains("@NoArgsConstructor").contains("AccessLevel.PROTECTED");
        });
    }

    @Test
    void acceptsNoArgsConstructorWithProtectedAccess() {
        String source = """
                package sample;

                import lombok.AccessLevel;
                import lombok.NoArgsConstructor;

                @NoArgsConstructor(access = AccessLevel.PROTECTED)
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsNoArgsConstructorWithNonProtectedAccess() {
        String source = """
                package sample;

                import lombok.AccessLevel;
                import lombok.NoArgsConstructor;

                @NoArgsConstructor(access = AccessLevel.PUBLIC)
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("AccessLevel.PROTECTED");
        });
    }

    @Test
    void acceptsNoArgsConstructorThatIsNotFromLombok() {
        String source = """
                package sample;

                import com.example.NoArgsConstructor;

                @NoArgsConstructor
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsNoArgsConstructorWithoutAccessLevelWhenLombokIsImportedWithWildcard() {
        String source = """
                package sample;

                import lombok.*;

                @NoArgsConstructor
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).hasSize(2).last().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(5);
            assertThat(violation.message()).contains("AccessLevel.PROTECTED");
        });
    }

    @Test
    void acceptsNoArgsConstructorThatIsNotImported() {
        String source = """
                package sample;

                @NoArgsConstructor
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsHandWrittenLogger() {
        String source = """
                package sample;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                class Sample {
                    private static final Logger log = LoggerFactory.getLogger(Sample.class);
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(7);
            assertThat(violation.message()).contains("@Slf4j");
        });
    }

    @Test
    void ignoresCommentsAndStringLiterals() {
        String source = """
                package sample;

                /**
                 * @lombok.Data 는 쓰지 않는다.
                 */
                class Sample {
                    // LoggerFactory.getLogger(Sample.class) 대신 @Slf4j를 쓴다.
                    static final String GUIDE = "@NoArgsConstructor와 LoggerFactory.getLogger(x)";
                    static final String BLOCK = \"""
                            @lombok.Data
                            \""";
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsHandWrittenSimpleGetter() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    public String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getName").contains("@Getter");
        });
    }

    @Test
    void rejectsSimpleGetterReturningThisFieldAndBooleanIsGetter() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";
                    private boolean active = true;

                    public String getName() {
                        return this.name;
                    }

                    public boolean isActive() {
                        return active;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source))
                .extracting(LombokStyleChecker.Violation::line)
                .containsExactly(7, 11);
    }

    @Test
    void acceptsGetterWithLogicAndGetterOfNonInstanceField() {
        String source = """
                package sample;

                class Sample {
                    static final String VERSION = "1";
                    private String name;

                    public String getName() {
                        return name == null ? "" : name;
                    }

                    public String getVersion() {
                        return VERSION;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsHandWrittenValueConstructor() {
        String source = """
                package sample;

                class Sample {
                    private final String name;
                    private final int size;

                    Sample(String name, int size) {
                        this.name = name;
                        this.size = size;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(7);
            assertThat(violation.message()).contains("@RequiredArgsConstructor");
        });
    }

    @Test
    void acceptsValueConstructorWhoseParameterHasAnnotation() {
        String source = """
                package sample;

                class Sample {
                    private final Clock clock;

                    Sample(@Qualifier("system") Clock clock) {
                        this.clock = clock;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsValueConstructorThatItselfHasAnnotation() {
        String source = """
                package sample;

                class Sample {
                    private final String name;

                    @Autowired
                    Sample(String name) {
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorWithLogic() {
        String source = """
                package sample;

                class Sample {
                    private final String name;
                    private final int size;

                    Sample(String name, int size) {
                        this.name = Objects.requireNonNull(name);
                        this.size = size;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsHandWrittenEmptyProtectedConstructor() {
        String source = """
                package sample;

                class Sample {
                    protected Sample() {}
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(4);
            assertThat(violation.message()).contains("@NoArgsConstructor");
        });
    }

    @Test
    void doesNotCheckEnumRecordAndInterfaceDeclarations() {
        String source = """
                package sample;

                enum Level {
                    LOW("low");

                    private static final Logger log = LoggerFactory.getLogger(Level.class);
                    private final String label;

                    Level(String label) {
                        this.label = label;
                    }

                    String getLabel() {
                        return label;
                    }
                }

                record Pair(String first) {
                    static final Logger LOG = LoggerFactory.getLogger(Pair.class);
                }

                interface Marker {
                    Logger LOG = LoggerFactory.getLogger(Marker.class);
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsFullyQualifiedNoArgsConstructorWithoutAccessLevel() {
        String source = """
                package sample;

                @lombok.NoArgsConstructor
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(3);
            assertThat(violation.message()).contains("@NoArgsConstructor").contains("AccessLevel.PROTECTED");
        });
    }

    @Test
    void acceptsFullyQualifiedNoArgsConstructorWithProtectedAccess() {
        String source = """
                package sample;

                @lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsNoArgsConstructorWhoseArgumentsSpanSeveralLines() {
        String source = """
                package sample;

                import lombok.AccessLevel;
                import lombok.NoArgsConstructor;

                @NoArgsConstructor(
                        // JPA가 쓰는 기본 생성자라 밖에 열지 않는다
                        access = AccessLevel.PROTECTED)
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsNoArgsConstructorWhoseMultiLineArgumentsLackProtectedAccess() {
        String source = """
                package sample;

                import lombok.AccessLevel;
                import lombok.NoArgsConstructor;

                @NoArgsConstructor(
                        access = AccessLevel.PUBLIC)
                class Sample {}
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("AccessLevel.PROTECTED");
        });
    }

    @Test
    void doesNotCheckAnonymousClassBodies() {
        String source = """
                package sample;

                class Sample {
                    Object create() {
                        return new Object() {
                            private int size;

                            public int getSize() {
                                return size;
                            }
                        };
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void reportsSourceThatDoesNotParseAsViolation() {
        String source = """
                package sample;

                class Sample {
                    void broken( {
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isNotEmpty().first().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(4);
            assertThat(violation.message()).startsWith("파싱 실패");
        });
    }

    @Test
    void acceptsLoggerInInterfaceEnumAndRecordNestedInsideAClass() {
        String source = """
                package sample;

                class Outer {
                    interface Marker {
                        Logger LOG = LoggerFactory.getLogger(Marker.class);
                    }

                    enum Level {
                        LOW;

                        private static final Logger log = LoggerFactory.getLogger(Level.class);
                    }

                    record Pair(String first) {
                        static final Logger LOG = LoggerFactory.getLogger(Pair.class);
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsLoggerInClassNestedInsideInterface() {
        String source = """
                package sample;

                interface Marker {
                    class Impl {
                        private static final Logger log = LoggerFactory.getLogger(Impl.class);
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(5);
            assertThat(violation.message()).contains("@Slf4j");
        });
    }

    @Test
    void acceptsConstructorThatAssignsNonFinalField() {
        String source = """
                package sample;

                class Sample {
                    private String name;

                    Sample(String name) {
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorThatTakesOnlySomeOfTheFinalFields() {
        String source = """
                package sample;

                class Sample {
                    private final String name;
                    private final int size;

                    {
                        this.size = 1;
                    }

                    Sample(String name) {
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorWhoseParametersAreInDifferentOrderThanTheFields() {
        String source = """
                package sample;

                class Sample {
                    private final String name;
                    private final int size;

                    Sample(int size, String name) {
                        this.size = size;
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorWhoseParameterTypeDiffersFromTheField() {
        String source = """
                package sample;

                class Sample {
                    private final long size;

                    Sample(int size) {
                        this.size = size;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorThatDeclaresThrows() {
        String source = """
                package sample;

                class Sample {
                    private final String name;

                    Sample(String name) throws IOException {
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorWithVarargsParameter() {
        String source = """
                package sample;

                class Sample {
                    private final String[] names;

                    Sample(String... names) {
                        this.names = names;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsConstructorOfClassWithNonNullAnnotatedField() {
        String source = """
                package sample;

                class Sample {
                    private final String name;

                    @NonNull
                    private String title;

                    Sample(String name) {
                        this.name = name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsGetterOfFieldWhoseNameStartsWithNonAsciiLetter() {
        String source = """
                package sample;

                class Sample {
                    private String état = "a";

                    public String getÉtat() {
                        return état;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getÉtat").contains("@Getter");
        });
    }

    @Test
    void rejectsGetterNamedWithTitleCaseOfFirstLetter() {
        String source = """
                package sample;

                class Sample {
                    private String ǆx = "a";

                    public String getǅx() {
                        return ǆx;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getǅx").contains("@Getter");
        });
    }

    @Test
    void rejectsGetterNamedWithUpperCaseOfFirstLetterWhenSecondLetterIsUpperCase() {
        String source = """
                package sample;

                class Sample {
                    private String ǆBc = "a";

                    public String getǄBc() {
                        return ǆBc;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getǄBc").contains("@Getter");
        });
    }

    @Test
    void rejectsGetterNamedWithFirstLetterThatIsAlreadyUpperCase() {
        String source = """
                package sample;

                class Sample {
                    private String Ǆx = "a";

                    public String getǄx() {
                        return Ǆx;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getǄx").contains("@Getter");
        });
    }

    @Test
    void rejectsGetterNamedWithTitleCaseOfFirstLetterWhenFieldNameHasTwoLetters() {
        String source = """
                package sample;

                class Sample {
                    private String ǆB = "a";

                    public String getǅB() {
                        return ǆB;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getǅB").contains("@Getter");
        });
    }

    @Test
    void acceptsGetterWhoseNameDiffersFromTheLombokGetterName() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    public String getDisplayName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsGetterWhoseReturnTypeDiffersFromTheFieldType() {
        String source = """
                package sample;

                class Sample {
                    private int size;

                    public long getSize() {
                        return size;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsGetPrefixedGetterOfPrimitiveBooleanField() {
        String source = """
                package sample;

                class Sample {
                    private boolean active = true;

                    public boolean getActive() {
                        return active;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsGetterOfBooleanFieldWhoseNameStartsWithIs() {
        String source = """
                package sample;

                class Sample {
                    private boolean isActive = true;

                    public boolean isActive() {
                        return isActive;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("isActive").contains("@Getter");
        });
    }

    @Test
    void acceptsIsPrefixedGetterOfBoxedBooleanField() {
        String source = """
                package sample;

                class Sample {
                    private Boolean active = true;

                    public Boolean isActive() {
                        return active;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsGetPrefixedGetterOfBoxedBooleanField() {
        String source = """
                package sample;

                class Sample {
                    private Boolean active = true;

                    public Boolean getActive() {
                        return active;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getActive").contains("@Getter");
        });
    }

    @Test
    void acceptsGetterWithAnnotationOtherThanOverride() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    @JsonIgnore
                    public String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsGetterThatOnlyHasOverrideAnnotation() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    @Override
                    public String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(7);
            assertThat(violation.message()).contains("getName").contains("@Getter");
        });
    }

    @Test
    void acceptsEmptyProtectedConstructorThatHasAnnotation() {
        String source = """
                package sample;

                class Sample {
                    @Deprecated
                    protected Sample() {}
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsGetterOfFirstFieldInMultiVariableDeclaration() {
        String source = """
                package sample;

                class Sample {
                    private String first, last;

                    public String getFirst() {
                        return first;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getFirst").contains("@Getter");
        });
    }

    @Test
    void rejectsGetterOfLastFieldInMultiVariableDeclarationWithInitializer() {
        String source = """
                package sample;

                class Sample {
                    private int a = 1, b;

                    public int getB() {
                        return b;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(6);
            assertThat(violation.message()).contains("getB").contains("@Getter");
        });
    }

    @Test
    void ignoresLombokTextAfterEscapedQuotesInsideTextBlock() {
        String source = """
                package sample;

                class Sample {
                    static final String BLOCK = \"""
                            \\\""" @lombok.Data
                            \""";
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void rejectsSimpleGetterRightAfterTextBlockThatContainsEscapedQuotes() {
        String source = """
                package sample;

                class Sample {
                    static final String BLOCK = \"""
                            \\\""" is not the end
                            \""";
                    private String name = "a";

                    public String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).singleElement().satisfies(violation -> {
            assertThat(violation.line()).isEqualTo(9);
            assertThat(violation.message()).contains("getName").contains("@Getter");
        });
    }

    @Test
    void acceptsEmptyProtectedConstructorThatDeclaresThrows() {
        String source = """
                package sample;

                class Sample {
                    protected Sample() throws IOException {}
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsFinalGetter() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    public final String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsGetterThatDeclaresThrows() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    public String getName() throws IOException {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }

    @Test
    void acceptsSynchronizedGetter() {
        String source = """
                package sample;

                class Sample {
                    private String name = "a";

                    public synchronized String getName() {
                        return name;
                    }
                }
                """;

        assertThat(LombokStyleChecker.check(source)).isEmpty();
    }
}
