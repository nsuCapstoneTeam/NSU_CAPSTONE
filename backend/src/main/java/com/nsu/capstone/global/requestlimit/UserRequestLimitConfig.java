package com.nsu.capstone.global.requestlimit;

import com.nsu.capstone.global.logging.RequestIdExecutors;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;

/**
 * 계정별 요청 한도 설정(ADR user_request_limit/00). 공유 설정 파일을 고치지 않고 모듈 설정 파일을 직접 읽는다.
 *
 * <p>{@link BudgetLedger}는 {@code @Component}가 아니라 여기서 만든다. {@code @WebMvcTest}가 Redis 빈을 읽지 않게 하기 위해서다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({UserRequestLimitProperties.class, TomcatServerProperties.class})
@PropertySource("classpath:config/user-request-limit.properties")
public class UserRequestLimitConfig {

    /**
     * {@code @PreAuthorize} 바로 다음(순서 201)에 돈다. {@code @EnableMethodSecurity}가 등록한 자동 프록시 생성기가 이 advisor도 컨트롤러 프록시에
     * 붙인다. 인프라 역할이어야 그 생성기가 고른다. 정적 메서드라 설정 클래스를 일찍 만들지 않는다.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor userRequestLimitAdvisor(ObjectProvider<BudgetLedger> ledger) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                AnnotationMatchingPointcut.forMethodAnnotation(UserRequestLimit.class),
                new UserRequestLimitAdvice(ledger));
        advisor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 1);
        return advisor;
    }

    /**
     * 한도 확인 전용 Redis 연결과 가상 스레드 실행기를 가진 원장. 실행기 최대 크기와 연결의 명령 큐 크기는 요청 스레드 수
     * ({@code server.tomcat.threads.max})와 같다. 동시에 보내는 명령이 실행기 작업 수를 넘지 않아, 정상 Redis에서 큐가 먼저 차는 일이 없다.
     */
    @Bean
    BudgetLedger userRequestLimitLedger(
            UserRequestLimitProperties limits,
            Clock clock,
            LettuceConnectionFactory sharedFactory,
            TomcatServerProperties tomcat) {
        int requestThreads = tomcat.getThreads().getMax();
        LettuceConnectionFactory dedicated = DedicatedRedis.factory(sharedFactory, limits, requestThreads);
        // 원장이 AutoCloseable이라 Spring이 컨텍스트를 닫을 때 close()를 부르고, 거기서 실행기와 전용 연결을 닫는다
        return new BudgetLedger(
                new StringRedisTemplate(dedicated), clock, limits, executor(requestThreads), dedicated::destroy);
    }

    /** 최대 크기를 넘는 작업은 줄 세우지 않고 거절한다. 거절은 장애가 아니라 이 서버의 과부하로 다룬다. */
    private static ExecutorService executor(int maxSize) {
        return RequestIdExecutors.wrap(new ThreadPoolExecutor(
                maxSize,
                maxSize,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                Thread.ofVirtual().name("user-request-limit-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()));
    }
}
