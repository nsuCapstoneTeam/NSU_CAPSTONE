package com.nsu.capstone.verification.externalaccount.config;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 앱이 뜰 때 설정을 검사한다(ADR 14번, 원칙 테스트 15). YouTube 연결을 켰을 때만 검사한다.
 *
 * <p>Google은 토큰 하나를 폐기하면 그 프로젝트에 준 모든 권한을 없앤다. YouTube 연결이 로그인과 같은 Google Cloud 프로젝트에
 * 있으면 연결 때 하는 토큰 폐기가 로그인 동의까지 지운다. Google 클라이언트 ID의 앞부분 숫자가 프로젝트 번호이므로,
 * 두 클라이언트 ID의 프로젝트 번호가 같으면 앱이 뜨지 않게 한다.
 */
@Component
public class ExternalAccountSettingsValidator implements InitializingBean {

    private static final Pattern PROJECT_NUMBER = Pattern.compile("^(\\d+)-");

    private final ExternalAccountProperties properties;
    private final OAuthProperties loginProperties;

    public ExternalAccountSettingsValidator(ExternalAccountProperties properties, OAuthProperties loginProperties) {
        this.properties = properties;
        this.loginProperties = loginProperties;
    }

    @Override
    public void afterPropertiesSet() {
        ExternalAccountProperties.Youtube youtube = properties.youtube();
        if (!youtube.enabled()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        require(missing, youtube.clientId(), "external-account.youtube.client-id");
        require(missing, youtube.clientSecret(), "external-account.youtube.client-secret");
        require(missing, youtube.apiKey(), "external-account.youtube.api-key");
        require(missing, youtube.redirectUri(), "external-account.youtube.redirect-uri");
        require(missing, properties.frontendBaseUri(), "external-account.frontend-base-uri");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("YouTube 연결을 켜려면 다음 설정이 필요하다: " + String.join(", ", missing));
        }
        String projectNumber = projectNumberOf(youtube.clientId());
        if (projectNumber == null) {
            throw new IllegalStateException(
                    "external-account.youtube.client-id가 Google 클라이언트 ID 형식(숫자-...)이 아니어서 프로젝트 번호를 알 수 없다");
        }
        String loginClientId = loginProperties.getGoogle().getClientId();
        if (projectNumber.equals(projectNumberOf(loginClientId))) {
            throw new IllegalStateException("YouTube 연결 클라이언트와 로그인 Google 클라이언트가 같은 Google Cloud 프로젝트(" + projectNumber
                    + ")에 있다. 연결 때 하는 토큰 폐기가 로그인 동의까지 지우므로 프로젝트를 나눈다");
        }
    }

    private static void require(List<String> missing, String value, String property) {
        if (!StringUtils.hasText(value)) {
            missing.add(property);
        }
    }

    /** Google 클라이언트 ID({@code 1234567890-abc.apps.googleusercontent.com})의 앞부분 숫자다. 형식이 다르면 {@code null}이다. */
    private static String projectNumberOf(String clientId) {
        if (clientId == null) {
            return null;
        }
        Matcher matcher = PROJECT_NUMBER.matcher(clientId.strip());
        return matcher.find() ? matcher.group(1) : null;
    }
}
