package online.yudream.spring.base.exception;

public class NotFoundException extends BaseException{
    public NotFoundException() {
        super(404, "不存在的资源!");
    }
}
