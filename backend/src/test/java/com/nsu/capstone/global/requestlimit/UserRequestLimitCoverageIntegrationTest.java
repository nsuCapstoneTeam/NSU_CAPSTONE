package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 로그인해야 부를 수 있는 쓰기는 모두 {@code @UserRequestLimit}을 받는다(ADR user_request_limit/00 "원칙을 지키는 테스트" 1번).
 *
 * <p>익명으로 부를 수 있는지는 목록으로 관리하지 않고 {@code SecurityConfig}로 만든 {@link WebInvocationPrivilegeEvaluator}에 직접 묻는다.
 * 그래서 공개 경로를 바꿔도 이 테스트를 고치지 않는다.
 */
class UserRequestLimitCoverageIntegrationTest extends EventIntegrationTestBase {

    private static final String APP_PACKAGE = "com.nsu.capstone.";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> EVERY_METHOD = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "anonymous-key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    /** 아직 이 장치로 옮기지 못한 매핑. 항목마다 {@code 메서드 경로}, 근거 ADR, 옮길 후속 이슈 키를 적는다. 남겨 두지 않는다. */
    record Exemption(String mapping, String adr, String followUpIssue) {}

    private static final List<Exemption> EXEMPTIONS = List.of();

    /** 메서드 지정이 없는 앱 매핑을 흉내 내는 핸들러다. 실제 컨트롤러 목록에 섞이지 않게 이 테스트 안에서만 쓴다. */
    static class SyntheticController {

        public void unannotated() {}

        @UserRequestLimit(RequestBudget.PROFILE)
        public void annotated() {}
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WebInvocationPrivilegeEvaluator evaluator;

    @Test
    void 로그인해야_부를_수_있는_쓰기는_모두_한도가_있고_공개_경로에는_한도가_없다() {
        assertThat(violations(appHandlers(), EXEMPTIONS)).isEmpty();
    }

    @Test
    void 예외_목록의_항목은_모두_근거와_후속_이슈를_적고_지금_있는_매핑이다() {
        assertThat(EXEMPTIONS).allSatisfy(exemption -> {
            assertThat(exemption.adr()).isNotBlank();
            assertThat(exemption.followUpIssue()).isNotBlank();
        });
        assertThat(staleExemptions(appHandlers(), EXEMPTIONS)).isEmpty();
    }

    @Test
    void 행사_등록은_검사_대상에_들어_있다() {
        assertThat(targets(appHandlers())).contains("POST /api/v1/events");
    }

    @Test
    void 앱_매핑에서_빠지는_것은_프레임워크_핸들러뿐이고_error_핸들러가_그_안에_있다() {
        List<HandlerMethod> excluded = allHandlers().values().stream()
                .filter(handler -> !isAppHandler(handler))
                .toList();

        assertThat(excluded)
                .allSatisfy(
                        handler -> assertThat(handler.getBeanType().getName()).startsWith("org."));
        assertThat(excluded)
                .anySatisfy(handler ->
                        assertThat(handler.getBeanType().getSimpleName()).isEqualTo("BasicErrorController"));
    }

    @Test
    void 메서드를_지정하지_않은_앱_매핑은_모든_메서드로_보고_쓰기로_센다() throws Exception {
        RequestMappingInfo info =
                RequestMappingInfo.paths("/api/v1/me/synthetic").build();

        assertThat(violations(single(info, "unannotated"), List.of()))
                .singleElement()
                .asString()
                .contains("/api/v1/me/synthetic");
        assertThat(violations(single(info, "annotated"), List.of())).isEmpty();
    }

    @Test
    void 공개_경로에_붙은_한도는_위반이다() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/v1/auth/login")
                .methods(RequestMethod.POST)
                .build();

        assertThat(violations(single(info, "annotated"), List.of()))
                .singleElement()
                .asString()
                .contains("/api/v1/auth/login");
        // 공개 경로의 쓰기에 한도가 없는 것은 위반이 아니다(IP 단위 한도가 따로 있다)
        assertThat(violations(single(info, "unannotated"), List.of())).isEmpty();
    }

    @Test
    void 로그인한_사용자의_GET은_한도가_없어도_위반이_아니고_붙어_있어도_위반이_아니다() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/v1/me/synthetic")
                .methods(RequestMethod.GET)
                .build();

