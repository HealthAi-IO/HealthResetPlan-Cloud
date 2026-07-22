UPDATE ai_provider_config
SET api_key_cipher = '',
    api_key_iv = '',
    api_key_tag = '',
    status = 0
WHERE api_key_cipher <> '';
