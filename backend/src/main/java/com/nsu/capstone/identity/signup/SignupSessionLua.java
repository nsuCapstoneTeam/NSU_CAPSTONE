package com.nsu.capstone.identity.signup;

/** Lua functions shared by scripts that read or change signup completion state. */
final class SignupSessionLua {

    private SignupSessionLua() {}

    /**
     * {@code signupCompletionFailure(key, role, method, termsMatch)} returns a {@link SignupRejection} status. The
     * order of checks is the API contract: session, email, phone, terms, adult.
     */
    static final String COMPLETION = """
        local function signupCompletionFailure(key, role, method, termsMatch)
            if redis.call('EXISTS', key) == 0 or redis.call('HGET', key, 'role') ~= role
                or (redis.call('HGET', key, 'signupMethod') or 'LOCAL') ~= method then return 1 end
            if redis.call('HGET', key, 'emailVerified') ~= 'true' then return 2 end
            if redis.call('HGET', key, 'phoneVerified') ~= 'true' then return 3 end
            if redis.call('HGET', key, 'requiredTermsAgreed') ~= 'true'
                or (redis.call('HGET', key, 'agreements') or '') == ''
                or (redis.call('HGET', key, 'agreedAt') or '') == ''
                or not termsMatch then return 4 end
            if redis.call('HGET', key, 'adultConfirmed') ~= 'true' then return 5 end
            return 0
        end
        """;

    /** {@code signupVerifiedField(channel)} maps a {@code VerificationChannel.keyPart()} to its hash field. */
    static final String VERIFIED_FIELD = """
        local function signupVerifiedField(channel)
            if channel == 'email' then return 'emailVerified' end
            if channel == 'phone' then return 'phoneVerified' end
            return error('unknown signup verification channel')
        end
        """;
}
