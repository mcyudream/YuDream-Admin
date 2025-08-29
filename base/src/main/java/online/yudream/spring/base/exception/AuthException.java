package online.yudream.spring.base.exception;

/**
 * 认证信息错误
 */
public class AuthException extends BaseException{
    public AuthException(String message) {
        super(403, message);
    }

    public AuthException(){
        super(403);
    }
}
