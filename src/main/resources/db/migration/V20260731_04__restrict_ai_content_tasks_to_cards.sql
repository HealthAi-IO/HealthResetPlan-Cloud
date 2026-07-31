UPDATE ai_content_task
SET enabled = 0,
    next_run_at = NULL,
    last_result = 'disabled',
    last_error = '图文资讯已调整为仅支持人工创建和发布',
    version = version + 1
WHERE content_type <> 'card';

UPDATE ai_content_task
SET publish_mode = 'auto',
    version = version + 1
WHERE content_type = 'card'
  AND publish_mode <> 'auto';
