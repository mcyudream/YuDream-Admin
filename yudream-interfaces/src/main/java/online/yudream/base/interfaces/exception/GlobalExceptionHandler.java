package online.yudream.base.interfaces.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.common.RequestFailureContext;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.common.ResultCode;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(HttpServletRequest request, BizException e) {
        return failure(request, e, HttpStatus.BAD_REQUEST, Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(HttpServletRequest request,
                                                                                MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return failure(request, e, HttpStatus.BAD_REQUEST, Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(HttpServletRequest request, BindException e) {
        String message = e.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return failure(request, e, HttpStatus.BAD_REQUEST, Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(HttpServletRequest request,
                                                                             ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return failure(request, e, HttpStatus.BAD_REQUEST, Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException(HttpServletRequest request, NotLoginException e) {
        return failure(request, e, HttpStatus.UNAUTHORIZED, Result.fail(ResultCode.UNAUTHORIZED));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermissionException(HttpServletRequest request, NotPermissionException e) {
        return failure(request, e, HttpStatus.FORBIDDEN,
                Result.fail(ResultCode.FORBIDDEN.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceeded(HttpServletRequest request,
                                                                    MaxUploadSizeExceededException e) {
        return uploadSizeExceeded(request, e);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipartException(HttpServletRequest request, MultipartException e) {
        if (isUploadSizeExceeded(e)) {
            return uploadSizeExceeded(request, e);
        }
        return failure(request, e, HttpStatus.BAD_REQUEST, Result.fail(ResultCode.BAD_REQUEST.getCode(), "上传文件解析失败"));
    }

    /**
     * SSE/异步流式响应过程中客户端断开（浏览器刷新、切页）属于正常现象：
     * 响应头已是 text/event-stream，无法再写 JSON 错误体，静默结束即可，避免刷错误日志。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(HttpServletRequest request, AsyncRequestNotUsableException e) {
        log.debug("流式请求客户端断开: method={}, path={}", request.getMethod(), request.getRequestURI());
        return null;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(HttpServletRequest request, NoResourceFoundException e) {
        return failure(request, e, HttpStatus.NOT_FOUND, Result.fail(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(HttpServletRequest request, Exception e) {
        return failure(request, e, HttpStatus.INTERNAL_SERVER_ERROR, Result.fail(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Result<Void>> failure(HttpServletRequest request, Exception e, HttpStatus status,
                                                 Result<Void> result) {
        RequestFailureContext.mark(request, e);
        if (status.is5xxServerError()) {
            log.error("HTTP request failed: method={}, path={}, status={}, type={}",
                    request.getMethod(), request.getRequestURI(), status.value(), e.getClass().getSimpleName());
        }
        else {
            log.warn("HTTP request failed: method={}, path={}, status={}, type={}",
                    request.getMethod(), request.getRequestURI(), status.value(), e.getClass().getSimpleName());
        }
        // SSE 请求（Accept: text/event-stream）协商不出 JSON 错误体：异常发生在进入流式阶段之前，
        // 强写 Result 会抛 HttpMediaTypeNotAcceptableException，连带原始异常被容器以 ERROR 刷屏。
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            @SuppressWarnings("unchecked")
            ResponseEntity<Result<Void>> empty = (ResponseEntity<Result<Void>>) (ResponseEntity<?>)
                    ResponseEntity.status(status).build();
            return empty;
        }
        return ResponseEntity.status(status).body(result);
    }

    private ResponseEntity<Result<Void>> uploadSizeExceeded(HttpServletRequest request, Exception e) {
        return failure(request, e, HttpStatus.BAD_REQUEST,
                Result.fail(ResultCode.BAD_REQUEST.getCode(), "上传文件超过大小限制"));
    }

    static boolean isUploadSizeExceeded(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException) {
                return true;
            }
            String typeName = current.getClass().getName();
            if (typeName.endsWith("FileSizeLimitExceededException")
                    || typeName.endsWith("SizeLimitExceededException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
