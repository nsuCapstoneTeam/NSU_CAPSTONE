-- EVENT_PARTNER 프로필 (05 EVENT_PARTNER 프로필 API §5, ADR event_partner_profile/00).
-- 가입할 때 만들지 않고 처음 PUT할 때 만든다. user_id가 PK이면서 users.id를 참조한다(1:1).
CREATE TABLE event_partner_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    name VARCHAR(40) NOT NULL,
    organization VARCHAR(60),
    bio VARCHAR(700),
    event_types VARCHAR(32)[] NOT NULL,
    region_codes VARCHAR(5)[] NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
