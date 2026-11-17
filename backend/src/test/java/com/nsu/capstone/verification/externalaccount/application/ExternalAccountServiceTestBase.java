package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

/** 서비스 통합 테스트의 공통 설정: YouTube 연결을 켜고, 외부 호출은 가짜 게이트웨이로 대신한다. */
@TestPropertySource(
        properties = {
            "external-account.youtube.enabled=true",
            "external-account.youtube.client-id=1234567890-link.apps.googleusercontent.com",
            "external-account.youtube.client-secret=test-only-secret",
            "external-account.youtube.api-key=test-only-api-key",
            "external-account.youtube.redirect-uri=http://localhost:5173/api/v1/external-accounts/oauth/callback/youtube",
            "external-account.frontend-base-uri=http://localhost:5173"
        })
public abstract class ExternalAccountServiceTestBase extends ExternalAccountIntegrationTestBase {

    @Autowired
    protected ExternalAccountService service;

    @MockitoBean
    protected YoutubeGateway youtubeGateway;

    @Autowired
    protected ExternalAccountRepository repository;

    /** 서비스를 거치지 않고 이미 연결된 상태를 만든다. */
    protected ExternalAccount existingAccount(User artist, ExternalAccountProvider provider, String providerAccountId) {
        ExternalAccount account =
                new ExternalAccount(uuidV7Generator.generate(), artist.getId(), provider, providerAccountId, NOW, NOW);
        inTransaction(() -> repository.insert(repository.lockArtist(artist.getId()), account));
        return account;
    }

    protected int authorizationKeyCount() {
        return redis.keys("extacct:authz:*").size();
    }

    /** 시작 결과의 인가 주소에서 {@code state}를 꺼낸다. */
    protected static String stateOf(AuthorizationStart start) {
        return UriComponentsBuilder.fromUriString(start.authorizationUrl())
                .build()
                .getQueryParams()
                .getFirst("state");
    }

    protected List<String> auditRows(User artist) {
        return jdbcTemplate.query(
                "SELECT event_type || '/' || actor || '/' || COALESCE(reason, '-') FROM external_account_audit_events"
                        + " WHERE artist_id = ? ORDER BY occurred_at, event_type",
                (rs, rowNum) -> rs.getString(1),
                artist.getId());
    }
}