        assertThat(violations(single(info, "unannotated"), List.of())).isEmpty();
        assertThat(violations(single(info, "annotated"), List.of())).isEmpty();
    }

    @Test
    void 예외_목록에_있는_매핑은_위반에서_빠지고_없는_매핑을_가리키는_항목은_잡힌다() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/v1/me/synthetic")
                .methods(RequestMethod.POST)
                .build();
        Map<RequestMappingInfo, HandlerMethod> handlers = single(info, "unannotated");
        List<Exemption> exemptions = List.of(
                new Exemption("POST /api/v1/me/synthetic", "user_request_limit/00", "NSU-1"),
                new Exemption("POST /api/v1/me/missing", "user_request_limit/00", "NSU-2"));

        assertThat(violations(handlers, exemptions)).isEmpty();
        assertThat(staleExemptions(handlers, exemptions)).containsExactly("POST /api/v1/me/missing");
    }

    private static Map<RequestMappingInfo, HandlerMethod> single(RequestMappingInfo info, String methodName)
            throws NoSuchMethodException {
        Method method = SyntheticController.class.getMethod(methodName);
        return Map.of(info, new HandlerMethod(new SyntheticController(), method));
    }

    private Map<RequestMappingInfo, HandlerMethod> allHandlers() {
        Map<RequestMappingInfo, HandlerMethod> all = new LinkedHashMap<>();
        context.getBeansOfType(RequestMappingHandlerMapping.class)
                .values()
                .forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        return all;
    }

    private Map<RequestMappingInfo, HandlerMethod> appHandlers() {
        Map<RequestMappingInfo, HandlerMethod> app = new LinkedHashMap<>();
        allHandlers().forEach((info, handler) -> {
            if (isAppHandler(handler)) {
                app.put(info, handler);
            }
        });
        return app;
    }

    private static boolean isAppHandler(HandlerMethod handler) {
        return handler.getBeanType().getName().startsWith(APP_PACKAGE);
    }

    /** 메서드를 지정하지 않은 매핑은 모든 메서드로 본다. */
    private static List<String> methodsOf(RequestMappingInfo info) {
        Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
        if (declared.isEmpty()) {
            return EVERY_METHOD;
        }
        return declared.stream().map(Enum::name).sorted().toList();
    }

    /** {@code {변수}}와 {@code *}를 글자로 바꿔 실제로 부를 수 있는 주소로 만든다. */
    private static String sampleUriOf(String pattern) {
        return pattern.replaceAll("\\{[^}]*}", "x").replace("**", "x").replace("*", "x");
    }

    private boolean anonymousCanCall(String method, String pattern) {
        return evaluator.isAllowed("", sampleUriOf(pattern), method, ANONYMOUS);
    }

    /** 익명으로 부를 수 없는 쓰기를 가진 매핑이다. 한도가 있어야 한다. */
    private boolean needsLimit(RequestMappingInfo info, String pattern) {
        return methodsOf(info).stream()
                .filter(WRITE_METHODS::contains)
                .anyMatch(method -> !anonymousCanCall(method, pattern));
    }

    private Set<String> targets(Map<RequestMappingInfo, HandlerMethod> handlers) {
        Set<String> targets = new LinkedHashSet<>();
        handlers.keySet()
                .forEach(info -> info.getPatternValues()
                        .forEach(pattern -> methodsOf(info).stream()
                                .filter(WRITE_METHODS::contains)
                                .filter(method -> !anonymousCanCall(method, pattern))
                                .forEach(method -> targets.add(method + " " + pattern))));
        return targets;
    }

    private List<String> violations(Map<RequestMappingInfo, HandlerMethod> handlers, List<Exemption> exemptions) {
        Set<String> exempt = new HashSet<>();
        exemptions.forEach(exemption -> exempt.add(exemption.mapping()));
        List<String> violations = new ArrayList<>();
        handlers.forEach((info, handler) -> {
            boolean annotated = handler.hasMethodAnnotation(UserRequestLimit.class);
            for (String pattern : info.getPatternValues()) {
                String where = methodsOf(info) + " " + pattern + " (" + handler.getShortLogMessage() + ")";
                if (annotated) {
                    if (methodsOf(info).stream().anyMatch(method -> anonymousCanCall(method, pattern))) {
                        violations.add("공개 경로에 한도가 붙었다: " + where);
                    }
                } else if (needsLimit(info, pattern) && !exemptedEvery(info, pattern, exempt)) {
                    violations.add("한도가 없다: " + where);
                }
            }
        });
        return violations;
    }

    /** 익명 불가 쓰기 메서드가 모두 예외 목록에 있어야 빠진다. */
    private boolean exemptedEvery(RequestMappingInfo info, String pattern, Set<String> exempt) {
        return methodsOf(info).stream()
                .filter(WRITE_METHODS::contains)
                .filter(method -> !anonymousCanCall(method, pattern))
                .allMatch(method -> exempt.contains(method + " " + pattern));
    }

    private static List<String> staleExemptions(
            Map<RequestMappingInfo, HandlerMethod> handlers, List<Exemption> exemptions) {
        Set<String> existing = new HashSet<>();
        handlers.keySet()
                .forEach(info -> info.getPatternValues()
                        .forEach(pattern -> methodsOf(info).forEach(method -> existing.add(method + " " + pattern))));
        return exemptions.stream()
                .map(Exemption::mapping)
                .filter(mapping -> !existing.contains(mapping))
                .toList();
    }
}
