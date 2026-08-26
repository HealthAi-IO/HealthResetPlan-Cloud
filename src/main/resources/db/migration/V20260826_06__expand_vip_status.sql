ALTER TABLE user_vip_subscription
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'active';
