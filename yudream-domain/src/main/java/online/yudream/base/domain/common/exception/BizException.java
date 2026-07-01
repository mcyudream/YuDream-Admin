package online.yudream.base.domain.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 1000;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
