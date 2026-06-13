package io.healthresetplan.modules.auth.dto;

public class PasswordResetCodeResponse {
    private String debugCode;
    private long expiresIn;

    public PasswordResetCodeResponse(String debugCode, long expiresIn) {
        this.debugCode = debugCode;
        this.expiresIn = expiresIn;
    }

    public String getDebugCode() { return debugCode; }
    public void setDebugCode(String debugCode) { this.debugCode = debugCode; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
}
