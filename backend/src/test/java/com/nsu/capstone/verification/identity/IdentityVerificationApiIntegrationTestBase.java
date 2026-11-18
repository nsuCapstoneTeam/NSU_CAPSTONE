package com.nsu.capstone.verification.identity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * 컨텍스트의 제공자 빈을 시험용 Mock으로 바꿔 PortOne 응답을 코드로 정하고 세 경로를 MockMvc로 부른다. 실제 PortOne은 부르지 않는다(계약 07 §10).
 */
abstract class IdentityVerificationApiIntegrationTestBase extends IdentityVerificationHttpTestBase {

    @TestBean(methodName = "scriptedProvider")
    IdentityVerificationProvider provider;

    private int lookupsBeforeTest;

    static IdentityVerificationProvider scriptedProvider() {
        return MockIdentityVerificationProvider.notCompletedUnlessScripted();
    }

    @BeforeEach
    void rememberLookups() {
        lookupsBeforeTest = mock().totalLookups();
    }

    MockIdentityVerificationProvider mock() {
        return (MockIdentityVerificationProvider) provider;
    }

    /** 같은 컨텍스트를 쓰는 다른 시험이 부른 조회는 빼고, 이 시험에서 제공자를 부른 횟수. */
    int lookupsInThisTest() {
        return mock().totalLookups() - lookupsBeforeTest;
    }
}
