package com.coldstore.freezer.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handle(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("ok", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handle(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("ok", false, "message", ex.getMessage()));
    }

    /**
     * Bean Validation 注解校验（如未来在 DTO 上加 @NotNull）失败时同样明确 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("请求参数校验未通过");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("ok", false, "message", msg));
    }

    /**
     * 绕过页面直连时常见的坏请求：空请求体、JSON 语法坏、字段类型对不上
     * （例如 capacity 传成 "" / "abc" / true）。一律明确 400 驳回，
     * 绝不能让请求落到实体里把容量静默写成 null。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handle(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("ok", false, "message",
                        "请求体无法解析（可能是空请求体、JSON 格式错误，或容量/箱数等数字字段填了非数字内容），请检查后重试；库间容量与库存、预占状态均未改动"));
    }
}
