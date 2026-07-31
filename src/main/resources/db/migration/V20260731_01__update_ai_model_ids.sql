UPDATE ai_provider_config
SET model = 'doubao-seed-2-1-pro-260628',
    updated_at = NOW(3)
WHERE provider = 'doubao'
  AND model = 'ep-20260721151735-7px4f'
  AND deleted_at IS NULL;

UPDATE ai_provider_config
SET model = 'glm-5-2-260617',
    updated_at = NOW(3)
WHERE provider = 'glm'
  AND model = 'ep-20260721151959-wgsk4'
  AND deleted_at IS NULL;

UPDATE ai_provider_config
SET model = 'deepseek-v4-pro-260425',
    updated_at = NOW(3)
WHERE provider = 'deepseek'
  AND model = 'ep-20260721152104-szzhr'
  AND deleted_at IS NULL;
