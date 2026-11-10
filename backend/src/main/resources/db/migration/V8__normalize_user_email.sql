DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Email normalization collision: resolve duplicate accounts before migrating';
    END IF;
END $$;
UPDATE users SET email = lower(btrim(email));
ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)));
