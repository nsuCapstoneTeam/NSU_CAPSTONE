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
    void rejectsValueConstructorEvenWhenConstructorItselfHasAnnotation() {
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

        assertThat(LombokStyleChecker.check(source))
                .singleElement()
                .satisfies(violation -> assertThat(violation.line()).isEqualTo(7));
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
}
