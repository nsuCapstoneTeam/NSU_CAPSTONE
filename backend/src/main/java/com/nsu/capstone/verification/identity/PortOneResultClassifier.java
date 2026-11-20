package com.nsu.capstone.verification.identity;

/** PortOne 재조회 결과를 계약 08 §4.2 표대로 나눈다. 본문은 200일 때만 넘어온다. */
record PortOneResultClassifier(ChannelPolicy channelPolicy) {

    ProviderOutcome classify(int httpStatus, PortOneResponse body) {
        return switch (httpStatus) {
            case 200 -> classifyBody(body);
            case 404 -> new ProviderOutcome.NotCompleted();
            case 401, 403 -> new ProviderOutcome.Unavailable(UnavailableCause.CREDENTIAL_REJECTED);
            default -> new ProviderOutcome.Unavailable(UnavailableCause.UNEXPECTED_STATUS);
        };
    }

    private ProviderOutcome classifyBody(PortOneResponse body) {
        if (body == null || body.status() == null) {
            return unreadable();
        }
        return switch (body.status()) {
            case "VERIFIED" ->
                channelPolicy
                        .reject(body.channel())
                        .<ProviderOutcome>map(ProviderOutcome.Rejected::new)
                        .orElseGet(() -> new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
            case "READY" -> new ProviderOutcome.NotCompleted();
            case "FAILED" -> new ProviderOutcome.Failed();
            default -> unreadable();
        };
    }

    private static ProviderOutcome unreadable() {
        return new ProviderOutcome.Unavailable(UnavailableCause.UNREADABLE_RESPONSE);
    }
}
