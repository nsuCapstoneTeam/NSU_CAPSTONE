package com.nsu.capstone.global.codestyle;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** 김동엽 파일의 Lombok 스타일 규칙(docs/adr/backend_code_style/00_lombok_usage.md)을 JDK 파서의 구문 트리에서 검사한다. */
final class LombokStyleChecker {

    record Violation(int line, String message) {}

    /** 허용 목록을 바꿀 때는 ADR, 협업 가이드 10절, 이 목록을 함께 고친다. */
    private static final Set<String> ALLOWED_LOMBOK = Set.of(
            "lombok.Getter",
            "lombok.RequiredArgsConstructor",
            "lombok.NoArgsConstructor",
            "lombok.AccessLevel",
            "lombok.extern.slf4j.Slf4j");

    private static final Set<String> PROTECTED_ACCESS = Set.of("AccessLevel.PROTECTED", "lombok.AccessLevel.PROTECTED");

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private LombokStyleChecker() {}

    static List<Violation> check(String source) {
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
                new Scanner(unit, Trees.instance(task).getSourcePositions(), source, violations).scan(unit, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Violation> parseFailures = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> new Violation(
                        (int) diagnostic.getLineNumber(), "파싱 실패: " + diagnostic.getMessage(Locale.ENGLISH)))
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
        private final String source;
        private final List<Violation> violations;
        private final Set<String> imports;
        private final Deque<ClassTree> enclosingTypes = new ArrayDeque<>();

        Scanner(CompilationUnitTree unit, SourcePositions positions, String source, List<Violation> violations) {
            this.unit = unit;
            this.positions = positions;
            this.source = source;
            this.violations = violations;
            this.imports = Set.copyOf(unit.getImports().stream()
                    .filter(importTree -> !importTree.isStatic())
                    .map(importTree -> importTree.getQualifiedIdentifier().toString())
                    .toList());
        }

        @Override
        public Void visitImport(ImportTree node, Void unused) {
            String name = node.getQualifiedIdentifier().toString();
            if (name.startsWith("lombok.") && (node.isStatic() || !ALLOWED_LOMBOK.contains(name))) {
                report(startLine(node), "허용 밖 Lombok 사용: " + name);
            }
            return super.visitImport(node, unused);
        }

        @Override
        public Void visitAnnotation(AnnotationTree node, Void unused) {
            String type = node.getAnnotationType().toString();
            if (type.startsWith("lombok.") && !ALLOWED_LOMBOK.contains(type)) {
                report(startLine(node), "허용 밖 Lombok 사용: " + type);
            }
            if (isLombokNoArgsConstructor(type) && !hasProtectedAccess(node)) {
                report(startLine(node), "@NoArgsConstructor에는 access = AccessLevel.PROTECTED가 있어야 한다");
            }
            return super.visitAnnotation(node, unused);
        }

        /** 정규화된 이름이거나, 이름을 Lombok에서 import했을 때만 Lombok의 {@code @NoArgsConstructor}다. */
        private boolean isLombokNoArgsConstructor(String type) {
            return type.equals("lombok.NoArgsConstructor")
                    || (type.equals("NoArgsConstructor")
                            && (imports.contains("lombok.NoArgsConstructor") || imports.contains("lombok.*")));
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            enclosingTypes.push(node);
            try {
                if (node.getKind() == Tree.Kind.CLASS && !node.getSimpleName().isEmpty()) {
                    checkMembers(node);
                }
                return super.visitClass(node, unused);
            } finally {
                enclosingTypes.pop();
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (isLoggerFactoryGetLogger(node) && isInsideNamedClass()) {
                report(startLine(node), "logger는 손으로 만들지 않고 @Slf4j를 쓴다");
            }
            return super.visitMethodInvocation(node, unused);
        }

        /** 호출을 감싸는 가장 안쪽의 이름 있는 타입 선언이 {@code class}인지 본다. 익명 클래스는 건너뛴다. */
        private boolean isInsideNamedClass() {
            return enclosingTypes.stream()
                    .filter(type -> !type.getSimpleName().isEmpty())
                    .findFirst()
                    .filter(type -> type.getKind() == Tree.Kind.CLASS)
                    .isPresent();
        }

        private void checkMembers(ClassTree node) {
            String className = node.getSimpleName().toString();
            Map<String, VariableTree> instanceFields = new HashMap<>();
            List<VariableTree> requiredFields = new ArrayList<>();
            boolean hasNonNullField = false;
            for (Tree member : node.getMembers()) {
                if (member instanceof VariableTree field
                        && !field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                    instanceFields.put(field.getName().toString(), field);
                    if (field.getModifiers().getFlags().contains(Modifier.FINAL) && field.getInitializer() == null) {
                        requiredFields.add(field);
                    }
                    // Lombok은 이런 필드를 매개변수와 null 검사로 바꾸므로, 손으로 쓴 생성자와 같지 않다.
                    if (field.getInitializer() == null
                            && field.getModifiers().getAnnotations().stream()
                                    .anyMatch(annotation -> lastName(annotation.getAnnotationType())
                                            .equals("NonNull"))) {
                        hasNonNullField = true;
                    }
                }
            }
            for (Tree member : node.getMembers()) {
                if (!(member instanceof MethodTree method) || method.getBody() == null) {
                    continue;
                }
                if (method.getName().contentEquals("<init>")) {
                    checkConstructor(className, method, requiredFields, hasNonNullField);
                } else {
                    checkGetter(method, instanceFields);
                }
            }
        }

        private void checkConstructor(
                String className, MethodTree constructor, List<VariableTree> requiredFields, boolean hasNonNullField) {
            int line = nameLine(constructor, className);
            // Lombok은 매개변수의 어노테이션(@Qualifier 등)을 생성자로 옮기지 않으므로, 그런 생성자는 대체할 수 없다.
            boolean annotatedParameter = constructor.getParameters().stream()
                    .anyMatch(parameter ->
                            !parameter.getModifiers().getAnnotations().isEmpty());
            if (!annotatedParameter
                    && !hasNonNullField
                    && constructor.getModifiers().getAnnotations().isEmpty()
                    && constructor.getThrows().isEmpty()
                    && !hasVarargs(constructor)
                    && takesRequiredFields(constructor, requiredFields)
                    && isValueAssignments(constructor)) {
                report(line, "값만 넣는 생성자 " + className + "()는 손으로 쓰지 않고 @RequiredArgsConstructor를 쓴다");
            }
            if (constructor.getParameters().isEmpty()
                    && constructor.getBody().getStatements().isEmpty()
                    && constructor.getModifiers().getAnnotations().isEmpty()
                    && constructor.getThrows().isEmpty()
                    && constructor.getModifiers().getFlags().contains(Modifier.PROTECTED)) {
                report(line, "빈 protected 생성자 " + className + "()는 손으로 쓰지 않고 @NoArgsConstructor를 쓴다");
            }
        }

        /** 구문 트리에는 가변 인자 표시가 없어(타입이 {@code String[]}로 보인다) 매개변수의 소스 범위에서 주석을 빼고 {@code ...}를 찾는다. */
        private boolean hasVarargs(MethodTree constructor) {
            return constructor.getParameters().stream().anyMatch(parameter -> {
                int start = (int) positions.getStartPosition(unit, parameter);
                int end = (int) positions.getEndPosition(unit, parameter);
                return COMMENT.matcher(source.substring(start, end))
                        .replaceAll(" ")
                        .contains("...");
            });
        }

        /** {@code @RequiredArgsConstructor}는 초기화하지 않은 {@code final} 인스턴스 필드만 받는다. */
        private static boolean takesRequiredFields(MethodTree constructor, List<VariableTree> requiredFields) {
            List<? extends VariableTree> parameters = constructor.getParameters();
            if (parameters.size() != requiredFields.size()) {
                return false;
            }
            for (int i = 0; i < parameters.size(); i++) {
                VariableTree parameter = parameters.get(i);
                VariableTree field = requiredFields.get(i);
                if (!parameter.getName().contentEquals(field.getName())
                        || !parameter
                                .getType()
                                .toString()
                                .equals(field.getType().toString())) {
                    return false;
                }
            }
            return true;
        }

        /** 본문이 {@code this.x = x;} 대입문만으로 되어 있고, 대입하는 값이 같은 이름의 매개변수이면 true다. */
        private static boolean isValueAssignments(MethodTree constructor) {
            Set<String> parameterNames = new HashSet<>();
            constructor
                    .getParameters()
                    .forEach(parameter -> parameterNames.add(parameter.getName().toString()));
            List<? extends StatementTree> statements = constructor.getBody().getStatements();
            if (statements.isEmpty()) {
                return false;
            }
            for (StatementTree statement : statements) {
                if (!(statement instanceof ExpressionStatementTree expressionStatement)
                        || !(expressionStatement.getExpression() instanceof AssignmentTree assignment)
                        || !(assignment.getVariable() instanceof MemberSelectTree target)
                        || !(target.getExpression() instanceof IdentifierTree owner)
                        || !owner.getName().contentEquals("this")
                        || !(assignment.getExpression() instanceof IdentifierTree value)
                        || !target.getIdentifier().contentEquals(value.getName())
                        || !parameterNames.contains(value.getName().toString())) {
                    return false;
                }
            }
            return true;
        }

        private void checkGetter(MethodTree method, Map<String, VariableTree> instanceFields) {
            if (method.getReturnType() == null
                    || !method.getParameters().isEmpty()
                    || method.getModifiers().getFlags().contains(Modifier.STATIC)
                    || method.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED)
                    || method.getModifiers().getFlags().contains(Modifier.FINAL)
                    || !method.getThrows().isEmpty()
                    || method.getModifiers().getAnnotations().stream()
                            .anyMatch(annotation ->
                                    !lastName(annotation.getAnnotationType()).equals("Override"))) {
                return;
            }
            List<? extends StatementTree> statements = method.getBody().getStatements();
            if (statements.size() == 1
                    && statements.get(0) instanceof ReturnTree returned
                    && returnedField(returned.getExpression()) instanceof String fieldName
                    && instanceFields.get(fieldName) instanceof VariableTree field
                    && method.getName().contentEquals(lombokGetterName(field))
                    && method.getReturnType().toString().equals(field.getType().toString())) {
                report(
                        nameLine(method, method.getName().toString()),
                        "단순 getter " + method.getName() + "()는 손으로 쓰지 않고 @Getter를 쓴다");
            }
        }

        /** Lombok이 필드에 만드는 getter 이름. 기본형 {@code boolean}은 {@code is}, 그 밖은 {@code get}으로 시작한다. */
        private static String lombokGetterName(VariableTree field) {
            String name = field.getName().toString();
            String capitalized = capitalized(name);
            boolean primitiveBoolean = field.getType() instanceof PrimitiveTypeTree type
                    && type.getPrimitiveTypeKind() == TypeKind.BOOLEAN;
            if (primitiveBoolean
                    && name.length() > 2
                    && name.startsWith("is")
                    && !Character.isLowerCase(name.charAt(2))) {
                return name;
            }
            return (primitiveBoolean ? "is" : "get") + capitalized;
        }

        /** Lombok처럼 소문자인 첫 글자만 바꾼다. 3글자 이상이고 둘째 글자가 대문자이면 대문자로, 아니면 title case로 바꾼다. */
        private static String capitalized(String name) {
            char first = name.charAt(0);
            if (!Character.isLowerCase(first)) {
                return name;
            }
            boolean upperCase = name.length() > 2
                    && (Character.isTitleCase(name.charAt(1)) || Character.isUpperCase(name.charAt(1)));
            return (upperCase ? Character.toUpperCase(first) : Character.toTitleCase(first)) + name.substring(1);
        }

        /** {@code x} 또는 {@code this.x}이면 필드 이름을, 아니면 null을 돌려준다. */
        private static String returnedField(ExpressionTree expression) {
            if (expression instanceof IdentifierTree identifier) {
                return identifier.getName().toString();
            }
            if (expression instanceof MemberSelectTree select
                    && select.getExpression() instanceof IdentifierTree owner
                    && owner.getName().contentEquals("this")) {
                return select.getIdentifier().toString();
            }
            return null;
        }

        private static boolean hasProtectedAccess(AnnotationTree annotation) {
            for (ExpressionTree argument : annotation.getArguments()) {
                if (argument instanceof AssignmentTree assignment
                        && assignment.getVariable().toString().equals("access")
                        && PROTECTED_ACCESS.contains(assignment.getExpression().toString())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isLoggerFactoryGetLogger(MethodInvocationTree invocation) {
            return invocation.getMethodSelect() instanceof MemberSelectTree select
                    && select.getIdentifier().contentEquals("getLogger")
                    && lastName(select.getExpression()).equals("LoggerFactory");
        }

        private static String lastName(Tree name) {
            if (name instanceof IdentifierTree identifier) {
                return identifier.getName().toString();
            }
            if (name instanceof MemberSelectTree select) {
                return select.getIdentifier().toString();
            }
            return "";
        }

        /** 메서드의 시작 위치는 어노테이션 줄을 가리키므로, 반환 타입이나 한정자 뒤에서 이름을 찾아 그 줄을 돌려준다. */
        private int nameLine(MethodTree method, String name) {
            long from = method.getReturnType() != null
                    ? positions.getEndPosition(unit, method.getReturnType())
                    : positions.getEndPosition(unit, method.getModifiers());
            if (from < 0) {
                from = positions.getStartPosition(unit, method);
            }
            int index = source.indexOf(name, (int) from);
            return line(index < 0 ? from : index);
        }

        private int startLine(Tree node) {
            return line(positions.getStartPosition(unit, node));
        }

        private int line(long position) {
            return (int) unit.getLineMap().getLineNumber(position);
        }

        private void report(int line, String message) {
            violations.add(new Violation(line, message));
        }
    }
}
