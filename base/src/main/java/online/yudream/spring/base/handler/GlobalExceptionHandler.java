package online.yudream.spring.base.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import online.yudream.spring.base.common.R;
import online.yudream.spring.base.exception.BaseException;
import online.yudream.spring.base.utils.AESUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常捕获类
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.application.print-trace-log}")
    private boolean printTraceLog;

    @Value("${base.security.enable-res-encode:false}") // 默认关闭
    private boolean enableResEncode;
    @Resource
    private AESUtil aesUtil;
    @Resource
    private  ObjectMapper objectMapper;


    @ExceptionHandler(BaseException.class)
    public Object baseException(BaseException e, HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        log.error("出现自定义异常: {}",e.getMessage());
        if (printTraceLog){
            e.printStackTrace();
        }
        R<String> res = R.fail(e.getMessage(),e.getCode());
        response.setStatus(e.getCode());
        if (enableResEncode) {
            String originalJson = new ObjectMapper().writeValueAsString(res);
            String encryptedData = aesUtil.encrypt(originalJson);
            return new R.EncryptedResponse(encryptedData);
        }
        return res;
    }


    /* 未登录 */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> handleNotLogin(NotLoginException e) {
        log.warn("NotLogin: type={}, msg={}", e.getType(), e.getMessage());
        if (printTraceLog) log.debug("stack", e);
        R<?> body = R.fail("暂未登录", e.getCode()); // 业务码仍可放这里
        return build(body, HttpStatus.UNAUTHORIZED);
    }

    /* 无权限 / 无角色 */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<?> handleAuthz(SaTokenException e) {
        log.warn("Authz denied: {}", e.getMessage());
        if (printTraceLog) log.debug("stack", e);
        R<?> body = R.fail(e.getMessage(), e.getCode());
        return build(body, HttpStatus.FORBIDDEN);
    }

    /* 其他 Sa-Token 异常 */
    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<?> handleSaToken(SaTokenException e) {
        log.warn("SaToken exception: {}", e.getMessage());
        if (printTraceLog) log.debug("stack", e);
        R<?> body = R.fail(e.getMessage(), e.getCode());
        return build(body, HttpStatus.BAD_REQUEST);
    }

    /** 构建统一响应（按开关可加密），避免在 Advice 再次加密 */
    private ResponseEntity<?> build(Object payload, HttpStatus status) {
        if (!enableResEncode) {
            return ResponseEntity.status(status).body(payload);
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            String encrypted = aesUtil.encrypt(json);
            // 返回一个“已加密”标记的载体，供 ResponseBodyAdvice 识别跳过
            return ResponseEntity.status(status).body(new R.EncryptedResponse(encrypted));
        } catch (Exception ex) {
            log.error("响应加密失败，降级为明文", ex);
            return ResponseEntity.status(status).body(payload);
        }
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<String> runtimeException(RuntimeException e) {
        log.error("出现运行异常: {}", e.getMessage());
        if (printTraceLog) {
            e.printStackTrace();
        }
        return R.fail(e.getMessage(), 400);
    }

}
