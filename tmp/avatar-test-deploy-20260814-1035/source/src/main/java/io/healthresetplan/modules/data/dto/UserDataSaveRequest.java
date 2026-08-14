package io.healthresetplan.modules.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class UserDataSaveRequest {
    @Min(0)
    private long version;
    @NotNull
    private Map<String, Object> data;

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
