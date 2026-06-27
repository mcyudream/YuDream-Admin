package online.yudream.base.interfaces.exception;

/**
 * 邮件发送异常
 */
public class MailSendException extends RuntimeException {
    public MailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
