ALTER TABLE user_key_meta
    DROP INDEX uk_user,
    ADD UNIQUE KEY uk_user_public_finger (user_id, public_finger);
