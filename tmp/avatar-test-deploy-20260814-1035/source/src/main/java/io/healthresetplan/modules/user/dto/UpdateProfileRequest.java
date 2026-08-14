package io.healthresetplan.modules.user.dto;

public class UpdateProfileRequest {

    private String nickname;
    private String avatarUrl;
    private String customId;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getCustomId() { return customId; }
    public void setCustomId(String customId) { this.customId = customId; }
}
