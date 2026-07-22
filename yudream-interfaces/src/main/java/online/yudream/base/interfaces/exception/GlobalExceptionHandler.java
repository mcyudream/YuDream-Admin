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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.status(status).body(result);
    }
}
