package com.zyt.medconsensus.controller;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return Map.of(
                "status", status.value(),
                "message", status.is5xxServerError() ? "服务暂时不可用，请稍后重试"
                        : (exception.getReason() == null ? status.getReasonPhrase() : exception.getReason())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("请求参数不合法");

        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "message", message
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraintViolationException(ConstraintViolationException exception) {
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "message", "请求参数不合法"
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        return Map.of(
                "status", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "message", "上传文件过大，请上传 50MB 以内的 PDF、DOCX、JPG 或 PNG 文件"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException exception) {
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "message", "请求参数不合法"
        );
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleConcurrencyConflict(RuntimeException exception) {
        return Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "message", "数据已被其他请求修改，请刷新后重试"
        );
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException exception) {
        log.error("Unhandled API exception", exception);
        return Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "message", "服务暂时不可用，请稍后重试"
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleAuthenticationException(AuthenticationException exception) {
        return Map.of("status", HttpStatus.UNAUTHORIZED.value(), "message", "请先登录");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDeniedException(AccessDeniedException exception) {
        return Map.of("status", HttpStatus.FORBIDDEN.value(), "message", "无权访问该资源");
    }
}
