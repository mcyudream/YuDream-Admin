package online.yudream.spring.base.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class R <T>{
    private int code;
    private String message;
    private T data;

    public static <T>R<T> success(T data) {
        return new R<>(0, "success", data);
    }

    public static <T>R<T> success() {
        return success("success", null);
    }

    public static <T>R<T> success(String msg, T data) {
        return new R<>(0, msg, data);
    }

    public static <T>R<T> success(String msg) {
        return success(msg, null);
    }

    public static <T>R<T> fail(String msg) {
        return new R<T>(400, msg, null);
    }

    public static <T>R<T> fail(String msg, int code) {
        return new R<>(code, msg, null);
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EncryptedResponse {
        private String data; // 加密后的数据
    }


}

