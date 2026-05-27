package io.healthresetplan.modules.auth.dto;

public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    /** accessToken 有效期（秒） */
    private long accessExpiresIn;
    private String userId;

    public TokenResponse() {}

    public TokenResponse(String accessToken, String refreshToken, long accessExpiresIn, String userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresIn = accessExpiresIn;
        this.userId = userId;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getAccessExpiresIn() { return accessExpiresIn; }
    public void setAccessExpiresIn(long accessExpiresIn) { this.accessExpiresIn = accessExpiresIn; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
