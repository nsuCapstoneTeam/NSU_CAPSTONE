package com.nsu.capstone.global.codestyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 김동엽 파일의 Lombok 스타일 규칙(docs/adr/backend_code_style/00_lombok_usage.md)을 소스 원문에서 검사한다. */
final class LombokStyleChecker {

    record Violation(int line, String message) {}

    /** 허용 목록을 바꿀 때는 ADR, 협업 가이드 10절, 이 목록을 함께 고친다. */
    private static final Set<String> ALLOWED_LOMBOK = Set.of(
            "lombok.Getter",
            "lombok.RequiredArgsConstructor",
            "lombok.NoArgsConstructor",
            "lombok.AccessLevel",
            "lombok.extern.slf4j.Slf4j");

    private static final Pattern LOMBOK_IMPORT =
            Pattern.compile("^\\s*import\\s+(static\\s+)?(lombok\\.[\\w.]*\\*?)\\s*;");

    private static final Pattern QUALIFIED_LOMBOK = Pattern.compile("@(lombok\\.\\w+(?:\\.\\w+)*)");

    private static final Pattern NO_ARGS_CONSTRUCTOR =
            Pattern.compile("@(?:lombok\\.)?NoArgsConstructor\\b(\\s*\\([^)]*\\))?");

    private static final Pattern PROTECTED_ACCESS =
            Pattern.compile("\\baccess\\s*=\\s*(?:lombok\\.)?AccessLevel\\.PROTECTED\\b");

    private static final Pattern HAND_WRITTEN_LOGGER = Pattern.compile("\\bLoggerFactory\\.getLogger\\s*\\(");

