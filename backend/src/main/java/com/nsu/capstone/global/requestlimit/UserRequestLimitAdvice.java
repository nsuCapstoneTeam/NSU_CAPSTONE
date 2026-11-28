package com.nsu.capstone.global.requestlimit;

import java.lang.reflect.Method;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * {@link UserRequestLimit}이 붙은 핸들러 앞에서 로그인한 계정의 한도를 1회 쓴다(ADR user_request_limit/00 Decision 3). 한도를 다 썼으면 본문을
 * 돌리지 않고 {@code BusinessException}으로 끝난다.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class UserRequestLimitAdvice implements MethodInterceptor {

    /** 빈을 늦게 받는다. advisor는 다른 빈보다 먼저 만들어지는데, 원장은 Redis 연결 설정에 기대기 때문이다. */
    private final ObjectProvider<BudgetLedger> ledger;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        ledger.getObject().spend(accountId(), budgetOf(invocation));
        return invocation.proceed();
    }

    private static RequestBudget budgetOf(MethodInvocation invocation) {
        Method method = AopUtils.getMostSpecificMethod(
                invocation.getMethod(), AopProxyUtils.ultimateTargetClass(invocation.getThis()));
        UserRequestLimit limit = AnnotatedElementUtils.findMergedAnnotation(method, UserRequestLimit.class);
        if (limit == null) {
            throw new IllegalStateException("요청 한도 애너테이션을 찾을 수 없다: " + method);
        }
        return limit.value();
    }

    /** JWT 없이 도착하면 한도가 사라지므로 조용히 건너뛰지 않고 500으로 끝낸다. */
    private static UUID accountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("요청 한도 핸들러에 JWT 없이 도착했다. 공개 경로에는 한도를 달 수 없다");
        }
        return UUID.fromString(jwt.getToken().getSubject());
    }
}
