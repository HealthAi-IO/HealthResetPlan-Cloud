SET NAMES utf8mb4;

ALTER TABLE feedback
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'normal' AFTER status,
    ADD COLUMN assignee VARCHAR(64) NOT NULL DEFAULT '' AFTER priority,
    ADD COLUMN resolution TEXT NULL AFTER assignee,
    ADD KEY idx_feedback_priority (priority),
    ADD KEY idx_feedback_created_at (created_at);

