CREATE TABLE IF NOT EXISTS health_content_reaction (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(64)     NOT NULL,
    content_id  BIGINT UNSIGNED NOT NULL,
    reaction    VARCHAR(8)      NOT NULL COMMENT 'like/dislike',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_content_reaction (user_id, content_id),
    KEY idx_health_content_reaction_content (content_id, reaction),
    CONSTRAINT fk_health_content_reaction_user FOREIGN KEY (user_id)
        REFERENCES user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_health_content_reaction_content FOREIGN KEY (content_id)
        REFERENCES health_content (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯点赞与点踩';

CREATE TABLE IF NOT EXISTS health_content_comment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(64)     NOT NULL,
    content_id  BIGINT UNSIGNED NOT NULL,
    content     VARCHAR(500)    NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_health_content_comment_list (content_id, created_at, id),
    CONSTRAINT fk_health_content_comment_user FOREIGN KEY (user_id)
        REFERENCES user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_health_content_comment_content FOREIGN KEY (content_id)
        REFERENCES health_content (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯评论';
