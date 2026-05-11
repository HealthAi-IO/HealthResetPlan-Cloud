package io.healthresetplan.common.exception;

/**
 * 业务异常。{@link #getCode()} 与 {@link io.healthresetplan.common.result.R#getCode()} 保持一致。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
