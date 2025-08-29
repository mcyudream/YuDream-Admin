package online.yudream.spring.base.exception;


import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

/**
 * 规定的一个全局异常，方便与统一返回体R对接
 */
@Getter
@NoArgsConstructor
public class BaseException extends RuntimeException {
    private int code=400; // 规定默认未注册错误为400
    private String msg;

    public BaseException(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public BaseException(String msg) {
        this.msg = msg;
    }

    public BaseException(int code) {
        this.code = code;
        this.msg = "未知错误: %s".formatted(ArrayUtils.toString(getStackTrace()));
    }
}