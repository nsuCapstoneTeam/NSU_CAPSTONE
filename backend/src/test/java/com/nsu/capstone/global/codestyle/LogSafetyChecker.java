package com.nsu.capstone.global.codestyle;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * 로그와 예외 메시지에 개인정보와 요청 입력이 새지 않도록 src/main/java를 JDK 파서의 구문 트리로 검사한다
 * (ADR logging/00 Decision 8). 구문만 보므로 타입은 같은 파일의 선언과 이름으로 추정한다.
 */
final class LogSafetyChecker {

    record Violation(int line, String rule, String message) {}

    /** 이름에 이 단어가 들어 있으면 개인정보나 비밀값으로 본다. camelCase·snake_case를 단어로 쪼개 비교한다. */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "email",
            "phone",
            "password",
            "passwd",
            "token",
            "otp",
            "secret",
            "jwt",
            "credential",
            "credentials",
            "apikey",
            "birth",
            "birthday",
            "birthdate",
            "ssn",
            "nonce",
            "verifier");

    private static final Set<String> LOG_METHODS = Set.of("trace", "debug", "info", "warn", "error");

    /** 콘솔로 결과를 내는 시뮬레이션 CLI. S1의 유일한 허용 파일이다. */
    private static final String S1_ALLOWED_FILE = "trust/simulation/ReliabilitySimulator.java";

    private static final Set<String> MESSAGE_METHODS = Set.of("getMessage", "getLocalizedMessage", "toString");

    private static final Set<String> RESPONSE_BODY_METHODS = Set.of("getResponseBodyAsString", "getResponseBodyAs");

    /** 선언을 못 찾을 때(필드, 다른 파일) 예외로 보는 관례 이름 */
    private static final Set<String> EXCEPTION_NAMES =
            Set.of("e", "ex", "exc", "exception", "cause", "throwable", "err", "error", "t");

    private static final String S4_OWNER_PACKAGE = "/global/logging/";

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Pattern ALL_CAPS = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** 다른 파일의 record·DTO는 선언을 못 보므로 이름 접미사로 추정한다. */
    private static final Pattern DTO_SUFFIX = Pattern.compile("(Dto|DTO|Request|Response|Command|Payload|Body)$");

    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|[_\\W]+");

    private LogSafetyChecker() {}

    /**
     * @param relativePath backend/ 기준 경로. 규칙 예외(S1 허용 파일, S4의 global/logging)를 정하는 데 쓴다.
     * @param source 소스 원문
     */
    static List<Violation> check(String relativePath, String source) {
        return check(relativePath, source, Set.of());
    }

    /**
     * @param knownRecords 레포의 다른 파일에서 선언한 record 이름. S2b가 이 이름의 타입을 DTO로 본다.
     */
    static List<Violation> check(String relativePath, String source, Set<String> knownRecords) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK가 필요하다. 이 검사는 jdk.compiler 모듈의 파서를 쓴다");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file =
                new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source;
                    }
                };
        JavacTask task =
                (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));
        List<Violation> violations = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                new Scanner(relativePath, unit, Trees.instance(task).getSourcePositions(), violations, knownRecords)
                        .scan(unit, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Violation> parseFailures = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> new Violation(
                        (int) diagnostic.getLineNumber(), "PARSE", "파싱 실패: " + diagnostic.getMessage(Locale.ENGLISH)))
                .toList();
        if (!parseFailures.isEmpty()) {
            return parseFailures;
        }
        violations.sort(Comparator.comparingInt(Violation::line));
        return violations;
    }

    private static final class Scanner extends TreeScanner<Void, Void> {

        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<Violation> violations;

        /** 변수 이름 → 선언 타입의 단순 이름. 클래스, 메서드, 블록, 람다마다 한 겹이다. */
        private final Deque<Map<String, String>> scopes = new ArrayDeque<>();

        /** 이 파일에서 선언한 record 이름. 다른 파일의 record는 접미사로 추정한다. */
        private final Set<String> localRecords = new HashSet<>();

        /** S1을 적용하지 않는 파일(시뮬레이터 CLI) */
        private final boolean s1Allowed;

        /** 요청 ID의 주인인 global.logging 안에서는 S4를 적용하지 않는다 */
        private final boolean s4Allowed;

        Scanner(
                String relativePath,
                CompilationUnitTree unit,
                SourcePositions positions,
                List<Violation> violations,
                Set<String> knownRecords) {
            this.s1Allowed = relativePath.endsWith(S1_ALLOWED_FILE);
            this.s4Allowed = relativePath.contains(S4_OWNER_PACKAGE);
            this.unit = unit;
            this.positions = positions;
            this.violations = violations;
            this.localRecords.addAll(knownRecords);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (node.getKind() == Tree.Kind.RECORD) {
                localRecords.add(node.getSimpleName().toString());
            }
            Map<String, String> fields = new HashMap<>();
            for (Tree member : node.getMembers()) {
                if (member instanceof VariableTree field) {
                    fields.put(field.getName().toString(), simpleTypeName(field.getType()));
                } else if (member instanceof ClassTree nested && nested.getKind() == Tree.Kind.RECORD) {
                    localRecords.add(nested.getSimpleName().toString());
                }
            }
            scopes.push(fields);
            try {
                return super.visitClass(node, unused);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            scopes.push(new HashMap<>());
            try {
                return super.visitMethod(node, unused);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (!scopes.isEmpty()) {
                scopes.peek().put(node.getName().toString(), simpleTypeName(node.getType()));
            }
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (isLogCall(node)) {
                node.getArguments().forEach(argument -> checkArgument(argument));
            }
            if (!s1Allowed
                    && node.getMethodSelect() instanceof MemberSelectTree select
                    && select.getIdentifier().contentEquals("printStackTrace")
                    && node.getArguments().isEmpty()) {
                report(node, "S1", "printStackTrace()를 쓰지 않는다. Logback을 거치지 않아 JSON이 아니다");
            }
            if (!s4Allowed) {
                checkMdcUse(node);
            }
            return super.visitMethodInvocation(node, unused);
        }

        /** S4: 요청 ID의 MDC 키는 global.logging이 주인이다. 다른 곳이 MDC를 통째로 지우거나 갈아 끼우면 요청 ID가 사라진다. */
        private void checkMdcUse(MethodInvocationTree node) {
            if (!(node.getMethodSelect() instanceof MemberSelectTree select)
                    || !(select.getExpression() instanceof IdentifierTree owner)
                    || !owner.getName().contentEquals("MDC")) {
                return;
            }
            String method = select.getIdentifier().toString();
            if (method.equals("clear") || method.equals("setContextMap")) {
                report(node, "S4", "MDC." + method + "()는 global.logging만 쓴다. 키별 MDC.putCloseable을 쓴다");
            } else if (!node.getArguments().isEmpty()
                    && node.getArguments().get(0) instanceof LiteralTree key
                    && "requestId".equals(key.getValue())) {
                report(node, "S4", "MDC 키 requestId는 global.logging만 쓰거나 지운다");
            }
        }

        /** S4: 헤더 이름 문자열 X-Request-Id의 주인도 global.logging이다. HTTP 헤더 이름은 대소문자를 가리지 않으므로 대소문자 무시로 본다. */
        @Override
        public Void visitLiteral(LiteralTree node, Void unused) {
            if (!s4Allowed && node.getValue() instanceof String text && text.equalsIgnoreCase(REQUEST_ID_HEADER)) {
                report(node, "S4", "헤더 이름 \"" + text + "\"은 global.logging만 쓴다");
            }
            return super.visitLiteral(node, unused);
        }

        /** S1: System.out·System.err는 Logback을 거치지 않아 한 줄 JSON이 아니고 요청 ID도 붙지 않는다. */
        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            if (!s1Allowed
                    && node.getExpression() instanceof IdentifierTree owner
                    && owner.getName().contentEquals("System")
                    && (node.getIdentifier().contentEquals("out")
                            || node.getIdentifier().contentEquals("err"))) {
                report(node, "S1", "System." + node.getIdentifier() + "을 쓰지 않는다. Logback을 거치지 않아 JSON이 아니다");
            }
            return super.visitMemberSelect(node, unused);
        }

        /** S3: Spring Security 디버그는 요청 헤더와 필터 체인 정보를 찍는다. */
        @Override
        public Void visitAnnotation(AnnotationTree node, Void unused) {
            if (node.getAnnotationType().toString().endsWith("EnableWebSecurity")) {
                for (ExpressionTree argument : node.getArguments()) {
                    if (argument instanceof AssignmentTree assignment
                            && assignment.getVariable().toString().equals("debug")
                            && assignment.getExpression().toString().equals("true")) {
                        report(node, "S3", "@EnableWebSecurity(debug = true)를 쓰지 않는다");
                    }
                }
            }
            return super.visitAnnotation(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            if (isExceptionType(node.getIdentifier().toString())) {
                node.getArguments().forEach(argument -> checkArgument(argument));
            }
            return super.visitNewClass(node, unused);
        }

        private static boolean isLogCall(MethodInvocationTree node) {
            if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
                return false;
            }
            if (!LOG_METHODS.contains(select.getIdentifier().toString())) {
                return false;
            }
            ExpressionTree receiver = select.getExpression();
            if (receiver instanceof IdentifierTree identifier) {
                String name = identifier.getName().toString().toLowerCase(Locale.ROOT);
                return name.equals("log") || name.equals("logger") || name.endsWith("log") || name.endsWith("logger");
            }
            return receiver instanceof MethodInvocationTree invocation
                    && invocation.getMethodSelect().toString().endsWith("getLogger");
        }

        private static boolean isExceptionType(String typeName) {
            String simple = typeName.substring(typeName.lastIndexOf('.') + 1);
            return simple.endsWith("Exception") || simple.endsWith("Error");
        }

        /** 로그 호출과 예외 생성자의 인자 하나를 본다. */
        private void checkArgument(ExpressionTree argument) {
            checkWholeObject(argument);
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitLiteral(LiteralTree node, Void unused) {
                    return null; // 문자열 상수는 대상이 아니다
                }

                @Override
                public Void visitIdentifier(IdentifierTree node, Void unused) {
                    checkName(node.getName().toString(), node);
                    return null;
                }

                @Override
                public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                    checkName(node.getIdentifier().toString(), node);
                    return super.visitMemberSelect(node, unused);
                }

                @Override
                public Void visitBinary(BinaryTree node, Void unused) {
                    if (node.getKind() == Tree.Kind.PLUS) {
                        for (ExpressionTree operand : List.of(node.getLeftOperand(), node.getRightOperand())) {
                            if (operand instanceof IdentifierTree identifier
                                    && isExceptionVariable(identifier.getName().toString())) {
                                report(
                                        node,
                                        "S6",
                                        "예외 '" + identifier.getName() + "'를 문자열에 이어 붙이지 않는다. 예외를 마지막 인자로 통째로 넘긴다");
                            }
                        }
                    }
                    return super.visitBinary(node, unused);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    checkExceptionUse(node);
                    ExpressionTree select = node.getMethodSelect();
                    if (select instanceof MemberSelectTree member) {
                        checkName(member.getIdentifier().toString(), node);
                        member.getExpression().accept(this, null);
                    } else if (select instanceof IdentifierTree identifier) {
                        checkName(identifier.getName().toString(), node);
                    }
                    node.getArguments().forEach(inner -> inner.accept(this, null));
                    return null;
                }
            }.scan(argument, null);
        }

        /**
         * S6: 로그 문장(message 필드)은 메시지 없는 출력기를 거치지 않는다. 예외 메시지와 응답 본문(제공자 응답에 전화번호 등이 든다)을
         * 문장에 넣지 말고, 예외는 SLF4J 호출의 마지막 인자로 통째로 넘겨 스택 트레이스로 가게 한다.
         */
        private void checkExceptionUse(MethodInvocationTree node) {
            if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
                return;
            }
            String method = select.getIdentifier().toString();
            if (RESPONSE_BODY_METHODS.contains(method)) {
                report(node, "S6", "응답 본문을 로그·예외 인자에 넣지 않는다: " + method + "()");
            } else if (MESSAGE_METHODS.contains(method) && isExceptionExpression(select.getExpression())) {
                report(node, "S6", "예외의 " + method + "()를 로그·예외 인자에 넣지 않는다. 예외를 마지막 인자로 통째로 넘긴다");
            }
        }

        /** 호출·필드 접근 체인의 시작 변수가 예외로 보이는지. */
        private boolean isExceptionExpression(ExpressionTree expression) {
            ExpressionTree current = expression;
            while (true) {
                if (current instanceof MethodInvocationTree invocation
                        && invocation.getMethodSelect() instanceof MemberSelectTree inner) {
                    current = inner.getExpression();
                } else if (current instanceof MemberSelectTree member) {
                    current = member.getExpression();
                } else {
                    break;
                }
            }
            return current instanceof IdentifierTree identifier
                    && isExceptionVariable(identifier.getName().toString());
        }

        private boolean isExceptionVariable(String name) {
            String type = declaredType(name);
            if (type != null && (type.endsWith("Exception") || type.endsWith("Error") || type.equals("Throwable"))) {
                return true;
            }
            return EXCEPTION_NAMES.contains(name);
        }

        /** S2b: record·DTO를 통째로 넘기면 toString이 모든 필드를 찍는다. 변수·필드(this.x)·new 식 자체인 인자만 본다. */
        private void checkWholeObject(ExpressionTree argument) {
            String typeName = null;
            String shown = null;
            if (argument instanceof IdentifierTree identifier) {
                shown = identifier.getName().toString();
                typeName = declaredType(shown);
            } else if (argument instanceof MemberSelectTree select
                    && select.getExpression() instanceof IdentifierTree owner
                    && owner.getName().contentEquals("this")) {
                shown = "this." + select.getIdentifier();
                typeName = declaredType(select.getIdentifier().toString());
            } else if (argument instanceof NewClassTree creation) {
                typeName = simpleTypeName(creation.getIdentifier());
                shown = "new " + typeName + "(...)";
            }
            if (typeName != null && isDtoType(typeName)) {
                report(argument, "S2b", "DTO·record '" + shown + "'(" + typeName + ")를 통째로 넘기지 않는다. 필요한 단일 값만 넘긴다");
            }
        }

        private String declaredType(String variableName) {
            for (Map<String, String> scope : scopes) {
                String type = scope.get(variableName);
                if (type != null) {
                    return type;
                }
            }
            return null;
        }

        private boolean isDtoType(String typeName) {
            return localRecords.contains(typeName)
                    || DTO_SUFFIX.matcher(typeName).find();
        }

        private static String simpleTypeName(Tree type) {
            if (type == null) {
                return "";
            }
            String text = type.toString();
            int generic = text.indexOf('<');
            if (generic >= 0) {
                text = text.substring(0, generic);
            }
            return text.substring(text.lastIndexOf('.') + 1);
        }

        private void checkName(String name, com.sun.source.tree.Tree at) {
            if (ALL_CAPS.matcher(name).matches()) {
                return; // 상수 이름
            }
            for (String word : Arrays.stream(WORD_BOUNDARY.split(name))
                    .map(part -> part.toLowerCase(Locale.ROOT))
                    .toList()) {
                if (SENSITIVE_WORDS.contains(word)) {
                    report(at, "S2", "로그·예외 인자에 민감한 이름 '" + name + "'을 넘기지 않는다");
                    return;
                }
            }
        }

        private void report(com.sun.source.tree.Tree at, String rule, String message) {
            long position = positions.getStartPosition(unit, at);
            violations.add(new Violation((int) unit.getLineMap().getLineNumber(position), rule, message));
        }
    }
}
