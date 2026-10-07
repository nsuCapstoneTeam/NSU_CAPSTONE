package com.nsu.capstone.identity.oauth;

public final class OAuthRedisKeys {

    private OAuthRedisKeys() {
    }

    public static String authorization(String stateDigest) {
        return "auth:oauth:authorization:{" + stateDigest + "}";
    }

    public static String signupSession(String oauthSignupSessionId) {
        return "auth:oauth:signup:{" + oauthSignupSessionId + "}";
    }

    public static String result(String resultCodeDigest) {
        return "auth:oauth:result:{" + resultCodeDigest + "}";
    }
}