    private static final Pattern CLASS_DECLARATION = Pattern.compile("(?<![.\\w])class\\s+(\\w+)");

    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(?:\\.\\w+)*(?:\\s*\\([^)]*\\))?");

    private static final Pattern STATIC = Pattern.compile("\\bstatic\\b");

    private static final Pattern PROTECTED = Pattern.compile("\\bprotected\\b");

    private static final Pattern FIELD_NAME = Pattern.compile("(\\w+)\\s*(?:\\[\\s*]\\s*)*$");

    private static final Pattern GETTER_HEADER = Pattern.compile(
            "\\s*(?:(?:public|protected|private|final|synchronized)\\s+)*[\\w<>\\[\\],.?\\s]+?\\s+((?:get|is)[A-Z]\\w*)\\s*\\(\\s*\\)\\s*");

    private static final Pattern SIMPLE_RETURN = Pattern.compile("\\s*return\\s+(?:this\\.)?(\\w+)\\s*;\\s*");

    private static final Pattern VALUE_ASSIGNMENT = Pattern.compile("\\s*this\\.(\\w+)\\s*=\\s*(\\w+)\\s*");

    /** 클래스 본문의 멤버 하나. 필드·추상 메서드처럼 본문이 없으면 {@code body}가 null이다. */
    private record Member(String header, String body, int offset) {}

    private LombokStyleChecker() {}

    static List<Violation> check(String source) {
        List<Violation> violations = new ArrayList<>();
        String text = blankCommentsAndLiterals(source);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher lombokImport = LOMBOK_IMPORT.matcher(lines[i]);
            if (lombokImport.find()) {
                boolean isStatic = lombokImport.group(1) != null;
                String name = lombokImport.group(2);
                if (isStatic || !ALLOWED_LOMBOK.contains(name)) {
                    violations.add(new Violation(i + 1, "허용 밖 Lombok 사용: " + name));
                }
            }
            Matcher qualified = QUALIFIED_LOMBOK.matcher(lines[i]);
            while (qualified.find()) {
                if (!ALLOWED_LOMBOK.contains(qualified.group(1))) {
                    violations.add(new Violation(i + 1, "허용 밖 Lombok 사용: " + qualified.group(1)));
                }
            }
        }
        Matcher noArgs = NO_ARGS_CONSTRUCTOR.matcher(text);
        while (noArgs.find()) {
            String arguments = noArgs.group(1) == null ? "" : noArgs.group(1);
            if (!PROTECTED_ACCESS.matcher(arguments).find()) {
                violations.add(new Violation(
                        lineOf(text, noArgs.start()), "@NoArgsConstructor에는 access = AccessLevel.PROTECTED가 있어야 한다"));
            }
        }
        List<int[]> classBodies = new ArrayList<>();
        Matcher classDeclaration = CLASS_DECLARATION.matcher(text);
        while (classDeclaration.find()) {
            int open = bodyStart(text, classDeclaration.end());
            if (open >= 0) {
                classBodies.add(new int[] {open, matchingBrace(text, open)});
                checkClassBody(text, classDeclaration.group(1), open, violations);
            }
        }
        Matcher logger = HAND_WRITTEN_LOGGER.matcher(text);
        while (logger.find()) {
            if (classBodies.stream().anyMatch(body -> body[0] < logger.start() && logger.start() < body[1])) {
                violations.add(new Violation(lineOf(text, logger.start()), "logger는 손으로 만들지 않고 @Slf4j를 쓴다"));
            }
        }
        return violations;
    }

    private static void checkClassBody(String text, String className, int open, List<Violation> violations) {
        List<Member> members = members(text, open);
        Set<String> instanceFields = instanceFields(members);
        Pattern constructorHeader = Pattern.compile("\\s*(?:(?:public|protected|private)\\s+)?(?<name>"
                + Pattern.quote(className) + ")\\s*\\((?<parameters>[^)]*)\\)\\s*");
        for (Member member : members) {
            if (member.body() == null) {
                continue;
            }
            String header = blankAnnotations(member.header());
            Matcher constructor = constructorHeader.matcher(header);
            if (constructor.matches()) {
                int line = lineOf(text, member.offset() + constructor.start("name"));
                if (!hasAnnotatedParameter(member.header(), constructor)
                        && isValueAssignments(member.body(), constructor.group("parameters"))) {
                    violations.add(new Violation(
                            line, "값만 넣는 생성자 " + className + "()는 손으로 쓰지 않고 @RequiredArgsConstructor를 쓴다"));
                }
                if (constructor.group("parameters").isBlank()
                        && member.body().isBlank()
                        && PROTECTED.matcher(header).find()) {
                    violations.add(new Violation(
                            line, "빈 protected 생성자 " + className + "()는 손으로 쓰지 않고 @NoArgsConstructor를 쓴다"));
                }
            }
            Matcher getter = GETTER_HEADER.matcher(header);
            if (getter.matches() && !STATIC.matcher(header).find()) {
                Matcher returned = SIMPLE_RETURN.matcher(member.body());
                if (returned.matches() && instanceFields.contains(returned.group(1))) {
                    violations.add(new Violation(
                            lineOf(text, member.offset() + getter.start(1)),
                            "단순 getter " + getter.group(1) + "()는 손으로 쓰지 않고 @Getter를 쓴다"));
                }
            }
        }
    }

    /** Lombok은 매개변수의 어노테이션(@Qualifier 등)을 생성자로 옮기지 않으므로, 그런 생성자는 대체할 수 없다. */
    private static boolean hasAnnotatedParameter(String rawHeader, Matcher constructor) {
        return rawHeader
                .substring(constructor.start("parameters"), constructor.end("parameters"))
                .contains("@");
    }

    /** 본문이 {@code this.x = x;} 대입문만으로 되어 있고, 대입하는 값이 같은 이름의 매개변수이면 true다. */
    private static boolean isValueAssignments(String body, String parameters) {
        Set<String> parameterNames = new HashSet<>();
        Matcher token = Pattern.compile("\\w+").matcher(parameters);
        while (token.find()) {
            parameterNames.add(token.group());
        }
        String trimmed = body.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String statement : trimmed.split(";", -1)) {
            if (statement.isBlank()) {
                continue;
            }
            Matcher assignment = VALUE_ASSIGNMENT.matcher(statement);
            if (!assignment.matches()
                    || !assignment.group(1).equals(assignment.group(2))
                    || !parameterNames.contains(assignment.group(2))) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> instanceFields(List<Member> members) {
        Set<String> names = new HashSet<>();
        for (Member member : members) {
            if (member.body() != null) {
                continue;
            }
            String header = blankAnnotations(member.header());
            int assignment = header.indexOf('=');
            String declaration = (assignment < 0 ? header : header.substring(0, assignment)).stripTrailing();
            if (declaration.contains("(") || STATIC.matcher(declaration).find()) {
                continue;
            }
            Matcher name = FIELD_NAME.matcher(declaration);
            if (name.find()) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    /** 클래스 이름 뒤에서 본문을 여는 {@code {}의 위치를 찾는다. 본문이 없으면 -1이다. */
    private static int bodyStart(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                return i;
            }
            if (c == ';') {
                return -1;
            }
        }
        return -1;
    }

    /** {@code open}에서 시작하는 클래스 본문의 최상위 멤버를 순서대로 나눈다. */
    private static List<Member> members(String text, int open) {
        List<Member> members = new ArrayList<>();
        int headerStart = open + 1;
        int parentheses = 0;
        boolean assignment = false;
        for (int i = open + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses--;
            } else if (c == '=' && parentheses == 0) {
                assignment = true;
            } else if (c == ';' && parentheses == 0) {
                members.add(new Member(text.substring(headerStart, i), null, headerStart));
                headerStart = i + 1;
                assignment = false;
            } else if (c == '{') {
                int close = matchingBrace(text, i);
                if (!assignment && parentheses == 0) {
                    members.add(new Member(text.substring(headerStart, i), text.substring(i + 1, close), headerStart));
                    headerStart = close + 1;
                }
                i = close;
            } else if (c == '}') {
                break;
            }
        }
        return members;
    }

    private static int matchingBrace(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return text.length() - 1;
    }

    /** 어노테이션을 같은 길이의 공백으로 바꿔, 헤더 안의 위치가 그대로 남는다. */
    private static String blankAnnotations(String header) {
        return ANNOTATION
                .matcher(header)
                .replaceAll(match -> " ".repeat(match.group().length()));
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 주석, 문자열, 텍스트 블록, 문자 리터럴을 공백으로 바꾼다. 줄바꿈은 남겨 줄 번호가 그대로다. */
    private static String blankCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int length = source.length();
        int i = 0;
        while (i < length) {
            int stop = literalEnd(source, i);
            if (stop < 0) {
                out.append(source.charAt(i));
                i++;
                continue;
            }
            for (int j = i; j < stop; j++) {
                out.append(source.charAt(j) == '\n' ? '\n' : ' ');
            }
            i = stop;
        }
        return out.toString();
    }

    /** {@code start}에서 주석이나 리터럴이 시작하면 그 끝 위치를, 아니면 -1을 돌려준다. */
    private static int literalEnd(String source, int start) {
        int length = source.length();
        if (source.startsWith("//", start)) {
            int end = source.indexOf('\n', start);
            return end < 0 ? length : end;
        }
        if (source.startsWith("/*", start)) {
            int end = source.indexOf("*/", start + 2);
            return end < 0 ? length : end + 2;
        }
        if (source.startsWith("\"\"\"", start)) {
            int end = source.indexOf("\"\"\"", start + 3);
            return end < 0 ? length : end + 3;
        }
        char quote = source.charAt(start);
        if (quote != '"' && quote != '\'') {
            return -1;
        }
        int j = start + 1;
        while (j < length && source.charAt(j) != quote && source.charAt(j) != '\n') {
            j += source.charAt(j) == '\\' ? 2 : 1;
        }
        return Math.min(j + 1, length);
    }
}
