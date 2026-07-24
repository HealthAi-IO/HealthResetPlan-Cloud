UPDATE user_session
SET refresh_token = LOWER(SHA2(refresh_token, 256))
WHERE refresh_token <> ''
  AND CHAR_LENGTH(refresh_token) <> 64;
